package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.manifest.metadata.AssetsIndexMetadata;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record LaunchStepHardlinkAssets(StepWorker.Config config) implements StepWorker<OrderedVersion, StepInput.Empty> {
	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StepInput.Empty input, StepResults<OrderedVersion> results) throws Exception {

		Path assetsObjectsDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.ASSETS_OBJECTS));
		Path assetsIndexDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.ASSETS_INDEX));

		Path assetsPathObjects = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_ASSETS_OBJECTS));
		Path assetsPathIndexes = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_ASSETS_INDEXES));

		AssetsIndex assetsIndex = AssetsIndex.from(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(context.targetVersion().assetsIndex().resolve(assetsIndexDir)), AssetsIndexMetadata.class));

		Path symlinkedAssetsDir = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_GAME).resolve("assets");

		Files.deleteIfExists(symlinkedAssetsDir);
		Files.createSymbolicLink(
			symlinkedAssetsDir,
			results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_ASSETS)
		);

		if (context.targetVersion().versionInfo().assets().equalsIgnoreCase("pre-1.6") || context.targetVersion().versionInfo().assets().equalsIgnoreCase("legacy")) {
			// Legacy VFS
			Path assetsPathVfs = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_ASSETS_VIRTUALFS);
			MiscHelper.deleteDirectory(assetsPathVfs);
			Files.createDirectories(assetsPathVfs);
			for (Map.Entry<String, AssetsIndexMetadata.Asset> object : assetsIndex.assetsIndex().objects().entrySet()) {
				Path targetPath = assetsPathVfs.resolve(object.getKey());
				Files.createDirectories(targetPath.getParent());
				Artifact assetArtifact = new Artifact(AssetsIndex.makeMinecraftAssetUrl(object.getValue().hash()), object.getValue().hash(), object.getValue().hash());
				Path outerDirectory = assetsPathObjects.resolve(assetArtifact.name().substring(0, 2));
				Files.createLink(targetPath, assetArtifact.resolve(outerDirectory));
			}
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
