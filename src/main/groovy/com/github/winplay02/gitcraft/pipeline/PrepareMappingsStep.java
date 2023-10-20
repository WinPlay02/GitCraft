package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public class PrepareMappingsStep extends Step {
	@Override
	public String getName() {
		return STEP_PREPARE_MAPPINGS;
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour) throws Exception {
		return mappingFlavour.getMappingImpl().prepareMappings(mcVersion);
	}
}
