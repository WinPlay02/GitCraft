package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.GitCraftQuirks;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentTreeV1;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class ParchmentMappings extends Mapping {

	protected MojangMappings mojangMappings;

	public ParchmentMappings(MojangMappings mojangMappings) {
		this.mojangMappings = mojangMappings;
	}

	@Override
	public String getName() {
		return "Parchment";
	}

	@Override
	public boolean supportsComments() {
		return true;
	}

	@Override
	public boolean needsPackageFixingForLaunch() {
		return false;
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.NAMED.toString();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		if (!mojangMappings.doMappingsExist(mcVersion)) {
			return false;
		}
		if (GitCraftQuirks.parchmentMissingVersions.contains(mcVersion.launcherFriendlyVersionName()) || mcVersion.isSnapshotOrPending()) {
			return false;
		}
		return mcVersion.compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(GitCraftQuirks.PARCHMENT_START_VERSION_ID)) >= 0;
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		// parchment is provided for the merged jar
		return minecraftJar == MinecraftJar.MERGED && mojangMappings.doMappingsExist(mcVersion, minecraftJar) && doMappingsExist(mcVersion);
	}

	@Override
	public boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		// the merged mappings can be used for all jars
		return doMappingsExist(mcVersion);
	}

	@Override
	public StepStatus provideMappings(StepWorker.Context<OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		// parchment is provided for the merged jar
		if (minecraftJar != MinecraftJar.MERGED) {
			return StepStatus.NOT_RUN;
		}
		Path mappingsPath = getMappingsPathInternal(versionContext.targetVersion(), null);
		if (Files.exists(mappingsPath) && validateMappings(mappingsPath)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsPath);
		String lastestParchmentBuild = getLatestReleaseVersionParchmentBuild(versionContext.targetVersion());
		Path parchmentJson = PipelineFilesystemStorage.DEFAULT.get().rootFilesystem().getMappings().resolve(String.format("%s-parchment-%s-%s.json", versionContext.targetVersion().launcherFriendlyVersionName(), versionContext.targetVersion().launcherFriendlyVersionName(), lastestParchmentBuild));
		StepStatus downloadStatus = null;
		if (!Files.exists(parchmentJson)) {
			Path parchmentJar = PipelineFilesystemStorage.DEFAULT.get().rootFilesystem().getMappings().resolve(String.format("%s-parchment-%s-%s.jar", versionContext.targetVersion().launcherFriendlyVersionName(), versionContext.targetVersion().launcherFriendlyVersionName(), lastestParchmentBuild));
			downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(
					versionContext.executorService(),
					String.format("https://maven.parchmentmc.org/org/parchmentmc/data/parchment-%s/%s/parchment-%s-%s.zip", versionContext.targetVersion().launcherFriendlyVersionName(), lastestParchmentBuild, versionContext.targetVersion().launcherFriendlyVersionName(), lastestParchmentBuild),
					new FileSystemNetworkManager.LocalFileInfo(parchmentJar,
							null,
							null,
							"parchment mapping",
							versionContext.targetVersion().launcherFriendlyVersionName())
			);
			try (FileSystem fs = FileSystems.newFileSystem(parchmentJar)) {
				Path mappingsPathInJar = fs.getPath("parchment.json");
				Files.copy(mappingsPathInJar, parchmentJson, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				Files.deleteIfExists(parchmentJar);
				throw new IOException("Parchment mappings are invalid", e);
			}
		}
		// parchment requires mojmaps, which is provided separately for client and server jars
		StepStatus mojmapsClientStatus = mojangMappings.provideMappings(versionContext, MinecraftJar.CLIENT);
		StepStatus mojmapsServerStatus = mojangMappings.provideMappings(versionContext, MinecraftJar.SERVER);
		MemoryMappingTree mappings = new MemoryMappingTree();
		mojangMappings.visit(versionContext.targetVersion(), minecraftJar, new MappingSourceNsSwitch(mappings, MappingsNamespace.NAMED.toString()));
		ParchmentTreeV1 parchmentTreeV1 = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(parchmentJson), ParchmentTreeV1.class);
		parchmentTreeV1.visit(mappings, MappingsNamespace.NAMED.toString());
		try (MappingWriter writer = MappingWriter.create(mappingsPath, MappingFormat.TINY_2_FILE)) {
			mappings.accept(new MappingSourceNsSwitch(writer, MappingsNamespace.OFFICIAL.toString()));
		}
		return StepStatus.merge(downloadStatus, mojmapsClientStatus, mojmapsServerStatus, StepStatus.SUCCESS);
	}

	@Override
	public Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return PipelineFilesystemStorage.DEFAULT.get().rootFilesystem().getMappings().resolve(String.format("%s-parchment-%s-%s.tiny", mcVersion.launcherFriendlyVersionName(), mcVersion.launcherFriendlyVersionName(), getLatestReleaseVersionParchmentBuild(mcVersion)));
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		Path path = getMappingsPathInternal(mcVersion, MinecraftJar.MERGED);
		try (BufferedReader br = Files.newBufferedReader(path)) {
			Tiny2FileReader.read(br, visitor);
		}
	}

	private final Map<String, String> parchmentBuilds = new HashMap<>();

	private String getLatestReleaseVersionParchmentBuild(OrderedVersion mcVersion) {
		if (!parchmentBuilds.containsKey(mcVersion.launcherFriendlyVersionName())) {
			try {
				parchmentBuilds.put(mcVersion.launcherFriendlyVersionName(), RemoteHelper.readMavenLatestRelease(String.format("https://maven.parchmentmc.org/org/parchmentmc/data/parchment-%s/maven-metadata.xml", mcVersion.launcherFriendlyVersionName())));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return parchmentBuilds.get(mcVersion.launcherFriendlyVersionName());
	}
}
