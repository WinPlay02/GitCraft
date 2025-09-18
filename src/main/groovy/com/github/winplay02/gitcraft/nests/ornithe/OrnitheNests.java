package com.github.winplay02.gitcraft.nests.ornithe;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.meta.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.meta.MetaUrls;
import com.github.winplay02.gitcraft.meta.RemoteVersionMetaSource;
import com.github.winplay02.gitcraft.meta.VersionMetaSource;
import com.github.winplay02.gitcraft.nests.Nest;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.RemoteHelper;

import com.github.winplay02.gitcraft.util.SerializationTypes;
import net.ornithemc.nester.nest.NesterIo;
import net.ornithemc.nester.nest.Nests;

public class OrnitheNests extends Nest {

	private final VersionMetaSource<GameVersionBuildMeta> nestsVersions;

	public OrnitheNests() {
		this.nestsVersions = new RemoteVersionMetaSource<>(
			MetaUrls.ORNITHE_NESTS,
			SerializationTypes.TYPE_LIST_GAME_VERSION_BUILD_META,
			GameVersionBuildMeta::gameVersion
		);
	}

	private static String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return minecraftJar == MinecraftJar.MERGED
			? mcVersion.launcherFriendlyVersionName()
			: mcVersion.launcherFriendlyVersionName() + "-" + minecraftJar.name().toLowerCase();
	}

	private GameVersionBuildMeta getLatestNestsVersion(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException {
		return nestsVersions.getLatest(versionKey(mcVersion, minecraftJar));
	}

	@Override
	public String getName() {
		return "Ornithe Nests";
	}

	@Override
	public boolean doNestsExist(OrderedVersion mcVersion) {
		return doNestsExist(mcVersion, MinecraftJar.CLIENT) || doNestsExist(mcVersion, MinecraftJar.SERVER) || doNestsExist(mcVersion, MinecraftJar.MERGED);
	}

	@Override
	public boolean doNestsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			return getLatestNestsVersion(mcVersion, minecraftJar) != null;
		} catch (IOException | URISyntaxException | InterruptedException e) {
			return false;
		}
	}

	@Override
	public boolean canNestsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		return (!mcVersion.hasSharedVersioning() || mcVersion.hasSharedObfuscation())
			? doNestsExist(mcVersion, MinecraftJar.MERGED)
			: ((minecraftJar == MinecraftJar.MERGED)
				? doNestsExist(mcVersion)
				: doNestsExist(mcVersion, minecraftJar));
	}

	@Override
	public StepStatus provideNests(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) throws IOException, URISyntaxException, InterruptedException {
		GameVersionBuildMeta nestsVersion = getLatestNestsVersion(versionContext.targetVersion(), minecraftJar);
		if (nestsVersion == null) {
			return StepStatus.NOT_RUN;
		}
		if (!mappingFlavour.canBeUsedOn(versionContext.targetVersion(), minecraftJar)) {
			return StepStatus.NOT_RUN;
		}
		Path mappedNestsFile = getNestsPathInternal(versionContext.targetVersion(), minecraftJar, mappingFlavour);
		if (Files.exists(mappedNestsFile) && validateNests(mappedNestsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappedNestsFile);
		Path nestsFile = getNestsPathInternal(versionContext.targetVersion(), minecraftJar, MappingFlavour.IDENTITY_UNMAPPED);
		if (mappingFlavour == MappingFlavour.IDENTITY_UNMAPPED) {
			if (Files.exists(nestsFile) && validateNests(nestsFile)) {
				return StepStatus.UP_TO_DATE;
			}
			Files.deleteIfExists(nestsFile);
			Path nestsJarFile = getNestsJarPath(versionContext.targetVersion(), minecraftJar, mappingFlavour);
			StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), nestsVersion.makeJarMavenUrl(GitCraft.ORNITHE_MAVEN), new FileSystemNetworkManager.LocalFileInfo(nestsJarFile, null, null, "ornithe nests", versionContext.targetVersion().launcherFriendlyVersionName()));
			try (FileSystem fs = FileSystems.newFileSystem(nestsJarFile)) {
				Path nestsPathInJar = fs.getPath("nests", "mappings.nest");
				Files.copy(nestsPathInJar, nestsFile, StandardCopyOption.REPLACE_EXISTING);
			}
			return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
		} else {
			StepStatus unmappedStatus = provideNests(versionContext, minecraftJar, MappingFlavour.IDENTITY_UNMAPPED);
			if (!Files.exists(nestsFile) || !validateNests(nestsFile)) {
				return StepStatus.merge(unmappedStatus, StepStatus.FAILED);
			}
			StepStatus mapStatus = mapNests(versionContext.targetVersion(), minecraftJar, mappingFlavour, nestsFile, mappedNestsFile);
			return StepStatus.merge(unmappedStatus, mapStatus, StepStatus.SUCCESS);
		}
	}

	@Override
	protected Path getNestsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		try {
			GameVersionBuildMeta nestsVersion = getLatestNestsVersion(mcVersion, minecraftJar);
			if (nestsVersion == null) {
				return null;
			}
			return nestsVersion == null ? null : GitCraftPipelineFilesystemRoot.getPatchesStore().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-ornithe-nests-build.%d%s.nest", versionKey(mcVersion, minecraftJar), nestsVersion.build(), mappingFlavour == MappingFlavour.IDENTITY_UNMAPPED ? "" : ("-" + mappingFlavour.toString())));
		} catch (IOException | URISyntaxException | InterruptedException e) {
			return null;
		}
	}

	private Path getNestsJarPath(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		try {
			GameVersionBuildMeta nestsVersion = getLatestNestsVersion(mcVersion, minecraftJar);
			if (nestsVersion == null) {
				return null;
			}
			return nestsVersion == null ? null : GitCraftPipelineFilesystemRoot.getPatchesStore().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-ornithe-nests-build.%d%s.jar", versionKey(mcVersion, minecraftJar), nestsVersion.build(), mappingFlavour == MappingFlavour.IDENTITY_UNMAPPED ? "" : ("-" + mappingFlavour.toString())));
		} catch (IOException | URISyntaxException | InterruptedException e) {
			return null;
		}
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour, Nests visitor) throws IOException {
		if (!mcVersion.hasSharedVersioning() || mcVersion.hasSharedObfuscation()) {
			NesterIo.read(visitor, getNestsPathInternal(mcVersion, MinecraftJar.MERGED, mappingFlavour));
		} else {
			if (minecraftJar == MinecraftJar.MERGED) {
				visit(mcVersion, MinecraftJar.CLIENT, mappingFlavour, visitor);
				visit(mcVersion, MinecraftJar.SERVER, mappingFlavour, visitor);
			} else if (doNestsExist(mcVersion, minecraftJar)) {
				NesterIo.read(visitor, getNestsPathInternal(mcVersion, minecraftJar, mappingFlavour));
			}
		}
	}
}
