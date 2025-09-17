package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public record InFlightExecutionPlan<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig>(PipelineExecutionGraph<T, C, D> executionGraph,
																			Set<IPipeline.TupleVersionStep<T, C, D>> completedSubset,
																			Set<IPipeline.TupleVersionStep<T, C, D>> executingSubset,
																			Set<IStep<T, ?, C, D>> activeSteps,
																			Map<IPipeline.TupleVersionStep<T, C, D>, Exception> failedTasks,
																			Map<T, C> versionedContexts,
																			Map<T, D> versionedConfigs,
																			Object conditionalVar) {

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> InFlightExecutionPlan<T, C, D> create(PipelineDescription<T, C, D> description, AbstractVersionGraph<T> versionGraph) {
		return new InFlightExecutionPlan<>(PipelineExecutionGraph.populate(description, versionGraph), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new Object());
	}

	private void runSingleTask(ExecutorService executor, IPipeline.TupleVersionStep<T, C, D> task, IPipeline<T, C, D> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
		executor.execute(() -> {
			if (executingSubset.contains(task) || completedSubset.contains(task)) {
				return;
			}
			if (task.step().getParallelismPolicy().isRestrictedToSequential() && activeSteps.contains(task.step())) {
				return;
			}
			activeSteps.add(task.step());
			executingSubset.add(task);
			if (pipeline.threadLimiter() != null) {
				pipeline.threadLimiter().acquireUninterruptibly();
			}
			C context = this.versionedContexts().computeIfAbsent(task.version(), ctxVersion -> pipeline.getDescription().contextCreator().getContext(ctxVersion, repository, versionGraph, executor));
			D config = this.versionedConfigs().computeIfAbsent(task.version(), pipeline.getDescription().configCreator());
			boolean failed = false;
			try {
				if (!pipeline.getDescription().skipVersion().apply(versionGraph, context)) {
					pipeline.runSingleVersionSingleStep(task, context, config);
				} else {
					MiscHelper.println("Skipping step '%s' for %s (%s)...", task.step().getName(), context, config);
				}
				executingSubset.remove(task);
				completedSubset.add(task);
				activeSteps.remove(task.step());
			} catch (Exception e) {
				failedTasks.put(task, e);
				failed = true;
				MiscHelper.println("Step '%s' for %s (%s) failed: %s", task.step().getName(), context, config, e);
				e.printStackTrace();
			}
			signalUpdate();
			if (pipeline.threadLimiter() != null) {
				pipeline.threadLimiter().release();
			}
			if (!failed) {
				scanForTasks(executor, pipeline, repository, versionGraph);
			} else {
				executor.shutdown();
			}
		});
	}

	private synchronized void scanForTasks(ExecutorService executor, IPipeline<T, C, D> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
		Set<IPipeline.TupleVersionStep<T, C, D>> nextTasks = executionGraph.nextTuples(this.completedSubset());
		nextTasks.stream().filter(Predicate.not(this.executingSubset()::contains)).forEach(task -> this.runSingleTask(executor, task, pipeline, repository, versionGraph));
	}

	public void run(ExecutorService executor, IPipeline<T, C, D> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
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
