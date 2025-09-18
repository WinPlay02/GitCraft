package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import com.github.winplay02.gitcraft.util.Tuple2;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public final class IPipeline<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> {

	private final PipelineDescription<T, C, D> pipelineDescription;
	private final PipelineFilesystemStorage<T, C, D> pipelineFilesystemStorage;
	private final Map<Tuple2<StorageKey, T>, Tuple2<T, D>> overriddenPaths = new ConcurrentHashMap<>();
	private final Map<T, StepResults<T, C, D>> versionedResults = new ConcurrentHashMap<>();
	private Semaphore threadLimiter = null;

	public IPipeline(PipelineDescription<T, C, D> pipelineDescription, PipelineFilesystemStorage<T, C, D> pipelineFilesystemStorage) {
		this.pipelineDescription = pipelineDescription;
		this.pipelineFilesystemStorage = pipelineFilesystemStorage;
		this.pipelineDescription.validate();
	}

	public final PipelineDescription<T, C, D> getDescription() {
		return this.pipelineDescription;
	}

	public final PipelineFilesystemStorage<T, C, D> getFilesystemStorage() {
		return this.pipelineFilesystemStorage;
	}

	public final Path getStoragePath(StorageKey key, C context, D config) {
		Tuple2<StorageKey, T> versionOverride = Tuple2.tuple(key, context.targetVersion());
		if (this.overriddenPaths.containsKey(versionOverride)) {
			Tuple2<T, D> override = this.overriddenPaths.get(versionOverride);
			return this.getStoragePath(key, context.withDifferingVersion(override.getV1()), override.getV2());
		}
		return this.getFilesystemStorage().getPath(key, context, config);
	}

	public final void relinkStoragePathToDifferentVersion(StorageKey key, C context, D config, T version) {
		this.overriddenPaths.put(Tuple2.tuple(key, context.targetVersion()), Tuple2.tuple(version, config));
	}

	public Semaphore threadLimiter() {
		return threadLimiter;
	}

	protected record TupleVersionStep<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig>(IStep<T, ?, C, D> step, T version) {
	}

	protected void runSingleVersionSingleStep(TupleVersionStep<T, C, D> versionStep, C context, D config) {
		StepResults<T, C, D> results = this.versionedResults.computeIfAbsent(versionStep.version(), version -> StepResults.ofEmpty());

		MiscHelper.println("Performing step '%s' for %s (%s)...", versionStep.step().getName(), context, config);

		StepOutput<T, C, D> status = null;
		Exception exception = null;

		long timeStart = System.nanoTime();

		try {
			IStepWorker<T, ?, C, D> worker = versionStep.step().createWorker(config);
			if (worker.shouldExecute(this, context)) {
				status = worker.runGeneric(
					this,
					context,
					this.getDescription().stepInputMap().get(versionStep.step()).apply(this.getFilesystemStorage(), results),
					results
				);
				if (status.results() != results) {
					results.addAll(status.results());
				}
			} else {
				status = StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
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
				MiscHelper.println("\tStep '%s' for %s (%s) \u001B[32msucceeded\u001B[0m (%s)", versionStep.step().getName(), context, config, timeInfo);
			case UP_TO_DATE ->
				MiscHelper.println("\tStep '%s' for %s (%s) was \u001B[32malready up-to-date\u001B[0m", versionStep.step().getName(), context, config);
			case NOT_RUN -> {
				if (Library.CONF_GLOBAL.printNotRunSteps()) {
					MiscHelper.println("Step '%s' for %s (%s) was \u001B[36mnot run\u001B[0m", versionStep.step().getName(), context, config);
				}
			}
			default -> { }
		}

		if (status.status() == StepStatus.FAILED) {
			String message = String.format("Step '%s' for %s (%s) \u001B[31mfailed\u001B[0m (%s)", versionStep.step().getName(), context, config, timeInfo);

			if (exception == null) {
				MiscHelper.panic(message);
			} else {
				MiscHelper.panicBecause(exception, message);
			}
		}
	}

	public void runFully(RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
		InFlightExecutionPlan<T, C, D> executionPlan = InFlightExecutionPlan.create(this.getDescription(), versionGraph);
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Pipeline-Executor-" + this.getDescription().descriptionName()).factory())) {
			if (Library.CONF_GLOBAL.maxParallelPipelineSteps() > 0) {
				this.threadLimiter = new Semaphore(Library.CONF_GLOBAL.maxParallelPipelineSteps());
			}
			executionPlan.run(executor, this, repository, versionGraph);
		}
		if (!executionPlan.failedTasks().isEmpty()) {
			executionPlan.failedTasks().forEach((key, value) -> {
				MiscHelper.println("Step %s for version %s failed: %s", key.step().getName(), key.version().friendlyVersion(), value);
				value.printStackTrace();
			});
			MiscHelper.panic("Execution failed, for more information see trace(s) above");
		}
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> void run(PipelineDescription<T, C, D> description, PipelineFilesystemStorage<T, C, D> storage, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) throws Exception {
		MiscHelper.println("========== Running Pipeline '%s' ==========", description.descriptionName());
		IPipeline<T, C, D> pipeline = new IPipeline<>(description, storage);
		pipeline.runFully(repository, versionGraph);
	}
}
