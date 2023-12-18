package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public abstract class Mapping {
	public static final String KEY_UNPICK_DEFINITIONS = "unpick_definitions";
	public static final String KEY_UNPICK_CONSTANTS = "unpick_constants";

	public abstract String getName();

	public boolean supportsComments() {
		return false;
	}

	public boolean supportsConstantUnpicking() {
		return false;
	}

	public String getSourceNS() {
		return MappingsNamespace.OFFICIAL.toString();
	}

	public abstract String getDestinationNS();

	public abstract boolean doMappingsExist(OrderedVersion mcVersion);

	/**
	 * After calling this method, a path returned by {@link #getMappingsPath(OrderedVersion)} should be valid.
	 * This is only true if {@link #doMappingsExist(OrderedVersion)} returns true for the version.
	 *
	 * @param mcVersion Version
	 * @return A result
	 */
	public abstract Step.StepResult prepareMappings(OrderedVersion mcVersion) throws IOException;

	/**
	 * Should return a path to a tinyv2 mappings file, created by {@link #prepareMappings(OrderedVersion)}
	 *
	 * @param mcVersion Version
	 * @return path to tinyv2 mappings file
	 */
	public final Optional<Path> getMappingsPath(OrderedVersion mcVersion) {
		return Optional.ofNullable(getMappingsPathInternal(mcVersion));
	}

	/**
	 * Should return a path to further information that may be additionally contained in a mappings-distribution.
	 * These values will be populated by calling {@link #prepareMappings(OrderedVersion)} first.
	 * Examples for additional files are unpick definitions for yarn.
	 *
	 * @param mcVersion Version
	 * @return Map containing paths to additional files
	 */
	public Map<String, Path> getAdditionalMappingInformation(OrderedVersion mcVersion) {
		return Collections.emptyMap();
	}

	protected abstract Path getMappingsPathInternal(OrderedVersion mcVersion);

	public final IMappingProvider getMappingsProvider(OrderedVersion mcVersion) {
		if (!doMappingsExist(mcVersion)) {
			MiscHelper.panic("Tried to use %s-mappings for version %s. These mappings do not exist for this version.", this, mcVersion.launcherFriendlyVersionName());
		}
		Optional<Path> mappingsPath = getMappingsPath(mcVersion);
		if (mappingsPath.isEmpty() || !Files.exists(mappingsPath.orElseThrow())) {
			MiscHelper.panic("An error occurred while getting mapping information for %s (version %s)", this, mcVersion.launcherFriendlyVersionName());
		}
		return TinyUtils.createTinyMappingProvider(mappingsPath.get(), getSourceNS(), getDestinationNS());
	}

	protected static boolean validateMappings(Path mappingsTinyPath) {
		try {
			MappingReader.read(mappingsTinyPath, new MemoryMappingTree());
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
