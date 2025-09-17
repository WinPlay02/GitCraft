package com.github.winplay02.gitcraft.mappings.yarn;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.GitCraftQuirks;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.tiny.Tiny1FileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
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
	public boolean needsPackageFixingForLaunch() {
		return false;
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		if (GitCraftQuirks.intermediaryMissingVersions.contains(mcVersion.launcherFriendlyVersionName())) {
			return false;
		}
		return mcVersion.compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(GitCraftQuirks.FABRIC_INTERMEDIARY_MAPPINGS_START_VERSION_ID)) >= 0;
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		// fabric intermediary is provided for the merged jar
		return minecraftJar == MinecraftJar.MERGED && doMappingsExist(mcVersion);
	}

	@Override
	public boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		// the merged mappings can be used for all jars
		return doMappingsExist(mcVersion);
	}

	protected static String mappingsIntermediaryPathQuirkVersion(String version) {
		return GitCraftQuirks.yarnInconsistentVersionNaming.getOrDefault(version, version);
	}

	@Override
	public StepStatus provideMappings(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		// fabric intermediary is provided for the merged jar
		if (minecraftJar != MinecraftJar.MERGED) {
			return StepStatus.NOT_RUN;
		}
		Path mappingsFile = getMappingsPathInternal(versionContext.targetVersion(), minecraftJar);
		if (Files.exists(mappingsFile) && validateMappings(mappingsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsFile);
		Path mappingsV1 = getMappingsPathInternalV1(versionContext.targetVersion());
		StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryGitHub(versionContext.executorService(), "FabricMC/intermediary", "master", String.format("mappings/%s.tiny", mappingsIntermediaryPathQuirkVersion(versionContext.targetVersion().launcherFriendlyVersionName())), new FileSystemNetworkManager.LocalFileInfo(mappingsV1, null, null,"intermediary mapping", versionContext.targetVersion().launcherFriendlyVersionName()));
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		try (BufferedReader br = Files.newBufferedReader(mappingsV1, StandardCharsets.UTF_8)) {
			Tiny1FileReader.read(br, mappingTree);
		}
		try (MappingWriter writer = MappingWriter.create(mappingsFile, MappingFormat.TINY_2_FILE)) {
			mappingTree.accept(writer);
		}
		return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
	}

	protected Path getMappingsPathInternalV1(OrderedVersion mcVersion) {
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(mcVersion.launcherFriendlyVersionName() + "-intermediary-v1.tiny");
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(mcVersion.launcherFriendlyVersionName() + "-intermediary.tiny");
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		Path path = getMappingsPathInternal(mcVersion, MinecraftJar.MERGED);
		try (BufferedReader br = Files.newBufferedReader(path)) {
			Tiny2FileReader.read(br, visitor);
		}
	}
}
