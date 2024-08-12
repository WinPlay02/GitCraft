package com.github.winplay02.gitcraft.signatures.ornithe;

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
import com.github.winplay02.gitcraft.signatures.SignaturesPatch;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import io.github.gaming32.signaturechanger.tree.SigsFile;
import io.github.gaming32.signaturechanger.visitor.SigsReader;


public class SparrowSignatures extends SignaturesPatch {

	public static final String URL_ORNITHE_EXCEPTIONS_META = "https://meta.ornithemc.net/v3/versions/sparrow";

	private Map<String, OrnitheSparrowVersionMeta> sparrowVersions = null;

	private static String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return minecraftJar == MinecraftJar.MERGED
			? mcVersion.launcherFriendlyVersionName()
			: mcVersion.launcherFriendlyVersionName() + "-" + minecraftJar.name().toLowerCase();
	}

	@Override
	public String getName() {
		return "Ornithe Sparrow";
	}

	@Override
	public boolean doSignaturesExist(OrderedVersion mcVersion) {
		return doSignaturesExist(mcVersion, MinecraftJar.CLIENT) || doSignaturesExist(mcVersion, MinecraftJar.SERVER) || doSignaturesExist(mcVersion, MinecraftJar.MERGED);
	}

	@Override
	public boolean doSignaturesExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return sparrowVersions.containsKey(versionKey(mcVersion, minecraftJar));
	}

	@Override
	public boolean canSignaturesBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)
			// <1.3, signatures are provided separately for client and server jars
			? doSignaturesExist(mcVersion, minecraftJar)
			// >=1.3, signatures are provided as merged and can be used on any jar
			: doSignaturesExist(mcVersion, MinecraftJar.MERGED);
	}

	@Override
	public StepStatus provideSignatures(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		Path signaturesFile = getSignaturesPathInternal(mcVersion, minecraftJar);
		// Try existing
		if (Files.exists(signaturesFile) && validateSignatures(signaturesFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(signaturesFile);
		// Get latest build info
		OrnitheSparrowVersionMeta sparrowVersion = getOrnitheSparrowLatestBuild(mcVersion, minecraftJar);
		if (sparrowVersion == null) {
			return StepStatus.FAILED;
		}
		// Try latest ornithe sparrow build
		Path signaturesFileJar = GitCraftPaths.EXCEPTIONS.resolve(String.format("%s-ornithe-sparrow-build.%s.jar", versionKey(mcVersion, minecraftJar), sparrowVersion.build()));
		try {
			StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(sparrowVersion.makeMavenURL(), new RemoteHelper.LocalFileInfo(signaturesFileJar, null, "ornithe sparrow", mcVersion.launcherFriendlyVersionName()));
			try (FileSystem fs = FileSystems.newFileSystem(signaturesFileJar)) {
				Path signaturesPathInJar = fs.getPath("signatures", "mappings.sigs");
				Files.copy(signaturesPathInJar, signaturesFile, StandardCopyOption.REPLACE_EXISTING);
			}
			return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
		} catch (IOException | RuntimeException ignored) {
			Files.deleteIfExists(signaturesFileJar);
		}
		return StepStatus.FAILED;
	}

	@Override
	protected Path getSignaturesPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		OrnitheSparrowVersionMeta signaturesVersion = getOrnitheSparrowLatestBuild(mcVersion, minecraftJar);
		return signaturesVersion == null ? null : GitCraftPaths.EXCEPTIONS.resolve(String.format("%s-ornithe-sparrow-build.%d.sigs", versionKey(mcVersion, minecraftJar), signaturesVersion.build()));
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, SigsFile visitor) throws IOException {
		if (mcVersion.timestamp().isBefore(GitCraftConfig.FIRST_MERGEABLE_VERSION_RELEASE_TIME)) {
			// <1.3, signatures provided separately for client and server jars
			if (doSignaturesExist(mcVersion, minecraftJar)) {
				Path sigsPath = getSignaturesPathInternal(mcVersion, minecraftJar);

				try (SigsReader sr = new SigsReader(Files.newBufferedReader(sigsPath))) {
					sr.accept(visitor);
				}
			}
		} else {
			// >=1.3, signatures provided merged, can be used on any jar
			Path sigsPath = getSignaturesPathInternal(mcVersion, MinecraftJar.MERGED);

			try (SigsReader sr = new SigsReader(Files.newBufferedReader(sigsPath))) {
				sr.accept(visitor);
			}
		}
	}

	private OrnitheSparrowVersionMeta getOrnitheSparrowLatestBuild(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		if (sparrowVersions == null) {
			try {
				List<OrnitheSparrowVersionMeta> sparrowVersionMetas = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(URL_ORNITHE_EXCEPTIONS_META)), SerializationHelper.TYPE_LIST_ORNITHE_SPARROW_VERSION_META);
				sparrowVersions = sparrowVersionMetas.stream().collect(Collectors.groupingBy(OrnitheSparrowVersionMeta::gameVersion)).values().stream().map(ornitheSparrowVersionMetas -> ornitheSparrowVersionMetas.stream().max(Comparator.naturalOrder())).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(OrnitheSparrowVersionMeta::gameVersion, Function.identity()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return sparrowVersions.get(versionKey(mcVersion, minecraftJar));
	}
}
