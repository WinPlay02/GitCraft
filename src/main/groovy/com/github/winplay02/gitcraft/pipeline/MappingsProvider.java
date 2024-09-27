package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record MappingsProvider(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		Mapping mapping = config.mappingFlavour().getMappingImpl();
		OrderedVersion mcVersion = context.minecraftVersion();
		StepStatus mergedStatus = mapping.provideMappings(mcVersion, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = mapping.provideMappings(mcVersion, MinecraftJar.CLIENT);
		StepStatus serverStatus = mapping.provideMappings(mcVersion, MinecraftJar.SERVER);
		return StepStatus.merge(clientStatus, serverStatus);
	}
}
