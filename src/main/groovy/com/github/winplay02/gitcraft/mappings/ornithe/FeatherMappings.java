package com.github.winplay02.gitcraft.mappings.ornithe;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.meta.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.meta.MetaUrls;
import com.github.winplay02.gitcraft.meta.RemoteVersionMetaSource;
import com.github.winplay02.gitcraft.meta.VersionMetaSource;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.RemoteHelper;

import com.github.winplay02.gitcraft.util.SerializationTypes;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class FeatherMappings extends Mapping {
	private final int generation;
	private static final Map<Integer, VersionMetaSource<GameVersionBuildMeta>> FEATHER_VERSIONS = new HashMap<>();

	public static VersionMetaSource<GameVersionBuildMeta> featherVersions(int generation) {
		return FEATHER_VERSIONS.computeIfAbsent(generation, gen -> new RemoteVersionMetaSource<>(
			MetaUrls.ornitheFeather(gen),
			SerializationTypes.TYPE_LIST_GAME_VERSION_BUILD_META,
			GameVersionBuildMeta::gameVersion
		));
	}

	private VersionMetaSource<GameVersionBuildMeta> featherVersions() {
		return featherVersions(this.generation);
	}

	public FeatherMappings() {
		if (GitCraft.getApplicationConfiguration().ornitheIntermediaryGeneration() < 1) {
			throw new IllegalArgumentException("ornithe intermediary generation cannot be less than 1");
		}

		this.generation = GitCraft.getApplicationConfiguration().ornitheIntermediaryGeneration();
		this.featherVersions();
	}

	public static String versionKey(int generation, OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return generation == 1
			? mcVersion.launcherFriendlyVersionName() + (minecraftJar == MinecraftJar.MERGED ? "" : "-" + minecraftJar.name().toLowerCase())
			: mcVersion.launcherFriendlyVersionName();
	}

	public static GameVersionBuildMeta getLatestFeatherVersion(int generation, OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		try {
			return featherVersions(generation).getLatest(versionKey(generation, mcVersion, minecraftJar));
		} catch (URISyntaxException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getName() {
		return "Feather Gen " + generation;
	}

	@Override
	public boolean supportsComments() {
		return true;
	}

	@Override
	public boolean supportsConstantUnpicking() {
		return generation > 1;
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
			return getLatestFeatherVersion(this.generation, mcVersion, minecraftJar) != null;
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
	public StepStatus provideMappings(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException {
		GameVersionBuildMeta featherVersion = getLatestFeatherVersion(this.generation, versionContext.targetVersion(), minecraftJar);
		if (featherVersion == null) {
			return StepStatus.NOT_RUN;
		}

		Path mappingsFile = getMappingsPathInternal(versionContext.targetVersion(), minecraftJar);
		if (Files.exists(mappingsFile) && validateMappings(mappingsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsFile);

		Path mappingsJarFile = getMappingsJarPath(this.generation, versionContext.targetVersion(), minecraftJar);
		StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), featherVersion.makeMergedV2JarMavenUrl(GitCraft.ORNITHE_MAVEN), new FileSystemNetworkManager.LocalFileInfo(mappingsJarFile, null, null, "feather gen " + generation + " mapping", versionContext.targetVersion().launcherFriendlyVersionName()));
		try (FileSystem fs = FileSystems.newFileSystem(mappingsJarFile)) {
			Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
			Files.copy(mappingsPathInJar, mappingsFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta featherVersion = getLatestFeatherVersion(this.generation, mcVersion, minecraftJar);
			if (featherVersion == null) {
				return null;
			}
			return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(versionKey(this.generation, mcVersion, minecraftJar) + "-feather-gen" + generation + "-build." + featherVersion.build() + ".tiny");
		} catch (IOException e) {
			return null;
		}
	}

	public static Path getMappingsJarPath(int generation, OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta featherVersion = getLatestFeatherVersion(generation, mcVersion, minecraftJar);
			if (featherVersion == null) {
				return null;
			}
			return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(versionKey(generation, mcVersion, minecraftJar) + "-feather-gen" + generation + "-build." + featherVersion.build() + ".jar");
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
