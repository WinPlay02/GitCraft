package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.github.winplay02.gitcraft.GitCraft;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.parchment.ParchmentTreeV1;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
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
	public String getDestinationNS() {
		return MappingsNamespace.NAMED.toString();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return mcVersion.hasFullMojMaps() && !mcVersion.isSnapshotOrPending() && !GitCraftConfig.parchmentMissingVersions.contains(mcVersion.launcherFriendlyVersionName()) && mcVersion.compareTo(GitCraftConfig.PARCHMENT_START_VERSION) >= 0;
	}

	@Override
	public Step.StepResult prepareMappings(OrderedVersion mcVersion) throws IOException {
		Path mappingsPath = getMappingsPathInternal(mcVersion);
		if (Files.exists(mappingsPath)) {
			return Step.StepResult.UP_TO_DATE;
		}
		String parchmentLatestReleaseVersionBuild = getLatestReleaseVersionParchmentBuild(mcVersion);
		Path mappingsFileJson = GitCraft.MAPPINGS.resolve(String.format("%s-parchment-%s-%s.json", mcVersion.launcherFriendlyVersionName(), mcVersion.launcherFriendlyVersionName(), parchmentLatestReleaseVersionBuild));
		Step.StepResult downloadResult = null;
		if (!Files.exists(mappingsFileJson)) {
			Path mappingsFileJar = GitCraft.MAPPINGS.resolve(String.format("%s-parchment-%s-%s.jar", mcVersion.launcherFriendlyVersionName(), mcVersion.launcherFriendlyVersionName(), parchmentLatestReleaseVersionBuild));
			downloadResult = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(
					String.format("https://maven.parchmentmc.org/org/parchmentmc/data/parchment-%s/%s/parchment-%s-%s.zip", mcVersion.launcherFriendlyVersionName(), parchmentLatestReleaseVersionBuild, mcVersion.launcherFriendlyVersionName(), parchmentLatestReleaseVersionBuild),
					new RemoteHelper.LocalFileInfo(mappingsFileJar,
							null,
							"parchment mapping",
							mcVersion.launcherFriendlyVersionName())
			);
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mappingsFileJar)) {
				Path mappingsPathInJar = fs.get().getPath("parchment.json");
				Files.copy(mappingsPathInJar, mappingsFileJson, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				Files.deleteIfExists(mappingsFileJar);
				throw new IOException("Parchment mappings are invalid", e);
			}
		}
		Step.StepResult mojmapResult = mojangMappings.prepareMappings(mcVersion);
		Path mappingsFileMojmap = mojangMappings.getMappingsPathInternal(mcVersion);

		ParchmentTreeV1 parchmentTreeV1 = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(mappingsFileJson), ParchmentTreeV1.class);
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		{
			MappingSourceNsSwitch nsSwitchIntermediary = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.NAMED.toString());
			MappingReader.read(mappingsFileMojmap, nsSwitchIntermediary);
		}
		try (MappingWriter writer = MappingWriter.create(mappingsPath, MappingFormat.TINY_2_FILE)) {
			parchmentTreeV1.visit(mappingTree, MappingsNamespace.NAMED.toString());
			MappingSourceNsSwitch sourceNsSwitch = new MappingSourceNsSwitch(writer, MappingsNamespace.OFFICIAL.toString());
			mappingTree.accept(sourceNsSwitch);
		}
		return Step.StepResult.merge(downloadResult, mojmapResult, Step.StepResult.SUCCESS);
	}

	@Override
	public Path getMappingsPathInternal(OrderedVersion mcVersion) {
		return GitCraft.MAPPINGS.resolve(String.format("%s-parchment-%s-%s.tiny", mcVersion.launcherFriendlyVersionName(), mcVersion.launcherFriendlyVersionName(), getLatestReleaseVersionParchmentBuild(mcVersion)));
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
