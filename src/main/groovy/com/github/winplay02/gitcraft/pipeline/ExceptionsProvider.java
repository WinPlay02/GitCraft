package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.types.OrderedVersion;

public record ExceptionsProvider(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		OrderedVersion mcVersion = context.minecraftVersion();
		StepStatus mergedStatus = config.exceptionsFlavour().provide(mcVersion, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = config.exceptionsFlavour().provide(mcVersion, MinecraftJar.CLIENT);
		StepStatus serverStatus = config.exceptionsFlavour().provide(mcVersion, MinecraftJar.SERVER);
		return StepStatus.merge(clientStatus, serverStatus);
	}
}
