package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.exceptions.ExceptionsPatch;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record ExceptionsProvider(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		ExceptionsPatch exceptions = config.exceptionsFlavour().getExceptionsImpl();
		OrderedVersion mcVersion = context.minecraftVersion();
		StepStatus mergedStatus = exceptions.provideExceptions(mcVersion, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = exceptions.provideExceptions(mcVersion, MinecraftJar.CLIENT);
		StepStatus serverStatus = exceptions.provideExceptions(mcVersion, MinecraftJar.SERVER);
		return StepStatus.merge(clientStatus, serverStatus);
	}
}
