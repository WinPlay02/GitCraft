package com.github.winplay02.gitcraft.nests;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.ornithemc.nester.nest.NesterIo;
import net.ornithemc.nester.nest.Nests;

public abstract class Nest {

	public abstract String getName();

	/**
	 * @return whether nests of this flavour exist for the given minecraft version.
	 */
	public abstract boolean doNestsExist(OrderedVersion mcVersion);

	/**
	 * @return whether nests of this flavour exist for the given jar for the given minecraft version.
	 */
	public abstract boolean doNestsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * @return whether nests of this flavour can be used on the given jar for the given minecraft version.
	 */
	public abstract boolean canNestsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour);

	/**
	 * After calling this method, a path returned by {@link #getNestsPath(OrderedVersion, MinecraftJar, MappingFlavour)} should be valid.
	 * This is only true if {@link #doNestsExist(OrderedVersion, MinecraftJar)} returns true for the version.
	 *
	 * @param versionContext Version Context
	 * @param minecraftJar Minecraft jar
	 * @param mappingFlavour Mapping flavour
	 * @return A result
	 */
	public abstract StepStatus provideNests(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) throws IOException, URISyntaxException, InterruptedException;

	protected final StepStatus mapNests(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour, Path srcPath, Path dstPath) {
		try {
			if (mappingFlavour.canBeUsedOn(mcVersion, minecraftJar)) {
				MemoryMappingTree mappings = new MemoryMappingTree();
				mappingFlavour.visit(mcVersion, minecraftJar, mappings);

				return NestsMapper.mapNests(srcPath, dstPath, mappings, mappingFlavour.getDestinationNS());
			} else {
				Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
				return StepStatus.SUCCESS;
			}
		} catch (IOException e) {
			return StepStatus.FAILED;
		}
	}

	/**
	 * Should return a path to a nests file, created by {@link #provideNests(IStepContext, MinecraftJar, MappingFlavour)}
	 *
	 * @param mcVersion Version
	 * @param minecraftJar Minecraft jar
	 * @param mappingFlavour Mapping flavour
	 * @return path to nests file
	 */
	public final Optional<Path> getNestsPath(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		return Optional.ofNullable(getNestsPathInternal(mcVersion, minecraftJar, mappingFlavour));
	}

	protected abstract Path getNestsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour);

	/**
	 * Visits nests of this flavour for the given jar for the given minecraft version, using the given visitor.
	 * The visitor will only be called if {@link #canNestsBeUsedOn(OrderedVersion, MinecraftJar, MappingFlavour)} returns true for the version.
	 */
	public abstract void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour, Nests visitor) throws IOException;

	public final Nests getNests(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		if (!canNestsBeUsedOn(mcVersion, minecraftJar, mappingFlavour)) {
			MiscHelper.panic("Tried to use %s-nests for version %s, %s jar. These nests can not be used for this version.", this, mcVersion.launcherFriendlyVersionName(), minecraftJar.name().toLowerCase());
		}
		Nests nests = Nests.empty();
		try {
			visit(mcVersion, minecraftJar, mappingFlavour, nests);
		} catch (IOException e) {
			MiscHelper.panicBecause(e, "An error occurred while getting nests information for %s (version %s)", this, mcVersion.launcherFriendlyVersionName());
		}
		return nests;
	}

	protected static boolean validateNests(Path nestsPath) {
		try {
			NesterIo.read(Nests.empty(), nestsPath);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
