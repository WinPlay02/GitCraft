package com.github.winplay02.gitcraft.exceptions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.ExceptorIo;

public abstract class ExceptionsPatch {

	public abstract String getName();

	/**
	 * @return whether exceptions of this flavour exist for the given minecraft version.
	 */
	public abstract boolean doExceptionsExist(OrderedVersion mcVersion);

	/**
	 * @return whether exceptions of this flavour exist for the given jar for the given minecraft version.
	 */
	public abstract boolean doExceptionsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * @return whether exceptions of this flavour can be used on the given jar for the given minecraft version.
	 */
	public abstract boolean canExceptionsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * After calling this method, a path returned by {@link #getExceptionsPath(OrderedVersion, MinecraftJar)} should be valid.
	 * This is only true if {@link #doExceptionsExist(OrderedVersion, MinecraftJar)} returns true for the version.
	 *
	 * @param mcVersion Version
	 * @param minecraftJar Minecraft jar
	 * @return A result
	 */
	public abstract StepStatus provideExceptions(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException;

	/**
	 * Should return a path to a exceptions file, created by {@link #provideExceptions(OrderedVersion, MinecraftJar)}
	 *
	 * @param mcVersion Version
	 * @param minecraftJar Minecraft jar
	 * @return path to exceptions file
	 */
	public final Optional<Path> getExceptionsPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return Optional.ofNullable(getExceptionsPathInternal(mcVersion, minecraftJar));
	}

	protected abstract Path getExceptionsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * Visits exceptions of this flavour for the given jar for the given minecraft version, using the given visitor.
	 * The visitor will only be called if {@link #canExceptionsBeUsedOn(OrderedVersion, MinecraftJar)} returns true for the version.
	 */
	public abstract void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, ExceptionsFile visitor) throws IOException;

	public final ExceptionsFile getExceptions(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		if (!canExceptionsBeUsedOn(mcVersion, minecraftJar)) {
			MiscHelper.panic("Tried to use %s-exceptions for version %s, %s jar. These exceptions can not be used for this version.", this, mcVersion.launcherFriendlyVersionName(), minecraftJar.name().toLowerCase());
		}
		ExceptionsFile exceptions = new ExceptionsFile();
		try {
			visit(mcVersion, minecraftJar, exceptions);
		} catch (IOException e) {
			MiscHelper.panicBecause(e, "An error occurred while getting exceptions information for %s (version %s)", this, mcVersion.launcherFriendlyVersionName());
		}
		return exceptions;
	}

	protected static boolean validateExceptions(Path exceptionsPath) {
		try {
			ExceptorIo.read(exceptionsPath);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
