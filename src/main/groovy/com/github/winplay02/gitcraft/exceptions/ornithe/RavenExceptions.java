package com.github.winplay02.gitcraft.exceptions.ornithe;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.exceptions.ExceptionsPatch;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.ExceptorIo;

public class RavenExceptions extends ExceptionsPatch {

	public static final String URL_ORNITHE_EXCEPTIONS_META = "https://meta.ornithemc.net/v3/versions/raven";

	private Map<String, OrnitheRavenVersionMeta> ravenVersions = null;

	private static String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return minecraftJar == MinecraftJar.MERGED
			? mcVersion.launcherFriendlyVersionName()
			: mcVersion.launcherFriendlyVersionName() + "-" + minecraftJar.name().toLowerCase();
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
		return ravenVersions.containsKey(versionKey(mcVersion, minecraftJar));
	}

	@Override
	public boolean canExceptionsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)
			// <1.3, exceptions are provided separately for client and server jars
			? doExceptionsExist(mcVersion, minecraftJar)
			// >=1.3, exceptions are provided as merged and can be used on any jar
			: doExceptionsExist(mcVersion, MinecraftJar.MERGED);
	}

	@Override
	public StepStatus provideExceptions(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		Path exceptionsFile = getExceptionsPathInternal(mcVersion, minecraftJar);
		// Try existing
		if (Files.exists(exceptionsFile) && validateExceptions(exceptionsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(exceptionsFile);
		// Get latest build info
		OrnitheRavenVersionMeta ravenVersion = getOrnitheRavenLatestBuild(mcVersion, minecraftJar);
		if (ravenVersion == null) {
			return StepStatus.FAILED;
		}
		// Try latest ornithe raven build
		Path exceptionsFileJar = GitCraftPaths.EXCEPTIONS.resolve(String.format("%s-ornithe-raven-build.%s.jar", versionKey(mcVersion, minecraftJar), ravenVersion.build()));
		try {
			StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(ravenVersion.makeMavenURL(), new RemoteHelper.LocalFileInfo(exceptionsFileJar, null, "ornithe raven", mcVersion.launcherFriendlyVersionName()));
			try (FileSystem fs = FileSystems.newFileSystem(exceptionsFileJar)) {
				Path exceptionsPathInJar = fs.getPath("exceptions", "mappings.excs");
				Files.copy(exceptionsPathInJar, exceptionsFile, StandardCopyOption.REPLACE_EXISTING);
			}
			return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
		} catch (IOException | RuntimeException ignored) {
			Files.deleteIfExists(exceptionsFileJar);
		}
		return StepStatus.FAILED;
	}

	@Override
	protected Path getExceptionsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		OrnitheRavenVersionMeta exceptionsVersion = getOrnitheRavenLatestBuild(mcVersion, minecraftJar);
		return exceptionsVersion == null ? null : GitCraftPaths.EXCEPTIONS.resolve(String.format("%s-ornithe-raven-build.%d.excs", versionKey(mcVersion, minecraftJar), exceptionsVersion.build()));
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, ExceptionsFile visitor) throws IOException {
		if (mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)) {
			// <1.3, exceptions provided separately for client and server jars
			if (doExceptionsExist(mcVersion, minecraftJar)) {
				ExceptorIo.read(getExceptionsPathInternal(mcVersion, minecraftJar), visitor);
			}
		} else {
			// >=1.3, exceptions provided merged, can be used on any jar
			ExceptorIo.read(getExceptionsPathInternal(mcVersion, MinecraftJar.MERGED), visitor);
		}
	}

	private OrnitheRavenVersionMeta getOrnitheRavenLatestBuild(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		if (ravenVersions == null) {
			try {
				List<OrnitheRavenVersionMeta> ravenVersionMetas = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(URL_ORNITHE_EXCEPTIONS_META)), SerializationHelper.TYPE_LIST_ORNITHE_RAVEN_VERSION_META);
				ravenVersions = ravenVersionMetas.stream().collect(Collectors.groupingBy(OrnitheRavenVersionMeta::gameVersion)).values().stream().map(ornitheRavenVersionMetas -> ornitheRavenVersionMetas.stream().max(Comparator.naturalOrder())).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(OrnitheRavenVersionMeta::gameVersion, Function.identity()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return ravenVersions.get(versionKey(mcVersion, minecraftJar));
	}
}
