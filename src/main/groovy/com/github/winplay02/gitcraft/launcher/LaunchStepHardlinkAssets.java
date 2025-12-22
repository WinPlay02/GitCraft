package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.manifest.metadata.AssetsIndexMetadata;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepConfig;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepWorker;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record LaunchStepHardlinkAssets(GitCraftStepConfig config) implements GitCraftStepWorker<StepInput.Empty> {

	private static Artifact createArtifactForHash(String hash) {
		return new Artifact(AssetsIndex.makeMinecraftAssetUrl(hash), hash, hash);
	}

	private static final Map<String, Artifact> icons = Map.of(
		"icons/icon_16x16.png", createArtifactForHash("bdf48ef6b5d0d23bbb02e17d04865216179f510a"),
		"icons/icon_32x32.png", createArtifactForHash("92750c5f93c312ba9ab413d546f32190c56d6f1f"),
		"icons/minecraft.icns", createArtifactForHash("991b421dfd401f115241601b2b373140a8d78572")
	);

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		StepInput.Empty input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {

		Path assetsObjectsDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.ASSETS_OBJECTS));
		Path assetsIndexDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.ASSETS_INDEX));

		Path assetsPathObjects = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_ASSETS_OBJECTS));
		Path assetsPathIndexes = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_ASSETS_INDEXES));

		AssetsIndex assetsIndex = AssetsIndex.from(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(context.targetVersion().assetsIndex().resolve(assetsIndexDir)), AssetsIndexMetadata.class));

		Path symlinkedAssetsDir = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_GAME).resolve("assets");

		Files.deleteIfExists(symlinkedAssetsDir);
		Files.createDirectories(symlinkedAssetsDir.getParent());
		Files.createSymbolicLink(
			symlinkedAssetsDir,
			results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_ASSETS)
		);
		if (assetsIndex.assetsIndex().map_to_resources()) {
			// Legacy VFS
			Path assetsPathVfs = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_ASSETS_VIRTUALFS);
			MiscHelper.deleteDirectory(assetsPathVfs);
			Files.createDirectories(assetsPathVfs);
			for (Map.Entry<String, AssetsIndexMetadata.Asset> object : assetsIndex.assetsIndex().objects().entrySet()) {
				Path targetPath = assetsPathVfs.resolve(object.getKey());
				Files.createDirectories(targetPath.getParent());
				Artifact assetArtifact = new Artifact(object.getValue().url(), object.getValue().hash(), object.getValue().hash());
				Files.createLink(targetPath, assetArtifact.resolve(assetsObjectsDir));
			}
			// Check for icons
			for (Map.Entry<String, Artifact> icon : icons.entrySet()) {
				Path assetFileIcon = assetsPathVfs.resolve(icon.getKey());
				if (!Files.exists(assetFileIcon)) {
					Files.createDirectories(assetFileIcon.getParent());
					icon.getValue().fetchArtifact(context.executorService(), assetsObjectsDir, "compat asset");
					Files.createLink(assetFileIcon, icon.getValue().resolve(assetsObjectsDir));
				}
			}
			// Symlink to resources
			Path symlinkedResourcesDir = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_GAME).resolve("resources");
			if (Files.isDirectory(symlinkedResourcesDir)) {
				MiscHelper.deleteDirectory(symlinkedResourcesDir);
			} else {
				Files.deleteIfExists(symlinkedResourcesDir);
			}
			Files.createSymbolicLink(
				symlinkedResourcesDir,
				assetsPathVfs
			);
		} else {
			// Create Link to assets index
			Path targetAssetsIndexNoExt = context.targetVersion().assetsIndex().resolve(assetsPathIndexes);
			Path targetAssetsIndex = targetAssetsIndexNoExt.getParent().resolve(targetAssetsIndexNoExt.getFileName() + ".json");
			if (!Files.exists(targetAssetsIndex)) {
				Files.createLink(targetAssetsIndex, context.targetVersion().assetsIndex().resolve(assetsIndexDir));
			}
			// Create Link to assets
			for (Artifact assetObject : assetsIndex.assets()) {
				Path outerDirectory = assetsPathObjects.resolve(assetObject.name().substring(0, 2));
				Files.createDirectories(outerDirectory);
				if (!Files.exists(assetObject.resolve(outerDirectory))) {
					Files.createLink(assetObject.resolve(outerDirectory), assetObject.resolve(assetsObjectsDir));
				}
			}
		}
		return StepOutput.ofEmptyResultSet(StepStatus.SUCCESS);
	}
}
