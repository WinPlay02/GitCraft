package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.github.winplay02.gitcraft.types.Artifact;

public record LibrariesFetcher(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		Path librariesDir = pipeline.initResultFile(step, context, Results.LIBRARIES_DIRECTORY);
		Files.createDirectories(librariesDir);
		List<StepStatus> statuses = new ArrayList<>(context.minecraftVersion().libraries().size());
		for (Artifact library : context.minecraftVersion().libraries()) {
			statuses.add(library.fetchArtifact(librariesDir, "library"));
		}
		return StepStatus.merge(statuses);
	}

	public enum Results implements StepResult {
		LIBRARIES_DIRECTORY
	}
}
