package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record MappingsProvider(StepWorker.Config config) implements StepWorker<OrderedVersion, StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StepInput.Empty input, StepResults<OrderedVersion> results) throws Exception {
		Mapping mapping = config.mappingFlavour().getMappingImpl();
		OrderedVersion mcVersion = context.targetVersion();
		StepStatus mergedStatus = mapping.provideMappings(mcVersion, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return StepOutput.ofEmptyResultSet(mergedStatus);
		}
		StepStatus clientStatus = mapping.provideMappings(mcVersion, MinecraftJar.CLIENT);
		StepStatus serverStatus = mapping.provideMappings(mcVersion, MinecraftJar.SERVER);
		return StepOutput.ofEmptyResultSet(StepStatus.merge(clientStatus, serverStatus));
	}
}
