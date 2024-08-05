package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

public class Pipeline {

	private final Map<StepResult, Path> resultFiles;
	private final Map<MinecraftJar, Path> minecraftJars;

	public Pipeline() {
		this.resultFiles = new HashMap<>();
		this.minecraftJars = new EnumMap<>(MinecraftJar.class);
	}

	Path initResultFile(Step step, StepWorker.Context context, StepResult resultFile) {
		Path path = step.getResultFile(resultFile, context);
		resultFiles.put(resultFile, path);
		return path;
	}

	public Path getResultFile(StepResult resultFile) {
		return resultFiles.get(resultFile);
	}

	public Path getMinecraftJar(MinecraftJar minecraftJar) {
		return minecraftJars.get(minecraftJar);
	}

	public void run(RepoWrapper repository, MinecraftVersionGraph versionGraph, OrderedVersion minecraftVersion) {
		StepWorker.Context context = new StepWorker.Context(repository, versionGraph, minecraftVersion);
		StepWorker.Config config = new StepWorker.Config(GitCraft.config.getMappingsForMinecraftVersion(minecraftVersion).orElse(MappingFlavour.IDENTITY_UNMAPPED));

		for (Step step : Step.values()) {
			StepStatus status = null;
			Exception exception = null;

			long timeStart = System.nanoTime();

			try {
				MiscHelper.println("Performing step '%s' for %s (%s)...", step.getName(), context, config);
				status = step.createWorker(config).run(this, context);
			} catch (Exception e) {
				status = StepStatus.FAILED;
				exception = e;
			}

			long timeEnd = System.nanoTime();
			long delta = timeEnd - timeStart;
			Duration deltaDuration = Duration.ofNanos(delta);
			String timeInfo = String.format("elapsed: %dm %02ds", deltaDuration.toMinutes(), deltaDuration.toSecondsPart());

			switch (status) {
			case SUCCESS ->
				MiscHelper.println("\tStep '%s' for %s (%s) \u001B[32msucceeded\u001B[0m (%s)", step.getName(), context, config, timeInfo);
			case UP_TO_DATE ->
				MiscHelper.println("\tStep '%s' for %s (%s) was \u001B[32malready up-to-date\u001B[0m", step.getName(), context, config);
			case NOT_RUN -> {
				if (GitCraft.config.printNotRunSteps) {
					MiscHelper.println("Step '%s' for %s (%s) was \u001B[36mnot run\u001B[0m", step.getName(), context, config);
				}
			}
			}

			if (status == StepStatus.FAILED) {
				if (exception != null) {
					MiscHelper.panicBecause(exception, "Step '%s' for %s (%s) \u001B[31mfailed\u001B[0m (%s)", step.getName(), context, config, timeInfo);
				}
			} else if (status.hasRun()) {
				for (MinecraftJar minecraftJar : MinecraftJar.values()) {
					StepResult resultFile = step.getMinecraftJar(minecraftJar);

					// this step may not do anything with mc jars at all
					if (resultFile != null) {
						Path path = resultFiles.get(resultFile);

						// this step may not have affected this mc jar
						if (path != null) {
							minecraftJars.put(minecraftJar, path);
						}
					}
				}
			}
		}
	}
}
