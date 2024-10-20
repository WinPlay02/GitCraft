package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.types.Artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record LibrariesFetcher(StepWorker.Config config) implements StepWorker<StepInput.Empty> {

	@Override
	public StepOutput run(Pipeline pipeline, Context context, StepInput.Empty input, StepResults results) throws Exception {
		Path librariesDir = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.LIBRARIES));
		List<StepStatus> statuses = new ArrayList<>(context.minecraftVersion().libraries().size());
		for (Artifact library : context.minecraftVersion().libraries()) {
			statuses.add(library.fetchArtifact(librariesDir, "library"));
		}
		return new StepOutput(StepStatus.merge(statuses), results);
	}
}
