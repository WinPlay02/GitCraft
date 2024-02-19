package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import java.io.IOException;
import java.nio.file.Path;

public class IdentityMappings extends Mapping {
	@Override
	public String getName() {
		return "Identity (unmapped)";
	}

	@Override
	public boolean isMappingFileRequired() {
		return false;
	}

	@Override
	public String getDestinationNS() {
		return this.getSourceNS();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return true;
	}

	@Override
	public Step.StepResult prepareMappings(OrderedVersion mcVersion) throws IOException {
		return Step.StepResult.SUCCESS;
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion) {
		return null;
	}

	@Override
	public Path executeCustomRemappingLogic(Path previousFile, OrderedVersion mcVersion) {
		return previousFile;
	}
}
