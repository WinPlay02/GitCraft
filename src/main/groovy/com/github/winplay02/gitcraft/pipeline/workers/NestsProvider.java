package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record NestsProvider(StepWorker.Config config) implements StepWorker<OrderedVersion, StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StepInput.Empty input, StepResults<OrderedVersion> results) throws Exception {
		StepStatus mergedStatus = config.nestsFlavour().provide(context, MinecraftJar.MERGED, config.mappingFlavour());
		if (mergedStatus.hasRun()) {
			return StepOutput.ofEmptyResultSet(mergedStatus);
		}
		StepStatus clientStatus = config.nestsFlavour().provide(context, MinecraftJar.CLIENT, config.mappingFlavour());
		StepStatus serverStatus = config.nestsFlavour().provide(context, MinecraftJar.SERVER, config.mappingFlavour());
		return StepOutput.ofEmptyResultSet(StepStatus.merge(clientStatus, serverStatus));
	}
}
