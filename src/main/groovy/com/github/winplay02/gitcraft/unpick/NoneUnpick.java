package com.github.winplay02.gitcraft.unpick;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import java.io.IOException;

public class NoneUnpick implements Unpick {
	@Override
	public StepStatus provideUnpick(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		return StepStatus.NOT_RUN;
	}

	@Override
	public UnpickContext getContext(OrderedVersion targetVersion, MinecraftJar minecraftJar) throws IOException {
		return null;
	}

	@Override
	public boolean doesUnpickInformationExist(OrderedVersion mcVersion) {
		return true;
	}

	@Override
	public MappingFlavour applicableMappingFlavour(UnpickDescriptionFile unpickDescription) {
		return null;
	}

	@Override
	public boolean supportsUnpickRemapping(UnpickDescriptionFile unpickDescription) {
		return false;
	}
}
