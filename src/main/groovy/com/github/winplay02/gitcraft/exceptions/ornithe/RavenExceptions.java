package com.github.winplay02.gitcraft.exceptions.ornithe;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.exceptions.ExceptionsPatch;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MetaVersionsSource;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.ExceptorIo;

public class RavenExceptions extends ExceptionsPatch {

	private final MetaVersionsSource<GameVersionBuildMeta> ravenVersions;

	public RavenExceptions() {
		this.ravenVersions = new MetaVersionsSource<>(
			"https://meta.ornithemc.net/v3/versions/raven",
			SerializationHelper.TYPE_LIST_GAME_VERSION_BUILD_META,
			GameVersionBuildMeta::gameVersion
		);
	}

	private static String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return minecraftJar == MinecraftJar.MERGED
			? mcVersion.launcherFriendlyVersionName()
			: mcVersion.launcherFriendlyVersionName() + "-" + minecraftJar.name().toLowerCase();
	}

	private GameVersionBuildMeta getLatestRavenVersion(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
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
		} catch (IOException e) {
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
	public StepStatus provideExceptions(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		GameVersionBuildMeta ravenVersion = getLatestRavenVersion(mcVersion, minecraftJar);
		if (ravenVersion == null) {
			return StepStatus.NOT_RUN;
		}
		Path exceptionsFile = getExceptionsPathInternal(mcVersion, minecraftJar);
		if (Files.exists(exceptionsFile) && validateExceptions(exceptionsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(exceptionsFile);
		Path exceptionsJarFile = getExceptionsJarPath(mcVersion, minecraftJar);
		StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(ravenVersion.makeMavenJarUrl(GitCraft.ORNITHE_MAVEN), new RemoteHelper.LocalFileInfo(exceptionsJarFile, null, "ornithe raven", mcVersion.launcherFriendlyVersionName()));
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
			return ravenVersion == null ? null : GitCraftPaths.EXCEPTIONS.resolve(String.format("%s-ornithe-raven-build.%d.excs", versionKey(mcVersion, minecraftJar), ravenVersion.build()));
		} catch (IOException e) {
			return null;
		}
	}

	private Path getExceptionsJarPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta ravenVersion = getLatestRavenVersion(mcVersion, minecraftJar);
			if (ravenVersion == null) {
				return null;
			}
			return ravenVersion == null ? null : GitCraftPaths.EXCEPTIONS.resolve(String.format("%s-ornithe-raven-build.%d.jar", versionKey(mcVersion, minecraftJar), ravenVersion.build()));
		} catch (IOException e) {
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
