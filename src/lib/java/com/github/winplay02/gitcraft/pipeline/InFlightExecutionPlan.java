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
																			Object executionLock,
																			Object conditionalVar) {

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> InFlightExecutionPlan<T, C, D> create(PipelineDescription<T, C, D> description, AbstractVersionGraph<T> versionGraph) {
		return new InFlightExecutionPlan<>(PipelineExecutionGraph.populate(description, versionGraph), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new Object(), new Object());
	}

	private void runSingleTask(ExecutorService executor, IPipeline.TupleVersionStep<T, C, D> task, IPipeline<T, C, D> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
		if (executor.isShutdown()) {
			return;
		}

		executor.execute(() -> {
			synchronized (executionLock) {
				if (executingSubset.contains(task) || completedSubset.contains(task)) {
					return;
				}
				if (task.step().getParallelismPolicy().isRestrictedToSequential() && activeSteps.contains(task.step())) {
					return;
				}

				activeSteps.add(task.step());
				executingSubset.add(task);
			}

			if (pipeline.threadLimiter() != null) {
				pipeline.threadLimiter().acquireUninterruptibly();
			}

			C context = this.versionedContexts().computeIfAbsent(task.version(), ctxVersion -> pipeline.getDescription().contextCreator().getContext(ctxVersion, repository, versionGraph, executor));
			D config = this.versionedConfigs().computeIfAbsent(task.version(), pipeline.getDescription().configCreator());
			Exception storedException = null;

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
				storedException = e;
				MiscHelper.println("Step '%s' for %s (%s) failed: %s", task.step().getName(), context, config, e);
				e.printStackTrace();
			}
			if (pipeline.threadLimiter() != null) {
				pipeline.threadLimiter().release();
			}

			synchronized (executionLock) {
				if (storedException == null) {
					// success :)
					executingSubset.remove(task);
					activeSteps.remove(task.step());
					completedSubset.add(task);
				} else {
					// failure :(
					failedTasks.put(task, storedException);
				}

				signalUpdate();

				if (storedException == null) {
					scanForTasks(executor, pipeline, repository, versionGraph);
				} else {
					executor.shutdown();
				}
			}
		});
	}

	private void scanForTasks(ExecutorService executor, IPipeline<T, C, D> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
		// These are approximations of the set of tasks to execute; they should be equal or greater than the actual set; duplicate tasks get discarded later
		for (IPipeline.TupleVersionStep<T, C, D> task : this.executionGraph.nextTuples(this.completedSubset)) {
			this.runSingleTask(executor, task, pipeline, repository, versionGraph);
		}
	}

	public void run(ExecutorService executor, IPipeline<T, C, D> pipeline, RepoWrapper repository, AbstractVersionGraph<T> versionGraph) {
		scanForTasks(executor, pipeline, repository, versionGraph);
		await();
	}

	public int runningTasks() { // doesn't need to be absolutely accurate, when called concurrently; is intended to display some (approximate) information on the screen
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
			synchronized (executionLock) {
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
}
