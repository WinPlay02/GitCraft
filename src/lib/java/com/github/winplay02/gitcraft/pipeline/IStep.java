package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;

public interface IStep<T extends AbstractVersion<T>, S extends StepInput, C extends IStepContext<C, T>, D extends IStepConfig> {

	String getName();

	ParallelismPolicy getParallelismPolicy();

	IStepWorker<T, S, C, D> createWorker(D config);
}
