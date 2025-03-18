package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record RepoGarbageCollector(StepWorker.Config config) implements StepWorker<OrderedVersion, StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StepInput.Empty input, StepResults<OrderedVersion> results) throws Exception {
		if (GitCraft.config.noRepo) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		context.repository().gc();
		return StepOutput.ofEmptyResultSet(StepStatus.SUCCESS);
	}
}

// create global executor class, that can reuse? http connections; only use n parallel connections for each origin
