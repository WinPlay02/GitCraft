package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import groovy.lang.Tuple;
import groovy.lang.Tuple2;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Pipeline<T extends AbstractVersion<T>> {

	private final PipelineDescription<T> pipelineDescription;
	private final PipelineFilesystemStorage<T> pipelineFilesystemStorage;
	private final Map<Tuple2<StorageKey, T>, T> overriddenPaths = new ConcurrentHashMap<>();
	private final Map<T, StepResults<T>> versionedResults = new ConcurrentHashMap<>();

	public Pipeline(PipelineDescription<T> pipelineDescription, PipelineFilesystemStorage<T> pipelineFilesystemStorage) {
		this.pipelineDescription = pipelineDescription;
		this.pipelineFilesystemStorage = pipelineFilesystemStorage;
		this.pipelineDescription.validate();
	}

	public PipelineDescription<T> getDescription() {
		return this.pipelineDescription;
	}

	public PipelineFilesystemStorage<T> getFilesystemStorage() {
		return this.pipelineFilesystemStorage;
	}

	public Path getStoragePath(StorageKey key, StepWorker.Context<T> context) {
		Tuple2<StorageKey, T> versionOverride = Tuple.tuple(key, context.targetVersion());
		if (this.overriddenPaths.containsKey(versionOverride)) {
			return this.getStoragePath(key, context.withDifferingVersion(this.overriddenPaths.get(versionOverride)));
		}
		return this.getFilesystemStorage().getPath(key, context);
	}

	protected void relinkStoragePathToDifferentVersion(StorageKey key, StepWorker.Context<T> context, T version) {
		this.overriddenPaths.put(Tuple.tuple(key, context.targetVersion()), version);
	}

	protected void runSingleVersionSingleStep(TupleVersionStep<T> versionStep, StepWorker.Context<T> context, StepWorker.Config config) {
		StepResults<T> results = this.versionedResults.computeIfAbsent(versionStep.version(), version -> StepResults.ofEmpty());

		MiscHelper.println("Performing step '%s' for %s (%s)...", versionStep.step().getName(), context, config);

		StepOutput<T> status = null;
		Exception exception = null;

		long timeStart = System.nanoTime();

		try {
			StepWorker<T, ?> worker = (StepWorker<T, ?>) versionStep.step().createWorker(config);
			status = worker.runGeneric(
				this,
				context,
				this.getDescription().stepInputMap().get(versionStep.step()).apply(this.getFilesystemStorage(), results),
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
				MiscHelper.println("\tStep '%s' for %s (%s) \u001B[32msucceeded\u001B[0m (%s)", versionStep.step().getName(), context, config, timeInfo);
			case UP_TO_DATE ->
				MiscHelper.println("\tStep '%s' for %s (%s) was \u001B[32malready up-to-date\u001B[0m", versionStep.step().getName(), context, config);
			case NOT_RUN -> {
				if (GitCraft.config.printNotRunSteps) {
					MiscHelper.println("Step '%s' for %s (%s) was \u001B[36mnot run\u001B[0m", versionStep.step().getName(), context, config);
				}
			}
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

	protected record TupleVersionStep<T extends AbstractVersion<T>>(Step step, T version) {
	}

	protected record PipelineExecutionGraph<T extends AbstractVersion<T>>(Set<TupleVersionStep<T>> stepVersionSubsetVertices, Map<TupleVersionStep<T>, Set<TupleVersionStep<T>>> stepVersionSubsetEdges) {
		public static <T extends AbstractVersion<T>> PipelineExecutionGraph<T> populate(PipelineDescription<T> description, AbstractVersionGraph<T> versionGraph) {
			Set<TupleVersionStep<T>> stepVersionSubsetVertices = new HashSet<>();
			// directed: (target, source)
			Map<TupleVersionStep<T>, Set<TupleVersionStep<T>>> stepVersionSubsetEdges = new HashMap<>();
			for (T version : versionGraph) {
				for (Step step : description.steps()) {
					TupleVersionStep<T> node = new TupleVersionStep<T>(step, version);
					stepVersionSubsetVertices.add(node);
					stepVersionSubsetEdges.computeIfAbsent(node, __ -> new HashSet<>());
					// Inter-Version dependency: depend on previous version only; logically should depend on all previous versions
					// but this is not necessary as this dependency applies transitively in a valid pipeline description (step depending on itself)
					for (Step interVersionDependencyStep : description.getInterVersionDependencies(step)) {
						for (T previousVersion : versionGraph.getPreviousVertices(version)) {
							stepVersionSubsetEdges.get(node).add(new TupleVersionStep<T>(interVersionDependencyStep, previousVersion));
						}
					}
					// Intra-Version dependency
					for (Step intraVersionDependencyStep : description.getIntraVersionDependencies(step)) {
						DependencyType depType = description.getDependencyType(step, intraVersionDependencyStep);
						if (depType != null && depType.isDependency()) {
							stepVersionSubsetEdges.get(node).add(new TupleVersionStep<T>(intraVersionDependencyStep, version));
						}
					}
				}
			}
			// TODO validate execution graph
			return new PipelineExecutionGraph<T>(Collections.unmodifiableSet(stepVersionSubsetVertices), Collections.unmodifiableMap(stepVersionSubsetEdges));
		}

		public Set<TupleVersionStep<T>> nextTuples(Set<TupleVersionStep<T>> completedSubset) {
			return stepVersionSubsetEdges.entrySet().stream().filter(entry -> !completedSubset.contains(entry.getKey())).filter(entry -> MiscHelper.calculateAsymmetricSetDifference(entry.getValue(), completedSubset).isEmpty()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
		}
	}

	protected record InFlightExecutionPlan<T extends AbstractVersion<T>>(PipelineExecutionGraph<T> executionGraph,
										   Set<TupleVersionStep<T>> completedSubset,
										   Set<TupleVersionStep<T>> executingSubset,
										   Map<TupleVersionStep<T>, Exception> failedTasks,
										   Map<T, StepWorker.Context<T>> versionedContexts,
										   Map<T, StepWorker.Config> versionedConfigs,
										   Object conditionalVar) {

		public static <T extends AbstractVersion<T>> InFlightExecutionPlan<T> create(PipelineDescription<T> description, AbstractVersionGraph<T> versionGraph) {
			return new InFlightExecutionPlan<T>(PipelineExecutionGraph.populate(description, versionGraph), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new Object());
		}

		private StepWorker.Context<T> getContext(T version, RepoWrapper repository, AbstractVersionGraph<T> versionGraph, ExecutorService executorService) {
			return this.versionedContexts().computeIfAbsent(version, minecraftVersion -> new StepWorker.Context<T>(repository, versionGraph, minecraftVersion, executorService));
		}

		private StepWorker.Config getConfig(T version) {
			return this.versionedConfigs().computeIfAbsent(version, minecraftVersion -> new StepWorker.Config(GitCraft.config.getMappingsForMinecraftVersion((OrderedVersion) minecraftVersion).orElse(MappingFlavour.IDENTITY_UNMAPPED))); // TODO fix dependency on OrderedVersion
		}

		private void runSingleTask(ExecutorService executor, TupleVersionStep<T> task, Pipeline<T> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
			executor.execute(() -> {
				if (executingSubset.contains(task) || completedSubset.contains(task)) {
					return;
				}
				executingSubset.add(task);
				StepWorker.Context<T> context = this.getContext(task.version(), repository, versionGraph, executor);
				StepWorker.Config config = this.getConfig(task.version());
				boolean failed = false;
				try {
					pipeline.runSingleVersionSingleStep(task, context, config);
					executingSubset.remove(task);
					completedSubset.add(task);
				} catch (Exception e) {
					failedTasks.put(task, e);
					failed = true;
					MiscHelper.println("Step %s for version %s failed: %s", task.step(), task.version(), e);
					e.printStackTrace();
				}
				signalUpdate();
				if (!failed) {
					scanForTasks(executor, pipeline, repository, versionGraph);
				} else {
					executor.shutdown();
				}
			});
		}

		private synchronized void scanForTasks(ExecutorService executor, Pipeline<T> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
			Set<TupleVersionStep<T>> nextTasks = executionGraph.nextTuples(this.completedSubset());
			nextTasks.stream().filter(Predicate.not(this.executingSubset()::contains)).forEach(task -> this.runSingleTask(executor, task, pipeline, repository, versionGraph));
		}

		public void run(ExecutorService executor, Pipeline<T> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
			scanForTasks(executor, pipeline, repository, versionGraph);
			await();
		}

		public int runningTasks() {
			return this.executingSubset.size();
		}

		private void signalUpdate() {
			synchronized (conditionalVar) {
				conditionalVar.notifyAll();
			}
		}

		private void await() {
			while (true) {
				synchronized (conditionalVar) {
					try {
						conditionalVar.wait();
					} catch (InterruptedException ignored) {}
				}
				// Once everything is completed
				if (this.completedSubset().size() == this.executionGraph().stepVersionSubsetVertices().size()) {
					return;
				}
				// If anything failed, report
				if (!this.failedTasks().isEmpty()) {
					MiscHelper.println("Execution failed, waiting for existing tasks to complete...");
					return;
				}
			}
		}
	}

	public void runFully(RepoWrapper repository, AbstractVersionGraph<T> versionGraph) throws Exception {
		InFlightExecutionPlan<T> executionPlan = InFlightExecutionPlan.create(this.getDescription(), versionGraph);
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Pipeline-Executor-" + this.getDescription().descriptionName()).factory())) {
			executionPlan.run(executor, this, repository, versionGraph);
			/*while (true) {
				try {
					if (executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)) {
						break;
					}
				} catch (InterruptedException ignored) {}
			}*/
		}
		if (!executionPlan.failedTasks().isEmpty()) {
			executionPlan.failedTasks().forEach((key, value) -> {
				MiscHelper.println("Step %s for version %s failed: %s", key.step(), key.version(), value);
				value.printStackTrace();
			});
			MiscHelper.panic("Execution failed, for more information see trace(s) above");
		}
		// TODO remove this, after fixing other TODO
		/*for (OrderedVersion mcVersion : versionGraph) {
			if (repository == null || !repository.existsRevWithCommitMessage(mcVersion.toCommitMessage())) { // TODO this skip condition should be applied to the version graph directly, not during execution
				this.runSingleVersion(repository, versionGraph, mcVersion);
			}
		}*/
	}

	public static <T extends AbstractVersion<T>> void run(PipelineDescription<T> description, PipelineFilesystemStorage<T> storage, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) throws Exception {
		Pipeline<T> pipeline = new Pipeline<>(description, storage);
		pipeline.runFully(repository, versionGraph);
	}
}
