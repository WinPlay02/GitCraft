package com.github.winplay02;

import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.data.McVersion;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.ProGuardReader;
import net.fabricmc.mappingio.format.Tiny1Reader;
import net.fabricmc.mappingio.format.Tiny2Reader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MappingHelper {

	public static final SemanticVersion INTERMEDIARY_MAPPINGS_START_VERSION;

	static {
		try {
			INTERMEDIARY_MAPPINGS_START_VERSION = SemanticVersion.parse("1.14");
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}

	public static MemoryMappingTree createMojMapMappingsProvider(McVersion mcVersion) throws IOException {
		if (!mcVersion.hasMappings) {
			MiscHelper.panic("Tried to use mojmaps for version %s. This version does not contain mojmaps.", mcVersion.version);
		}
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		MappingSourceNsSwitch nsSwitchMojMap = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.OFFICIAL.toString());
		try (BufferedReader mojMappingsReader = Files.newBufferedReader(mappingsPathMojMap(mcVersion), StandardCharsets.UTF_8)) {
			Tiny2Reader.read(mojMappingsReader, nsSwitchMojMap);
		}
		//mappingTree.accept(nsSwitchMojMap);
		Path intermediaryPath = mappingsPathIntermediary(mcVersion);
		if (intermediaryPath != null) {
			MappingSourceNsSwitch nsSwitchIntermediary = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.OFFICIAL.toString());
			try (BufferedReader intermediaryMappingsReader = Files.newBufferedReader(intermediaryPath, StandardCharsets.UTF_8)) {
				Tiny1Reader.read(intermediaryMappingsReader, nsSwitchIntermediary);
			}
			//mappingTree.accept(nsSwitchIntermediary);
		} else {
			if (GitCraft.config.loomFixRecords) {
				MiscHelper.panic("Loom should be used to fix invalid records, but intermediary mappings are not (yet) available");
			}
		}
		return mappingTree;
	}

	private static Path mappingsPathIntermediary(McVersion mcVersion) {
		try {
			if (SemanticVersion.parse(mcVersion.loaderVersion).compareTo((Version) INTERMEDIARY_MAPPINGS_START_VERSION) < 0) {
				return null;
			}
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
		Path mappingsFile = GitCraft.MAPPINGS.resolve(mcVersion.version + "-intermediary.tiny");
		if (!mappingsFile.toFile().exists()) {
			RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetry(String.format("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/%s.tiny", mcVersion.version), mappingsFile, null, "intermediary mapping", mcVersion.version);
		}
		return mappingsFile;
	}

	private static Path mappingsPathMojMap(McVersion mcVersion) throws IOException {
		Path mappingsFile = GitCraft.MAPPINGS.resolve(mcVersion.version + "-moj.tiny");

		if (!mappingsFile.toFile().exists()) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();

			// Make official the source namespace
			MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.OFFICIAL.toString());

			try (BufferedReader clientBufferedReader = Files.newBufferedReader(mcVersion.artifacts.clientMappings().fetchArtifact().toPath(), StandardCharsets.UTF_8); BufferedReader serverBufferedReader = Files.newBufferedReader(mcVersion.artifacts.serverMappings().fetchArtifact().toPath(), StandardCharsets.UTF_8)) {
				ProGuardReader.read((Reader) clientBufferedReader, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
				ProGuardReader.read((Reader) serverBufferedReader, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
			}
			try (MappingWriter w = MappingWriter.create(mappingsFile, MappingFormat.TINY_2)) {
				mappingTree.accept(w);
			}
		}

		return mappingsFile;
	}
}
