package com.github.winplay02.gitcraft.pipeline.workers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.meta.AssetsIndexMetadata;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

public record AssetsFetcher(StepWorker.Config config) implements StepWorker<StepInput.Empty> {

	@Override
	public StepOutput run(Pipeline pipeline, Context context, StepInput.Empty input, StepResults results) throws Exception {
		if (!GitCraft.config.loadAssetsExtern || !GitCraft.config.loadAssets) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		if (context.minecraftVersion().assetsIndex() == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.ASSETS_INDEX));
		Path assetsObjectsDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.ASSETS_OBJECTS));
		Path assetsIndexPath = results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.ASSETS_INDEX_JSON);
		List<StepOutput> statuses = new ArrayList<>();
		statuses.add(ArtifactsFetcher.fetchArtifact(pipeline, context, context.minecraftVersion().assetsIndex(), PipelineFilesystemStorage.ASSETS_INDEX_JSON, "assets index"));
		AssetsIndex assetsIndex = AssetsIndex.from(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(assetsIndexPath), AssetsIndexMetadata.class));

		int maxRunningTasks = 16;
		statuses.addAll(
			MiscHelper.runTasksInParallelAndAwaitResult(
				maxRunningTasks,
				context.executorService(),
				assetsIndex.assets().stream().<Callable<StepOutput>>map(assetObject -> () -> StepOutput.ofEmptyResultSet(assetObject.fetchArtifact(assetsObjectsDir, "asset"))).toList()
			)
		);
		return StepOutput.merge(statuses);
	}
}
