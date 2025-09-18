package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;

public interface IStepWorker<T extends AbstractVersion<T>, S extends StepInput, C extends IStepContext<C, T>, D extends IStepConfig> {
	D config();

	StepOutput<T, C, D> run(IPipeline<T, C, D> pipeline, C context, S input, StepResults<T, C, D> results) throws Exception;

	default StepOutput<T, C, D> runGeneric(IPipeline<T, C, D> pipeline, C context, StepInput input, StepResults<T, C, D> results) throws Exception {
		@SuppressWarnings("unchecked")
		S castInput = (S) input;
		return this.run(pipeline, context, castInput, results);
	}

	default boolean shouldExecute(IPipeline<T, C, D> pipeline, C context) {
		return true;
	}
}
