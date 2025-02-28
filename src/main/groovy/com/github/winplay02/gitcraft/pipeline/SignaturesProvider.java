package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.types.OrderedVersion;

public record SignaturesProvider(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		OrderedVersion mcVersion = context.minecraftVersion();
		StepStatus mergedStatus = config.signaturesFlavour().provide(mcVersion, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = config.signaturesFlavour().provide(mcVersion, MinecraftJar.CLIENT);
		StepStatus serverStatus = config.signaturesFlavour().provide(mcVersion, MinecraftJar.SERVER);
		return StepStatus.merge(clientStatus, serverStatus);
	}
}
