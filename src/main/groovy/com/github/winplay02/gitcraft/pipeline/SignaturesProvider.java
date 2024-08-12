package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.signatures.SignaturesPatch;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record SignaturesProvider(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		SignaturesPatch signatures = config.signaturesFlavour().getSignaturesImpl();
		OrderedVersion mcVersion = context.minecraftVersion();
		StepStatus mergedStatus = signatures.provideSignatures(mcVersion, MinecraftJar.MERGED);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = signatures.provideSignatures(mcVersion, MinecraftJar.CLIENT);
		StepStatus serverStatus = signatures.provideSignatures(mcVersion, MinecraftJar.SERVER);
		return StepStatus.merge(clientStatus, serverStatus);
	}
}
