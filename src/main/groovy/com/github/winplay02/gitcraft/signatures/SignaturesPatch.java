package com.github.winplay02.gitcraft.signatures;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import io.github.gaming32.signaturechanger.tree.SigsFile;
import io.github.gaming32.signaturechanger.visitor.SigsReader;

public abstract class SignaturesPatch {

	public abstract String getName();

	/**
	 * @return whether signatures of this flavour exist for the given minecraft version.
	 */
	public abstract boolean doSignaturesExist(OrderedVersion mcVersion);

	/**
	 * @return whether signatures of this flavour exist for the given jar for the given minecraft version.
	 */
	public abstract boolean doSignaturesExist(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * @return whether signatures of this flavour can be used on the given jar for the given minecraft version.
	 */
	public abstract boolean canSignaturesBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * After calling this method, a path returned by {@link #getSignaturesPath(OrderedVersion, MinecraftJar)} should be valid.
	 * This is only true if {@link #doSignaturesExist(OrderedVersion, MinecraftJar)} returns true for the version.
	 *
	 * @param versionContext Version Context
	 * @param minecraftJar Minecraft jar
	 * @return A result
	 */
	public abstract StepStatus provideSignatures(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException;

	/**
	 * Should return a path to a signatures file, created by {@link #provideSignatures(IStepContext, MinecraftJar)}
	 *
	 * @param mcVersion Version
	 * @param minecraftJar Minecraft jar
	 * @return path to signatures file
	 */
	public final Optional<Path> getSignaturesPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return Optional.ofNullable(getSignaturesPathInternal(mcVersion, minecraftJar));
	}

	protected abstract Path getSignaturesPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * Visits signatures of this flavour for the given jar for the given minecraft version, using the given visitor.
	 * The visitor will only be called if {@link #canSignaturesBeUsedOn(OrderedVersion, MinecraftJar)} returns true for the version.
	 */
	public abstract void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, SigsFile visitor) throws IOException;

	public final SigsFile getSignatures(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		if (!canSignaturesBeUsedOn(mcVersion, minecraftJar)) {
			MiscHelper.panic("Tried to use %s-signatures for version %s, %s jar. These signatures can not be used for this version.", this, mcVersion.launcherFriendlyVersionName(), minecraftJar.name().toLowerCase());
		}
		SigsFile signatures = new SigsFile();
		try {
			visit(mcVersion, minecraftJar, signatures);
		} catch (IOException e) {
			MiscHelper.panicBecause(e, "An error occurred while getting signatures information for %s (version %s)", this, mcVersion.launcherFriendlyVersionName());
		}
		return signatures;
	}

	protected static boolean validateSignatures(Path signaturesPath) {
		try (SigsReader sr = new SigsReader(Files.newBufferedReader(signaturesPath))) {
			sr.accept(new SigsFile());
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
