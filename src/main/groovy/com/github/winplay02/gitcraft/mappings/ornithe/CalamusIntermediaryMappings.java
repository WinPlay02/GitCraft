package com.github.winplay02.gitcraft.mappings.ornithe;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.meta.MetaUrls;
import com.github.winplay02.gitcraft.meta.RemoteVersionMetaSource;
import com.github.winplay02.gitcraft.meta.SimpleVersionMeta;
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
import java.util.Map;

public class CalamusIntermediaryMappings extends Mapping {
	private final int generation;
	private final VersionMetaSource<SimpleVersionMeta> calamusVersions;

	public CalamusIntermediaryMappings() {
		if (GitCraft.getApplicationConfiguration().ornitheIntermediaryGeneration() < 1) {
			throw new IllegalArgumentException("ornithe intermediary generation cannot be less than 1");
		}

		this.generation = GitCraft.getApplicationConfiguration().ornitheIntermediaryGeneration();
		this.calamusVersions = new RemoteVersionMetaSource<>(
			MetaUrls.ornitheCalamusIntermediary(this.generation),
			SerializationTypes.TYPE_LIST_SIMPLE_VERSION_META,
			SimpleVersionMeta::version
		);
	}

	private String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return generation == 1
			? mcVersion.launcherFriendlyVersionName() + (minecraftJar == MinecraftJar.MERGED ? "" : "-" + minecraftJar.name().toLowerCase())
			: mcVersion.launcherFriendlyVersionName();
	}

	private SimpleVersionMeta getLatestCalamusVersion(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException {
		return calamusVersions.getLatest(versionKey(mcVersion, minecraftJar));
	}

	@Override
	public String getName() {
		return "Calamus Intermediary Gen " + generation;
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.INTERMEDIARY.toString();
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
			return getLatestCalamusVersion(mcVersion, minecraftJar) != null;
		} catch (IOException | URISyntaxException | InterruptedException e) {
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
		SimpleVersionMeta calamusVersion = getLatestCalamusVersion(versionContext.targetVersion(), minecraftJar);
		if (calamusVersion == null) {
			return StepStatus.NOT_RUN;
		}
		Path mappingsFile = getMappingsPathInternal(versionContext.targetVersion(), minecraftJar);
		if (Files.exists(mappingsFile) && validateMappings(mappingsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsFile);
		Path mappingsJarFile = getMappingsJarPath(versionContext.targetVersion(), minecraftJar);
		StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), calamusVersion.makeV2JarMavenUrl(GitCraft.ORNITHE_MAVEN), new FileSystemNetworkManager.LocalFileInfo(mappingsJarFile, null, null, "calamus intermediary gen " + generation + " mapping", versionContext.targetVersion().launcherFriendlyVersionName()));
		try (FileSystem fs = FileSystems.newFileSystem(mappingsJarFile)) {
			Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
			Files.copy(mappingsPathInJar, mappingsFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(mcVersion.launcherFriendlyVersionName() + "-calamus-intermediary-gen" + generation + ".tiny");
	}

	private Path getMappingsJarPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(mcVersion.launcherFriendlyVersionName() + "-calamus-intermediary-gen" + generation + ".jar");
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
