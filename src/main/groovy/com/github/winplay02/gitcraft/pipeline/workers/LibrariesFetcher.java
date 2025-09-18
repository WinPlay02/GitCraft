package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepConfig;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepWorker;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

public record LibrariesFetcher(GitCraftStepConfig config) implements GitCraftStepWorker<StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		StepInput.Empty input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		Path librariesDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LIBRARIES));

		int maxRunningTasks = 32;
		List<StepStatus> statuses = MiscHelper.runTasksInParallelAndAwaitResult(
			maxRunningTasks,
			context.executorService(),
			context.targetVersion().libraries().stream().<Callable<StepStatus>>map(library -> () -> library.fetchArtifact(context.executorService(), librariesDir, "library")).toList()
		);

		return new StepOutput<>(StepStatus.merge(statuses), results);
	}
}
