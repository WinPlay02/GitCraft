package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

public record LibrariesFetcher(StepWorker.Config config) implements StepWorker<OrderedVersion, StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StepInput.Empty input, StepResults<OrderedVersion> results) throws Exception {
		Path librariesDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LIBRARIES));

		int maxRunningTasks = 32;
		List<StepStatus> statuses = MiscHelper.runTasksInParallelAndAwaitResult(
			maxRunningTasks,
			context.executorService(),
			context.targetVersion().libraries().stream().<Callable<StepStatus>>map(library -> () -> library.fetchArtifact(context.executorService(), librariesDir, "library")).toList()
		);

		return new StepOutput<>(StepStatus.merge(statuses), results);
	}
}
