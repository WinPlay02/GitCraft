package com.github.winplay02.gitcraft.mappings.ornithe;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MetaVersionsSource;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class FeatherMappings extends Mapping {
	private final int generation;
	private final MetaVersionsSource<GameVersionBuildMeta> featherVersions;

	public FeatherMappings() {
		if (GitCraft.config.ornitheIntermediaryGeneration < 1) {
			throw new IllegalArgumentException("ornithe intermediary generation cannot be less than 1");
		}

		this.generation = GitCraft.config.ornitheIntermediaryGeneration;
		this.featherVersions = new MetaVersionsSource<>(
			"https://meta.ornithemc.net/v3/versions/gen" + this.generation + "/feather",
			SerializationHelper.TYPE_LIST_GAME_VERSION_BUILD_META,
			GameVersionBuildMeta::gameVersion
		);
	}

	private String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return generation == 1
			? mcVersion.launcherFriendlyVersionName() + (minecraftJar == MinecraftJar.MERGED ? "" : "-" + minecraftJar.name().toLowerCase())
			: mcVersion.launcherFriendlyVersionName();
	}

	private GameVersionBuildMeta getLatestFeatherVersion(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		return featherVersions.getLatest(versionKey(mcVersion, minecraftJar));
	}

	@Override
	public String getName() {
		return "Feather Gen " + generation;
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.NAMED.toString();
	}

	@Override
	public boolean supportsMergingPre1_3Versions() {
		return generation > 1;
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return doMappingsExist(mcVersion, MinecraftJar.MERGED) || doMappingsExist(mcVersion, MinecraftJar.CLIENT) || doMappingsExist(mcVersion, MinecraftJar.SERVER);
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			return getLatestFeatherVersion(mcVersion, minecraftJar) != null;
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return (generation > 1 || mcVersion.hasSharedObfuscation())
			? doMappingsExist(mcVersion, MinecraftJar.MERGED)
			: doMappingsExist(mcVersion, minecraftJar);
	}

	@Override
	public StepStatus provideMappings(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		GameVersionBuildMeta featherVersion = getLatestFeatherVersion(mcVersion, minecraftJar);
		if (featherVersion == null) {
			return StepStatus.NOT_RUN;
		}
		Path mappingsFile = getMappingsPathInternal(mcVersion, minecraftJar);
		if (Files.exists(mappingsFile) && validateMappings(mappingsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsFile);
		Path mappingsJarFile = getMappingsJarPath(mcVersion, minecraftJar);
		StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(featherVersion.makeMavenJarMergedV2Url(GitCraft.ORNITHE_MAVEN), new RemoteHelper.LocalFileInfo(mappingsJarFile, null, "feather gen " + generation + " mapping", mcVersion.launcherFriendlyVersionName()));
		try (FileSystem fs = FileSystems.newFileSystem(mappingsJarFile)) {
			Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
			Files.copy(mappingsPathInJar, mappingsFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta featherVersion = getLatestFeatherVersion(mcVersion, minecraftJar);
			if (featherVersion == null) {
				return null;
			}
			return GitCraftPaths.MAPPINGS.resolve(versionKey(mcVersion, minecraftJar) + "-feather-gen" + generation + "-build." + featherVersion.build() + ".tiny");
		} catch (IOException e) {
			return null;
		}
	}

	private Path getMappingsJarPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta featherVersion = getLatestFeatherVersion(mcVersion, minecraftJar);
			if (featherVersion == null) {
				return null;
			}
			return GitCraftPaths.MAPPINGS.resolve(versionKey(mcVersion, minecraftJar) + "-feather-gen" + generation + "-build." + featherVersion.build() + ".jar");
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		if (generation > 1 && mcVersion.hasSharedVersioning() && !mcVersion.hasSharedObfuscation()) {
			// make sure the src ns matches the return value of Mapping.getSourceNS
			MappingsNamespace officialNs = (minecraftJar == MinecraftJar.CLIENT)
				? MappingsNamespace.CLIENT_OFFICIAL
				: MappingsNamespace.SERVER_OFFICIAL;
			visitor = new MappingSourceNsSwitch(visitor, MappingsNamespace.OFFICIAL.toString(), true);
			visitor = new MappingNsRenamer(visitor, Map.of(officialNs.toString(), MappingsNamespace.OFFICIAL.toString()));
		}
		if (generation > 1 || mcVersion.hasSharedObfuscation()) {
			// merged mappings can be used on any jar for
			// all versions in gen2 or 1.3+ versions in gen1
			minecraftJar = MinecraftJar.MERGED;
		}
		Path path = getMappingsPathInternal(mcVersion, minecraftJar);
		try (BufferedReader br = Files.newBufferedReader(path)) {
			Tiny2FileReader.read(br, visitor);
		}
	}
}
