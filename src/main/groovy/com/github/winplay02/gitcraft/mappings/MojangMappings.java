package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.GitCraft;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MojangMappings extends Mapping {
	@Override
	public String getName() {
		return "Mojang Mappings";
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.NAMED.toString();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return mcVersion.hasFullMojMaps();
	}

	@Override
	public Step.StepResult prepareMappings(OrderedVersion mcVersion) throws IOException {
		Path mojmapPath = getMappingsPathInternal(mcVersion);
		Path artifactTargetPath = GitCraft.MC_VERSION_STORE.resolve(mcVersion.launcherFriendlyVersionName());
		if (Files.exists(mojmapPath)) {
			return Step.StepResult.UP_TO_DATE;
		}
		Step.StepResult clientMappings = null;
		Step.StepResult serverMappings = null;
		if (mcVersion.hasClientMojMaps()) {
			clientMappings = mcVersion.clientMappings().fetchArtifact(artifactTargetPath, "client mojmaps");
		}
		if (mcVersion.hasServerMojMaps()) {
			serverMappings = mcVersion.serverMappings().fetchArtifact(artifactTargetPath, "server mojmaps");
		}
		MemoryMappingTree mappingTree = new MemoryMappingTree();

		// Make official the source namespace
		MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.OFFICIAL.toString());

		try (BufferedReader clientBufferedReader = Files.newBufferedReader(mcVersion.clientMappings().resolve(artifactTargetPath), StandardCharsets.UTF_8); BufferedReader serverBufferedReader = Files.newBufferedReader(mcVersion.serverMappings().resolve(artifactTargetPath), StandardCharsets.UTF_8)) {
			ProGuardFileReader.read(clientBufferedReader, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
			ProGuardFileReader.read(serverBufferedReader, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
		}
		try (MappingWriter w = MappingWriter.create(mojmapPath, MappingFormat.TINY_2_FILE)) {
			mappingTree.accept(w);
		}
		return Step.StepResult.merge(clientMappings, serverMappings, Step.StepResult.SUCCESS);
	}

	@Override
	public Path getMappingsPathInternal(OrderedVersion mcVersion) {
		return GitCraft.MAPPINGS.resolve(mcVersion.launcherFriendlyVersionName() + "-moj.tiny");
	}
}
