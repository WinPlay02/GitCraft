package com.github.winplay02.gitcraft.pipeline.workers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.github.winplay02.gitcraft.GitCraft;
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
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

public record AssetsFetcher(GitCraftStepConfig config) implements GitCraftStepWorker<StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		StepInput.Empty input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		if (!GitCraft.getDataConfiguration().loadAssetsExtern() || !GitCraft.getDataConfiguration().loadAssets()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		if (context.targetVersion().assetsIndex() == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.ASSETS_INDEX));
		Path assetsObjectsDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.ASSETS_OBJECTS));
		Path assetsIndexPath = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.ASSETS_INDEX_JSON);
		List<StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig>> statuses = new ArrayList<>();
		statuses.add(ArtifactsFetcher.fetchArtifact(pipeline, context, this.config, context.targetVersion().assetsIndex(), GitCraftPipelineFilesystemStorage.ASSETS_INDEX_JSON, "assets index"));
		AssetsIndex assetsIndex = AssetsIndex.from(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(assetsIndexPath), AssetsIndexMetadata.class));

		int maxRunningTasks = 32;
		statuses.addAll(
			MiscHelper.runTasksInParallelAndAwaitResult(
				maxRunningTasks,
				context.executorService(),
				assetsIndex.assets().stream().<Callable<StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig>>>map(assetObject -> () -> StepOutput.ofEmptyResultSet(assetObject.fetchArtifact(context.executorService(), assetsObjectsDir, "asset"))).toList()
			)
		);
		return StepOutput.merge(statuses);
	}
}
