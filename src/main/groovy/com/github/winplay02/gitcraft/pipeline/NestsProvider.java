package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.types.OrderedVersion;

public record NestsProvider(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		OrderedVersion mcVersion = context.minecraftVersion();
		StepStatus mergedStatus = config.nestsFlavour().provide(mcVersion, MinecraftJar.MERGED, config.mappingFlavour());
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = config.nestsFlavour().provide(mcVersion, MinecraftJar.CLIENT, config.mappingFlavour());
		StepStatus serverStatus = config.nestsFlavour().provide(mcVersion, MinecraftJar.SERVER, config.mappingFlavour());
		return StepStatus.merge(clientStatus, serverStatus);
	}
}
