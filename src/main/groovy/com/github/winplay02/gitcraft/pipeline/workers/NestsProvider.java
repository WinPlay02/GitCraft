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

public record NestsProvider(GitCraftStepConfig config) implements GitCraftStepWorker<StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		StepInput.Empty input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		StepStatus mergedStatus = config.nestsFlavour().provide(context, MinecraftJar.MERGED, config.mappingFlavour());
		if (mergedStatus.hasRun()) {
			return StepOutput.ofEmptyResultSet(mergedStatus);
		}
		StepStatus clientStatus = config.nestsFlavour().provide(context, MinecraftJar.CLIENT, config.mappingFlavour());
		StepStatus serverStatus = config.nestsFlavour().provide(context, MinecraftJar.SERVER, config.mappingFlavour());
		return StepOutput.ofEmptyResultSet(StepStatus.merge(clientStatus, serverStatus));
	}
}
