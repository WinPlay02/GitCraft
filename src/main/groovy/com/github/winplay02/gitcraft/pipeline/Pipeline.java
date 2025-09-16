package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.unpick.UnpickFlavour;
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
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;

public class Pipeline<T extends AbstractVersion<T>> {

	private final PipelineDescription<T> pipelineDescription;
	private final PipelineFilesystemStorage<T> pipelineFilesystemStorage;
	private final Map<Tuple2<StorageKey, T>, Tuple2<T, StepWorker.Config>> overriddenPaths = new ConcurrentHashMap<>();
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

	public Path getStoragePath(StorageKey key, StepWorker.Context<T> context, StepWorker.Config config) {
		Tuple2<StorageKey, T> versionOverride = Tuple.tuple(key, context.targetVersion());
		if (this.overriddenPaths.containsKey(versionOverride)) {
			Tuple2<T, StepWorker.Config> override = this.overriddenPaths.get(versionOverride);
			return this.getStoragePath(key, context.withDifferingVersion(override.getV1()), override.getV2());
		}
		return this.getFilesystemStorage().getPath(key, context, config);
	}

	protected void relinkStoragePathToDifferentVersion(StorageKey key, StepWorker.Context<T> context, StepWorker.Config config, T version) {
		this.overriddenPaths.put(Tuple.tuple(key, context.targetVersion()), Tuple.tuple(version, config));
	}

	protected void runSingleVersionSingleStep(TupleVersionStep<T> versionStep, StepWorker.Context<T> context, StepWorker.Config config) {
		StepResults<T> results = this.versionedResults.computeIfAbsent(versionStep.version(), version -> StepResults.ofEmpty());

		MiscHelper.println("Performing step '%s' for %s (%s)...", versionStep.step().getName(), context, config);

		StepOutput<T> status = null;
		Exception exception = null;

		long timeStart = System.nanoTime();

		try {
			StepWorker<T, ?> worker = (StepWorker<T, ?>) versionStep.step().createWorker(config);
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
					// If the execution scope covers multiple versions, the step must go through them one at a time
					// Ergo, a step for some version depends on execution on all previous versions, but this dependency
					// applies transitively so declaring it only for the directly previous version suffices
					if (step.getScope().coversMultipleVersions()) {
						for (T previousVersion : versionGraph.getPreviousVertices(version)) {
							stepVersionSubsetEdges.get(node).add(new TupleVersionStep<T>(step, previousVersion));
						}
					}
					// Steps depending on other steps always stay within one version
					for (StepDependency dependency : description.getStepDependencies(step)) {
						if (dependency.relation().isDependency()) {
							stepVersionSubsetEdges.get(node).add(new TupleVersionStep<T>(dependency.step(), version));
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
										   Set<TupleVersionStep<T>> completedTasks,
										   Set<TupleVersionStep<T>> executingTasks,
										   Set<Step> executingSteps,
										   Map<TupleVersionStep<T>, Exception> failedTasks,
										   Map<T, StepWorker.Context<T>> versionedContexts,
										   Map<T, StepWorker.Config> versionedConfigs,
										   Object executionLock,
										   Object conditionalVar) {

		public static <T extends AbstractVersion<T>> InFlightExecutionPlan<T> create(PipelineDescription<T> description, AbstractVersionGraph<T> versionGraph) {
			return new InFlightExecutionPlan<T>(PipelineExecutionGraph.populate(description, versionGraph), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new Object(), new Object());
		}

		private StepWorker.Context<T> getContext(T version, RepoWrapper repository, AbstractVersionGraph<T> versionGraph, ExecutorService executorService) {
			return this.versionedContexts().computeIfAbsent(version, minecraftVersion -> new StepWorker.Context<T>(repository, versionGraph, minecraftVersion, executorService));
		}

		private Map<MinecraftJar, String> createIdentifierMap(OrderedVersion version) {
			Map<MinecraftJar, String> map = new HashMap<>();
			if (version.hasClientCode()) {
				map.put(MinecraftJar.CLIENT, version.clientJar().sha1sum().substring(0, 8));
			}
			if (version.hasServerCode()) {
				map.put(MinecraftJar.SERVER, MiscHelper.coalesce(version.serverDist().serverJar(), version.serverDist().windowsServer(), version.serverDist().serverZip()).sha1sum().substring(0, 8));
			}
			if (version.hasClientCode() && version.hasServerCode()) {
				map.put(MinecraftJar.MERGED, String.format("%s-%s", map.get(MinecraftJar.CLIENT), map.get(MinecraftJar.SERVER)));
			}
			return map;
		}

		private StepWorker.Config getConfig(T version) {
			return this.versionedConfigs().computeIfAbsent(version, minecraftVersion -> new StepWorker.Config(
				createIdentifierMap((OrderedVersion) minecraftVersion),
				GitCraft.getApplicationConfiguration().getMappingsForMinecraftVersion((OrderedVersion) minecraftVersion).orElse(MappingFlavour.IDENTITY_UNMAPPED),
				GitCraft.getApplicationConfiguration().getUnpickForMinecraftVersion((OrderedVersion) minecraftVersion).orElse(UnpickFlavour.NONE),
				GitCraft.getApplicationConfiguration().getExceptionsForMinecraftVersion((OrderedVersion) minecraftVersion).orElse(ExceptionsFlavour.NONE),
				GitCraft.getApplicationConfiguration().getSignaturesForMinecraftVersion((OrderedVersion) minecraftVersion).orElse(SignaturesFlavour.NONE),
				GitCraft.getApplicationConfiguration().getNestsForMinecraftVersion((OrderedVersion) minecraftVersion).orElse(NestsFlavour.NONE),
				GitCraft.getApplicationConfiguration().patchLvt(),
				GitCraft.getApplicationConfiguration().enablePreening()
			)); // TODO fix dependency on OrderedVersion
		}

		private void runSingleTask(ExecutorService executor, TupleVersionStep<T> task, Pipeline<T> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
			executor.execute(() -> {
				synchronized (executionLock) {
					if (executingTasks.contains(task) || completedTasks.contains(task)) {
						return;
					}
					if (task.step().getScope() == ExecutionScope.GRAPH && executingSteps.contains(task.step())) {
						return;
					}

					executingTasks.add(task);
					executingSteps.add(task.step());
				}

				if (pipeline.threadLimiter != null) {
					pipeline.threadLimiter.acquireUninterruptibly();
				}

				StepWorker.Context<T> context = this.getContext(task.version(), repository, versionGraph, executor);
				StepWorker.Config config = this.getConfig(task.version());

				Exception exception = null;

				try {
					if (!pipeline.pipelineDescription.skipVersion().apply(versionGraph, context)) {
						pipeline.runSingleVersionSingleStep(task, context, config);
					} else {
						MiscHelper.println("Skipping step '%s' for %s (%s)...", task.step().getName(), context, config);
					}
				} catch (Exception e) {
					exception = e;

					MiscHelper.println("Step '%s' for %s (%s) failed: %s", task.step().getName(), context, config, e);
					exception.printStackTrace();
				}

				if (pipeline.threadLimiter != null) {
					pipeline.threadLimiter.release();
				}

				synchronized (executionLock) {
					if (exception == null) {
						// success :)
						executingTasks.remove(task);
						executingSteps.remove(task.step());
						completedTasks.add(task);
					} else {
						// failure :(
						failedTasks.put(task, exception);
					}

					signalUpdate();

					if (exception == null) {
						scanForTasks(executor, pipeline, repository, versionGraph);
					} else {
						executor.shutdown();
					}
				}
			});
		}

		private void scanForTasks(ExecutorService executor, Pipeline<T> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
			for (TupleVersionStep<T> task : executionGraph.nextTuples(completedTasks)) {
				runSingleTask(executor, task, pipeline, repository, versionGraph);
			}
		}

		public void run(ExecutorService executor, Pipeline<T> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
			scanForTasks(executor, pipeline, repository, versionGraph);
			await();
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
				synchronized (executionLock) {
					// Once everything is completed
					if (this.completedTasks().size() == this.executionGraph().stepVersionSubsetVertices().size()) {
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
	}

	private Semaphore threadLimiter = null;

	public void runFully(RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
		InFlightExecutionPlan<T> executionPlan = InFlightExecutionPlan.create(this.getDescription(), versionGraph);
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

	public static <T extends AbstractVersion<T>> void run(PipelineDescription<T> description, PipelineFilesystemStorage<T> storage, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) throws Exception {
		MiscHelper.println("========== Running Pipeline '%s' ==========", description.descriptionName());
		Pipeline<T> pipeline = new Pipeline<>(description, storage);
		pipeline.runFully(repository, versionGraph);
	}
}
