package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

public class Pipeline {

	private final List<Step> steps; // TODO: customize?
	private final Map<StepResult, Path> resultFiles;
	private final Map<MinecraftJar, Path> minecraftJars;

	public Pipeline() {
		this.steps = Arrays.asList(Step.values());
		this.resultFiles = new HashMap<>();
		this.minecraftJars = new EnumMap<>(MinecraftJar.class);

		checkStepDependencies();
	}

	private void checkStepDependencies() {
		Set<Step> prev = EnumSet.noneOf(Step.class);
		Set<Step> next = EnumSet.copyOf(steps);

		for (Step step : steps) {
			try {
				checkStepDependencies(step, prev, next);
			} catch (Exception e) {
				MiscHelper.panicBecause(e, "Illegal pipeline!");
			}

			prev.add(step);
			next.remove(step);
		}
	}

	private void checkStepDependencies(Step step, Set<Step> prev, Set<Step> next) {
		for (Step required : step.getDependencies(DependencyType.REQUIRED)) {
			if (!prev.contains(required)) {
				throw new IllegalStateException("Step \'" + step.getName() + "\' depends on step \'" + required.getName() + "\' but that step does not appear before it in the pipeline!");
			}
		}
		for (Step notRequired : step.getDependencies(DependencyType.NOT_REQUIRED)) {
			if (next.contains(notRequired)) {
				throw new IllegalStateException("Step \'" + step.getName() + "\' depends on step \'" + notRequired.getName() + "\' but it appears before that step in the pipeline!");
			}
		}
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
		StepWorker.Config config = new StepWorker.Config(
			GitCraft.config.getExceptionsForMinecraftVersion(minecraftVersion).orElse(ExceptionsFlavour.NONE),
			GitCraft.config.getSignaturesForMinecraftVersion(minecraftVersion).orElse(SignaturesFlavour.NONE),
			GitCraft.config.getMappingsForMinecraftVersion(minecraftVersion).orElse(MappingFlavour.IDENTITY_UNMAPPED),
			GitCraft.config.getNestsForMinecraftVersion(minecraftVersion).orElse(NestsFlavour.NONE)
		);

		Set<Step> completed = EnumSet.noneOf(Step.class);

		for (Step step : steps) {
			MiscHelper.println("Performing step '%s' for %s (%s)...", step.getName(), context, config);

			StepStatus status = null;
			Exception exception = null;

			long timeStart = System.nanoTime();

			try {
				checkStepDependencies(step, completed, Collections.emptySet());
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
			default -> { }
			}

			if (status == StepStatus.FAILED) {
				String message = String.format("Step '%s' for %s (%s) \u001B[31mfailed\u001B[0m (%s)", step.getName(), context, config, timeInfo);

				if (exception == null) {
					MiscHelper.panic(message);
				} else {
					MiscHelper.panicBecause(exception, message);
				}
			}

			completed.add(step);

			if (status.hasRun()) {
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

	public static void run(RepoWrapper repository, MinecraftVersionGraph versionGraph) throws Exception {
		for (OrderedVersion mcVersion : versionGraph) {
			if (repository == null || !repository.findVersionRev(mcVersion)) {
				new Pipeline().run(repository, versionGraph, mcVersion);
			}
		}
	}
}
