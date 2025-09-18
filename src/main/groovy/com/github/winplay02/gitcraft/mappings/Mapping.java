package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public abstract class Mapping {

	public abstract String getName();

	public boolean supportsComments() {
		return false;
	}

	public boolean needsPackageFixingForLaunch() {
		return true;
	}

	public boolean supportsConstantUnpicking() {
		return false;
	}

	/**
	 * Whether this mapping implementation supports merging pre-1.3 versions.
	 * These versions have different obfuscation for the client and server jars,
	 * thus the obfuscated jars cannot be merged. Some mapping sets are specifically
	 * designed such that the remapped jars <i>can</i> be merged.
	 *
	 * @return True by default as mappings must be specifically designed to support merging pre-1.3 versions, otherwise false
	 */
	public boolean supportsMergingPre1_3Versions() {
		return false;
	}

	/**
	 * Whether this mapping implementation requires mapping files.
	 * This should be overridden if no mapping files are needed.
	 * If this is the case, a custom remapping implementation should be provided by overriding {@link #executeCustomRemappingLogic(Path, OrderedVersion, MinecraftJar)}.
	 *
	 * @return True by default as mapping files are required, otherwise false
	 */
	public boolean isMappingFileRequired() {
		return true;
	}

	public String getSourceNS() {
		return MappingsNamespace.OFFICIAL.toString();
	}

	public abstract String getDestinationNS();

	/**
	 * @return whether mappings of this flavour exist for the given minecraft version.
	 */
	public abstract boolean doMappingsExist(OrderedVersion mcVersion);

	/**
	 * @return whether mappings of this flavour exist for the given jar for the given minecraft version.
	 */
	public abstract boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * @return whether mappings of this flavour can be used on the given jar for the given minecraft version.
	 */
	public abstract boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * After calling this method, a path returned by {@link #getMappingsPath(OrderedVersion, MinecraftJar)} should be valid.
	 * This is only true if {@link #doMappingsExist(OrderedVersion, MinecraftJar)} returns true for the version.
	 *
	 * @param versionContext Version Context
	 * @param minecraftJar Minecraft jar
	 * @return A result
	 */
	public abstract StepStatus provideMappings(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException;

	/**
	 * Should return a path to a tinyv2 mappings file, created by {@link #provideMappings(IStepContext, MinecraftJar)}
	 *
	 * @param mcVersion Version
	 * @param minecraftJar Minecraft jar
	 * @return path to tinyv2 mappings file
	 */
	public final Optional<Path> getMappingsPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return Optional.ofNullable(getMappingsPathInternal(mcVersion, minecraftJar));
	}

	/**
	 * Should return a path to further information that may be additionally contained in a mappings-distribution.
	 * These values will be populated by calling {@link #provideMappings(IStepContext, MinecraftJar)} first.
	 * Examples for additional files are unpick definitions for yarn.
	 *
	 * @param mcVersion Version
	 * @param minecraftJar Minecraft jar
	 * @return Map containing paths to additional files
	 */
	public Map<String, Path> getAdditionalMappingInformation(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return Collections.emptyMap();
	}

	protected abstract Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar);

	/**
	 * Visits mappings of this flavour for the given jar for the given minecraft version, using the given visitor.
	 * The visitor will only be called if {@link #canMappingsBeUsedOn(OrderedVersion, MinecraftJar)} returns true for the version.
	 */
	public abstract void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException;

	public final IMappingProvider getMappingsProvider(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		VisitableMappingTree mappings = MappingUtils.createTreeFromMappingFlavour(this, mcVersion, minecraftJar);
		return TinyUtils.createMappingProvider(mappings, getSourceNS(), getDestinationNS());
	}

	/**
	 * A custom remapping logic, that is executed if no mapping file exists.
	 *
	 * @param previousFile Either a merged file (if exists) or an unmerged jar (either client or server side).
	 * @param mcVersion    Version that the provided jar belongs to.
	 * @param minecraftJar Minecraft jar the mappings will be used on
	 * @return Path that contains the remapped jar.
	 */
	public Path executeCustomRemappingLogic(Path previousFile, OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return null;
	}

	/**
	 * Delete mappings of a specific version, if they exist. This forces the implementation to download the mappings again in the future.
	 * If the mappings have been updated in the meantime, newer versions will be used.
	 *
	 * @param mcVersion Version
	 */
	// public abstract void resetExistingMappings(OrderedVersion mcVersion);

	protected static boolean validateMappings(Path mappingsTinyPath) {
		try {
			MappingReader.read(mappingsTinyPath, new MemoryMappingTree());
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
