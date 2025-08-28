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
import com.github.winplay02.gitcraft.util.SerializationHelper;

import java.nio.file.Files;
import java.nio.file.Path;

public record LaunchStepHardlinkAssets(StepWorker.Config config) implements StepWorker<OrderedVersion, StepInput.Empty> {
	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StepInput.Empty input, StepResults<OrderedVersion> results) throws Exception {

		Path assetsObjectsDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.ASSETS_OBJECTS));
		Path assetsIndexDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.ASSETS_INDEX));

		Path assetsPathObjects = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_ASSETS_OBJECTS));
		Path assetsPathIndexes = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_ASSETS_INDEXES));

		// Create Link to assets index
		Path targetAssetsIndexNoExt = context.targetVersion().assetsIndex().resolve(assetsPathIndexes);
		Path targetAssetsIndex = targetAssetsIndexNoExt.getParent().resolve(targetAssetsIndexNoExt.getFileName() + ".json");
		if (!Files.exists(targetAssetsIndex)) {
			Files.createLink(targetAssetsIndex, context.targetVersion().assetsIndex().resolve(assetsIndexDir));
		}
		// Create Link to assets
		AssetsIndex assetsIndex = AssetsIndex.from(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(context.targetVersion().assetsIndex().resolve(assetsIndexDir)), AssetsIndexMetadata.class));
		for (Artifact assetObject : assetsIndex.assets()) {
			Path outerDirectory = assetsPathObjects.resolve(assetObject.name().substring(0, 2));
			Files.createDirectories(outerDirectory);
			if (!Files.exists(assetObject.resolve(outerDirectory))) {
				Files.createLink(assetObject.resolve(outerDirectory), assetObject.resolve(assetsObjectsDir));
			}
		}
		return StepOutput.ofEmptyResultSet(StepStatus.SUCCESS);
	}
}
