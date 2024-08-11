package com.github.winplay02.gitcraft.nests.ornithe;

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
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.Nest;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import net.ornithemc.nester.nest.NesterIo;
import net.ornithemc.nester.nest.Nests;

public class OrnitheNests extends Nest {

	public static final String URL_ORNITHE_NESTS_META = "https://meta.ornithemc.net/v3/versions/nests";

	private Map<String, OrnitheNestsVersionMeta> nestsVersions = null;

	private static String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return minecraftJar == MinecraftJar.MERGED
			? mcVersion.launcherFriendlyVersionName()
			: mcVersion.launcherFriendlyVersionName() + "-" + minecraftJar.name().toLowerCase();
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
		return nestsVersions.containsKey(versionKey(mcVersion, minecraftJar));
	}

	@Override
	public boolean canNestsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		return mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)
			// <1.3, nests are provided separately for client and server jars
			? mappingFlavour.getMappingImpl().supportsMergingPre1_3Versions()
				// if the mapping allows for it, the mapped nests can be merged and used on any jar
				? doNestsExist(mcVersion)
				// otherwise, nests for that jar specifically must be available
				: doNestsExist(mcVersion, minecraftJar)
			// >=1.3, nests are provided as merged and can be used on any jar
			: doNestsExist(mcVersion, MinecraftJar.MERGED);
	}

	@Override
	public StepStatus provideNests(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) throws IOException {
		Path mappedNestsFile = getNestsPathInternal(mcVersion, minecraftJar, mappingFlavour);
		// Try existing mapped
		if (Files.exists(mappedNestsFile) && validateNests(mappedNestsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappedNestsFile);
		StepStatus status = null;
		Path nestsFile = getNestsPathInternal(mcVersion, minecraftJar, MappingFlavour.IDENTITY_UNMAPPED);
		// Try existing unmapped
		if (!Files.exists(nestsFile) || !validateNests(nestsFile)) {
			Files.deleteIfExists(nestsFile);
			// Get latest build info
			OrnitheNestsVersionMeta nestsVersion = getOrnitheNestsLatestBuild(mcVersion, minecraftJar);
			if (nestsVersion == null) {
				status = StepStatus.FAILED;
			} else {
				// Try latest ornithe nests build
				Path nestsFileJar = GitCraftPaths.NESTS.resolve(String.format("%s-ornithe-nests-build.%s.jar", versionKey(mcVersion, minecraftJar), nestsVersion.build()));
				try {
					StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(nestsVersion.makeMavenURL(), new RemoteHelper.LocalFileInfo(nestsFileJar, null, "ornithe nests", mcVersion.launcherFriendlyVersionName()));
					try (FileSystem fs = FileSystems.newFileSystem(nestsFileJar)) {
						Path nestsPathInJar = fs.getPath("nests", "mappings.nest");
						Files.copy(nestsPathInJar, nestsFile, StandardCopyOption.REPLACE_EXISTING);
					}
					status = StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
				} catch (IOException | RuntimeException ignored) {
					Files.deleteIfExists(nestsFileJar);
					status = StepStatus.FAILED;
				}
			}
		}
		if (status != StepStatus.FAILED && mappingFlavour != MappingFlavour.IDENTITY_UNMAPPED) {
			status = StepStatus.merge(status, mapNests(mcVersion, minecraftJar, mappingFlavour, nestsFile, mappedNestsFile));
		}
		return status;
	}

	@Override
	protected Path getNestsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour) {
		OrnitheNestsVersionMeta nestsVersion = getOrnitheNestsLatestBuild(mcVersion, minecraftJar);
		return nestsVersion == null ? null : GitCraftPaths.NESTS.resolve(String.format("%s-ornithe-nests-build.%d%s.nest", versionKey(mcVersion, minecraftJar), nestsVersion.build(), mappingFlavour == MappingFlavour.IDENTITY_UNMAPPED ? "" : mappingFlavour.toString()));
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingFlavour mappingFlavour, Nests visitor) throws IOException {
		if (mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)) {
			// <1.3, nests provided separately for client and server jars
			switch (minecraftJar) {
			case CLIENT:
			case SERVER:
				if (doNestsExist(mcVersion, minecraftJar)) {
					NesterIo.read(visitor, getNestsPathInternal(mcVersion, minecraftJar, mappingFlavour));
				}
				break;
			case MERGED:
				// if the mapping flavour allows for it, the mapped nests can be merged and used on any jar
				if (mappingFlavour.getMappingImpl().supportsMergingPre1_3Versions()) {
					visit(mcVersion, MinecraftJar.CLIENT, mappingFlavour, visitor);
					visit(mcVersion, MinecraftJar.SERVER, mappingFlavour, visitor);
				}
				break;
			}
		} else {
			// >=1.3, nests provided merged, can be used on any jar
			NesterIo.read(visitor, getNestsPathInternal(mcVersion, MinecraftJar.MERGED, mappingFlavour));
		}
	}

	private OrnitheNestsVersionMeta getOrnitheNestsLatestBuild(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		if (nestsVersions == null) {
			try {
				List<OrnitheNestsVersionMeta> nestsVersionMetas = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(URL_ORNITHE_NESTS_META)), SerializationHelper.TYPE_LIST_ORNITHE_NESTS_VERSION_META);
				nestsVersions = nestsVersionMetas.stream().collect(Collectors.groupingBy(OrnitheNestsVersionMeta::gameVersion)).values().stream().map(ornitheNestsVersionMetas -> ornitheNestsVersionMetas.stream().max(Comparator.naturalOrder())).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(OrnitheNestsVersionMeta::gameVersion, Function.identity()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return nestsVersions.get(versionKey(mcVersion, minecraftJar));
	}
}
