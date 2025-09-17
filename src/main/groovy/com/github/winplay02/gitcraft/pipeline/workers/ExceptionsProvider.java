package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepConfig;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record ExceptionsProvider(GitCraftStepConfig config) implements GitCraftStepWorker<StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		StepInput.Empty input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		StepStatus mergedStatus = config.exceptionsFlavour().provide(context, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return StepOutput.ofEmptyResultSet(mergedStatus);
		}
		StepStatus clientStatus = config.exceptionsFlavour().provide(context, MinecraftJar.CLIENT);
		StepStatus serverStatus = config.exceptionsFlavour().provide(context, MinecraftJar.SERVER);
		return StepOutput.ofEmptyResultSet(StepStatus.merge(clientStatus, serverStatus));
	}
}
