package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.meta.AssetsIndexMetadata;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.util.SerializationHelper;

public record AssetsFetcher(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		if (!GitCraft.config.loadAssetsExtern || !GitCraft.config.loadAssets) {
			return StepStatus.NOT_RUN;
		}
		if (context.minecraftVersion().assetsIndex() == null) {
			return StepStatus.NOT_RUN;
		}
		pipeline.initResultFile(step, context, ResultFiles.ASSETS_INDEX_DIRECTORY);
		Path assetsObjectsDir = pipeline.initResultFile(step, context, ResultFiles.ASSETS_OBJECTS_DIRECTORY);
		Path assetsIndexPath = pipeline.initResultFile(step, context, ResultFiles.ASSETS_INDEX);
		List<StepStatus> statuses = new ArrayList<>();
		statuses.add(ArtifactsFetcher.fetchArtifact(step, pipeline, context, context.minecraftVersion().assetsIndex(), ResultFiles.ASSETS_INDEX));
		AssetsIndex assetsIndex = AssetsIndex.from(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(assetsIndexPath), AssetsIndexMetadata.class));
		for (Artifact assetObject : assetsIndex.assets()) {
			statuses.add(assetObject.fetchArtifact(assetsObjectsDir, "asset"));
		}
		return StepStatus.merge(statuses);
	}

	public enum ResultFiles implements StepResult {
		ASSETS_INDEX_DIRECTORY, ASSETS_OBJECTS_DIRECTORY, ASSETS_INDEX
	}
}
