package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public record LibrariesFetcher(StepWorker.Config config) implements StepWorker<StepInput.Empty> {

	@Override
	public StepOutput run(Pipeline pipeline, Context context, StepInput.Empty input, StepResults results) throws Exception {
		Path librariesDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.LIBRARIES));

		int maxRunningTasks = 16;
		List<StepStatus> statuses = MiscHelper.runTasksInParallelAndAwaitResult(
			maxRunningTasks,
			context.executorService(),
			context.minecraftVersion().libraries().stream().<Callable<StepStatus>>map(library -> () -> library.fetchArtifact(librariesDir, "library")).toList()
		);

		return new StepOutput(StepStatus.merge(statuses), results);
	}
}
