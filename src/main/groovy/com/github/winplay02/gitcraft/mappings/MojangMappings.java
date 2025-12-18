package com.github.winplay02.gitcraft.mappings;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

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
	public boolean needsPackageFixingForLaunch() {
		return false;
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return mcVersion.isUnobfuscated() || mcVersion.hasClientMojMaps() || mcVersion.hasServerMojMaps();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return (minecraftJar == MinecraftJar.CLIENT && mcVersion.hasClientMojMaps()) || (minecraftJar == MinecraftJar.SERVER && mcVersion.hasServerMojMaps());
	}

	@Override
	public boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		// mappings are provided separately for the client and server jars,
		// but those mappings can be combined for the merged jar
		return (minecraftJar == MinecraftJar.CLIENT && mcVersion.hasClientMojMaps())
				|| (minecraftJar == MinecraftJar.SERVER && mcVersion.hasServerMojMaps())
				|| (minecraftJar == MinecraftJar.MERGED && mcVersion.hasFullMojMaps());
	}

	@Override
	public StepStatus provideMappings(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		// client and server mappings are provided separately
		if (minecraftJar == MinecraftJar.MERGED) {
			return StepStatus.NOT_RUN;
		}
		Path artifactTargetPath = GitCraftPipelineFilesystemRoot.getMcVersionStore().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(versionContext.targetVersion().launcherFriendlyVersionName());
		Path mappingsPath = getMappingsPathInternal(versionContext.targetVersion(), minecraftJar);
		Artifact artifact = null;
		if (minecraftJar == MinecraftJar.CLIENT)
			artifact = versionContext.targetVersion().clientMappings();
		if (minecraftJar == MinecraftJar.SERVER)
			artifact = versionContext.targetVersion().serverMappings();
		if (artifact == null)
			MiscHelper.panic("no mojang mappings artifact for %s jar", minecraftJar.name().toLowerCase());
		return provideMappings(versionContext.executorService(), artifactTargetPath, artifact, mappingsPath);
	}

	private StepStatus provideMappings(Executor executor, Path artifactTargetPath, Artifact artifact, Path mappingsPath) throws IOException {
		if (artifact == null) {
			return StepStatus.NOT_RUN;
		}
		if (Files.exists(mappingsPath) && validateMappings(mappingsPath)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsPath);
		StepStatus status = artifact.fetchArtifact(executor, artifactTargetPath);
		Path artifactPath = artifact.resolve(artifactTargetPath);
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		// Make official the source namespace
		MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.OFFICIAL.toString());
		try (BufferedReader br = Files.newBufferedReader(artifactPath, StandardCharsets.UTF_8)) {
			ProGuardFileReader.read(br, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
		}
		if (status.hasRun()) {
			try (MappingWriter w = MappingWriter.create(mappingsPath, MappingFormat.TINY_2_FILE)) {
				mappingTree.accept(w);
			}
			status = StepStatus.merge(status, StepStatus.SUCCESS);
		}
		return status;
	}

	@Override
	public Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve("%s-%s-moj.tiny".formatted(mcVersion.launcherFriendlyVersionName(), minecraftJar.name().toLowerCase()));
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		switch (minecraftJar) {
			case CLIENT, SERVER -> {
				Path path = getMappingsPathInternal(mcVersion, minecraftJar);
				try (BufferedReader br = Files.newBufferedReader(path)) {
					Tiny2FileReader.read(br, visitor);
				}
			}
			case MERGED -> {
				visit(mcVersion, MinecraftJar.CLIENT, visitor);
				visit(mcVersion, MinecraftJar.SERVER, visitor);
			}
		}
	}
}
