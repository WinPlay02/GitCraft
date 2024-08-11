package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.Nest;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record NestsProvider(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		Nest nest = config.nestsFlavour().getNestsImpl();
		OrderedVersion mcVersion = context.minecraftVersion();
		MappingFlavour mappingFlavour = config.mappingFlavour();
		StepStatus mergedStatus = nest.provideNests(mcVersion, MinecraftJar.MERGED, mappingFlavour);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = nest.provideNests(mcVersion, MinecraftJar.CLIENT, mappingFlavour);
		StepStatus serverStatus = nest.provideNests(mcVersion, MinecraftJar.SERVER, mappingFlavour);
		return StepStatus.merge(clientStatus, serverStatus);
	}
}
