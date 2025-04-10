package com.github.winplay02.gitcraft.exceptions;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import net.ornithemc.exceptor.io.ExceptionsFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public enum ExceptionsFlavour {
	RAVEN(GitCraft.RAVEN_EXCEPTIONS),
	NONE(GitCraft.NONE_EXCEPTIONS);

	private final LazyValue<? extends ExceptionsPatch> impl;

	ExceptionsFlavour(LazyValue<? extends ExceptionsPatch> exceptions) {
		this.impl = exceptions;
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}

	public String getName() {
		return impl.get().getName();
	}

	public boolean exists(OrderedVersion mcVersion) {
		return impl.get().doExceptionsExist(mcVersion);
	}

	public boolean exists(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().doExceptionsExist(mcVersion, minecraftJar);
	}

	public boolean canBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().canExceptionsBeUsedOn(mcVersion, minecraftJar);
	}

	public StepStatus provide(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		return impl.get().provideExceptions(mcVersion, minecraftJar);
	}

	public Optional<Path> getPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().getExceptionsPath(mcVersion, minecraftJar);
	}

	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, ExceptionsFile visitor) throws IOException {
		impl.get().visit(mcVersion, minecraftJar, visitor);
	}

	public ExceptionsFile getExceptions(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().getExceptions(mcVersion, minecraftJar);
	}
}
