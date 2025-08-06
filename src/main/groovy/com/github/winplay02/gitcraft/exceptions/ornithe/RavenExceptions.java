package com.github.winplay02.gitcraft.exceptions.ornithe;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.exceptions.ExceptionsPatch;
import com.github.winplay02.gitcraft.meta.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.meta.MetaUrls;
import com.github.winplay02.gitcraft.meta.RemoteVersionMetaSource;
import com.github.winplay02.gitcraft.meta.VersionMetaSource;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.RemoteHelper;

import com.github.winplay02.gitcraft.util.SerializationTypes;
import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.ExceptorIo;

public class RavenExceptions extends ExceptionsPatch {

	private final VersionMetaSource<GameVersionBuildMeta> ravenVersions;

	public RavenExceptions() {
		this.ravenVersions = new RemoteVersionMetaSource<>(
			MetaUrls.ORNITHE_RAVEN,
			SerializationTypes.TYPE_LIST_GAME_VERSION_BUILD_META,
			GameVersionBuildMeta::gameVersion
		);
	}

	private static String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return minecraftJar == MinecraftJar.MERGED
			? mcVersion.launcherFriendlyVersionName()
			: mcVersion.launcherFriendlyVersionName() + "-" + minecraftJar.name().toLowerCase();
	}

	private GameVersionBuildMeta getLatestRavenVersion(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException {
		return ravenVersions.getLatest(versionKey(mcVersion, minecraftJar));
	}

	@Override
	public String getName() {
		return "Ornithe Raven";
	}

	@Override
	public boolean doExceptionsExist(OrderedVersion mcVersion) {
		return doExceptionsExist(mcVersion, MinecraftJar.CLIENT) || doExceptionsExist(mcVersion, MinecraftJar.SERVER) || doExceptionsExist(mcVersion, MinecraftJar.MERGED);
	}

	@Override
	public boolean doExceptionsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			return getLatestRavenVersion(mcVersion, minecraftJar) != null;
		} catch (IOException | URISyntaxException | InterruptedException e) {
			return false;
		}
	}

	@Override
	public boolean canExceptionsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return (!mcVersion.hasSharedVersioning() || mcVersion.hasSharedObfuscation())
			? doExceptionsExist(mcVersion, MinecraftJar.MERGED)
			: doExceptionsExist(mcVersion, minecraftJar);
	}

	@Override
	public StepStatus provideExceptions(StepWorker.Context<OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException {
		GameVersionBuildMeta ravenVersion = getLatestRavenVersion(versionContext.targetVersion(), minecraftJar);
		if (ravenVersion == null) {
			return StepStatus.NOT_RUN;
		}
		Path exceptionsFile = getExceptionsPathInternal(versionContext.targetVersion(), minecraftJar);
		if (Files.exists(exceptionsFile) && validateExceptions(exceptionsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(exceptionsFile);
		Path exceptionsJarFile = getExceptionsJarPath(versionContext.targetVersion(), minecraftJar);
		StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), ravenVersion.makeJarMavenUrl(GitCraft.ORNITHE_MAVEN), new FileSystemNetworkManager.LocalFileInfo(exceptionsJarFile, null, null, "ornithe raven", versionContext.targetVersion().launcherFriendlyVersionName()));
		try (FileSystem fs = FileSystems.newFileSystem(exceptionsJarFile)) {
			Path exceptionsPathInJar = fs.getPath("exceptions", "mappings.excs");
			Files.copy(exceptionsPathInJar, exceptionsFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
	}

	@Override
	protected Path getExceptionsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta ravenVersion = getLatestRavenVersion(mcVersion, minecraftJar);
			if (ravenVersion == null) {
				return null;
			}
			return ravenVersion == null ? null : PipelineFilesystemStorage.DEFAULT.get().rootFilesystem().getPatchesStore().resolve(String.format("%s-ornithe-raven-build.%d.excs", versionKey(mcVersion, minecraftJar), ravenVersion.build()));
		} catch (IOException | URISyntaxException | InterruptedException e) {
			return null;
		}
	}

	private Path getExceptionsJarPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta ravenVersion = getLatestRavenVersion(mcVersion, minecraftJar);
			if (ravenVersion == null) {
				return null;
			}
			return ravenVersion == null ? null : PipelineFilesystemStorage.DEFAULT.get().rootFilesystem().getPatchesStore().resolve(String.format("%s-ornithe-raven-build.%d.jar", versionKey(mcVersion, minecraftJar), ravenVersion.build()));
		} catch (IOException | URISyntaxException | InterruptedException e) {
			return null;
		}
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, ExceptionsFile visitor) throws IOException {
		if (!mcVersion.hasSharedVersioning() || mcVersion.hasSharedObfuscation()) {
			minecraftJar = MinecraftJar.MERGED;
		}
		ExceptorIo.read(getExceptionsPathInternal(mcVersion, minecraftJar), visitor);
	}
}
