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

public record MappingsProvider(StepWorker.Config config) implements StepWorker<StepInput.Empty> {

	@Override
	public StepOutput run(Pipeline pipeline, Context context, StepInput.Empty input, StepResults results) throws Exception {
		Mapping mapping = config.mappingFlavour().getMappingImpl();
		OrderedVersion mcVersion = context.minecraftVersion();
		StepStatus mergedStatus = mapping.provideMappings(mcVersion, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return StepOutput.ofEmptyResultSet(mergedStatus);
		}
		StepStatus clientStatus = mapping.provideMappings(mcVersion, MinecraftJar.CLIENT);
		StepStatus serverStatus = mapping.provideMappings(mcVersion, MinecraftJar.SERVER);
		return StepOutput.ofEmptyResultSet(StepStatus.merge(clientStatus, serverStatus));
	}
}
