package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import groovy.lang.Tuple;
import groovy.lang.Tuple2;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Pipeline {

	private final PipelineDescription pipelineDescription;
	private final PipelineFilesystemStorage pipelineFilesystemStorage;
	private final Map<Tuple2<StorageKey, OrderedVersion>, OrderedVersion> overriddenPaths = new HashMap<>();
	private final Map<OrderedVersion, StepResults> versionedResults = new HashMap<>();

	public Pipeline(PipelineDescription pipelineDescription, PipelineFilesystemStorage pipelineFilesystemStorage) {
		this.pipelineDescription = pipelineDescription;
		this.pipelineFilesystemStorage = pipelineFilesystemStorage;
		this.pipelineDescription.validate();
	}

	public PipelineDescription getDescription() {
		return this.pipelineDescription;
	}

	public PipelineFilesystemStorage getFilesystemStorage() {
		return this.pipelineFilesystemStorage;
	}

	public Path getStoragePath(StorageKey key, StepWorker.Context context) {
		Tuple2<StorageKey, OrderedVersion> versionOverride = Tuple.tuple(key, context.minecraftVersion());
		if (this.overriddenPaths.containsKey(versionOverride)) {
			return this.getStoragePath(key, context.withDifferingVersion(this.overriddenPaths.get(versionOverride)));
		}
		return this.getFilesystemStorage().getPath(key, context);
	}

	protected void relinkStoragePathToDifferentVersion(StorageKey key, StepWorker.Context context, OrderedVersion version) {
		this.overriddenPaths.put(Tuple.tuple(key, context.minecraftVersion()), version);
	}

	public void run(RepoWrapper repository, MinecraftVersionGraph versionGraph, OrderedVersion minecraftVersion) {
		StepWorker.Context context = new StepWorker.Context(repository, versionGraph, minecraftVersion);
		StepWorker.Config config = new StepWorker.Config(GitCraft.config.getMappingsForMinecraftVersion(minecraftVersion).orElse(MappingFlavour.IDENTITY_UNMAPPED));
		StepResults results = this.versionedResults.computeIfAbsent(minecraftVersion, (version) -> StepResults.ofEmpty());

		for (Step step : this.getDescription().steps()) {
			MiscHelper.println("Performing step '%s' for %s (%s)...", step.getName(), context, config);

			StepOutput status = null;
			Exception exception = null;

			long timeStart = System.nanoTime();

			try {
				StepWorker<?> worker = step.createWorker(config);
				status = worker.runGeneric(
					this,
					context,
					this.getDescription().stepInputMap().get(step).apply(this.getFilesystemStorage(), results),
					results
				);
				if (status.results() != results) {
					results.addAll(status.results());
				}
			} catch (Exception e) {
				status = StepOutput.ofEmptyResultSet(StepStatus.FAILED);
				exception = e;
			}

			long timeEnd = System.nanoTime();
			long delta = timeEnd - timeStart;
			Duration deltaDuration = Duration.ofNanos(delta);
			String timeInfo = String.format("elapsed: %dm %02ds", deltaDuration.toMinutes(), deltaDuration.toSecondsPart());

			switch (status.status()) {
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

			if (status.status() == StepStatus.FAILED) {
				String message = String.format("Step '%s' for %s (%s) \u001B[31mfailed\u001B[0m (%s)", step.getName(), context, config, timeInfo);

				if (exception == null) {
					MiscHelper.panic(message);
				} else {
					MiscHelper.panicBecause(exception, message);
				}
			}
		}
	}

	public static void run(PipelineDescription description, PipelineFilesystemStorage storage, RepoWrapper repository, MinecraftVersionGraph versionGraph) throws Exception {
		for (OrderedVersion mcVersion : versionGraph) {
			if (repository == null || !repository.existsRevWithCommitMessage(mcVersion.toCommitMessage())) {
				new Pipeline(description, storage).run(repository, versionGraph, mcVersion);
			}
		}
	}
}
