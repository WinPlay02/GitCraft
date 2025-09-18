package com.github.winplay02.gitcraft.nests;

import java.io.IOException;
import java.nio.file.Path;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import net.ornithemc.nester.nest.Nests;

public class NoneNests extends Nest {

	@Override
	public String getName() {
		return "None";
	}

	@Override
	public boolean doNestsExist(OrderedVersion mcVersion) {
		return true;
	}

	@Override
	public boolean doNestsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return true;
	}

	@Override
	public boolean canNestsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		return true;
	}

	@Override
	public StepStatus provideNests(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) throws IOException {
		return StepStatus.SUCCESS;
	}

	@Override
	protected Path getNestsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		return null;
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour, Nests visitor) throws IOException {
	}
}
