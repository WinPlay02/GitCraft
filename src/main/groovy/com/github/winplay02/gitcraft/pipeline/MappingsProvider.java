package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record MappingsProvider(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		MappingFlavour mapping = config.mappingFlavour();
		OrderedVersion mcVersion = context.minecraftVersion();
		StepStatus mergedStatus = mapping.provide(mcVersion, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = mapping.provide(mcVersion, MinecraftJar.CLIENT);
		StepStatus serverStatus = mapping.provide(mcVersion, MinecraftJar.SERVER);
		return StepStatus.merge(clientStatus, serverStatus);
	}
}
