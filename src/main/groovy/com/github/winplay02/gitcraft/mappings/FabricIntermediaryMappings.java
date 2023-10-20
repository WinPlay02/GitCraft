package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.GitCraft;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.tiny.Tiny1FileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FabricIntermediaryMappings extends Mapping {
	@Override
	public String getName() {
		return "Fabric Intermediary";
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.INTERMEDIARY.toString();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return mcVersion.compareTo(GitCraftConfig.INTERMEDIARY_MAPPINGS_START_VERSION) >= 0;
	}

	protected static String mappingsIntermediaryPathQuirkVersion(String version) {
		return GitCraftConfig.yarnInconsistentVersionNaming.getOrDefault(version, version);
	}

	@Override
	public Step.StepResult prepareMappings(OrderedVersion mcVersion) throws IOException {
		Path mappingsFile = getMappingsPathInternal(mcVersion);
		if (Files.exists(mappingsFile)) {
			return Step.StepResult.UP_TO_DATE;
		}
		Path mappingsV1 = getMappingsPathInternalV1(mcVersion);
		Step.StepResult downloadResult = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryGitHub("FabricMC/intermediary", "master", String.format("mappings/%s.tiny", mappingsIntermediaryPathQuirkVersion(mcVersion.launcherFriendlyVersionName())), new RemoteHelper.LocalFileInfo(mappingsV1, null, "intermediary mapping", mcVersion.launcherFriendlyVersionName()));
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		try (BufferedReader clientBufferedReader = Files.newBufferedReader(mappingsV1, StandardCharsets.UTF_8)) {
			Tiny1FileReader.read(clientBufferedReader, mappingTree);
		}
		try (MappingWriter w = MappingWriter.create(mappingsFile, MappingFormat.TINY_2_FILE)) {
			mappingTree.accept(w);
		}
		return Step.StepResult.merge(downloadResult, Step.StepResult.SUCCESS);
	}

	protected Path getMappingsPathInternalV1(OrderedVersion mcVersion) {
		return GitCraft.MAPPINGS.resolve(mcVersion.launcherFriendlyVersionName() + "-intermediary-v1.tiny");
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion) {
		return GitCraft.MAPPINGS.resolve(mcVersion.launcherFriendlyVersionName() + "-intermediary.tiny");
	}
}
