package com.github.winplay02.gitcraft.exceptions;

import java.io.IOException;
import java.nio.file.Path;

import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import net.ornithemc.exceptor.io.ExceptionsFile;

public class NoneExceptions extends ExceptionsPatch {

	@Override
	public String getName() {
		return "None";
	}

	@Override
	public boolean doExceptionsExist(OrderedVersion mcVersion) {
		return true;
	}

	@Override
	public boolean doExceptionsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return true;
	}

	@Override
	public boolean canExceptionsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return true;
	}

	@Override
	public StepStatus provideExceptions(StepWorker.Context<OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		return StepStatus.SUCCESS;
	}

	@Override
	protected Path getExceptionsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return null;
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, ExceptionsFile visitor) throws IOException {
	}
}
