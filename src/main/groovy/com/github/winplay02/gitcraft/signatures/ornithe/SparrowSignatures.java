package com.github.winplay02.gitcraft.signatures.ornithe;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.github.winplay02.gitcraft.signatures.SignaturesPatch;
import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MetaVersionsSource;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import io.github.gaming32.signaturechanger.tree.SigsFile;
import io.github.gaming32.signaturechanger.visitor.SigsReader;


public class SparrowSignatures extends SignaturesPatch {

	private final MetaVersionsSource<GameVersionBuildMeta> sparrowVersions;

	public SparrowSignatures() {
		this.sparrowVersions = new MetaVersionsSource<>(
			"https://meta.ornithemc.net/v3/versions/sparrow",
			SerializationHelper.TYPE_LIST_GAME_VERSION_BUILD_META,
			GameVersionBuildMeta::gameVersion
		);
	}

	private static String versionKey(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return minecraftJar == MinecraftJar.MERGED
			? mcVersion.launcherFriendlyVersionName()
			: mcVersion.launcherFriendlyVersionName() + "-" + minecraftJar.name().toLowerCase();
	}

	private GameVersionBuildMeta getLatestSparrowVersion(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		return sparrowVersions.getLatest(versionKey(mcVersion, minecraftJar));
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
		try {
			return getLatestSparrowVersion(mcVersion, minecraftJar) != null;
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public boolean canSignaturesBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return (!mcVersion.hasSharedVersioning() || mcVersion.hasSharedObfuscation())
			? doSignaturesExist(mcVersion, MinecraftJar.MERGED)
			: doSignaturesExist(mcVersion, minecraftJar);
	}

	@Override
	public StepStatus provideSignatures(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		GameVersionBuildMeta sparrowVersion = getLatestSparrowVersion(mcVersion, minecraftJar);
		if (sparrowVersion == null) {
			return StepStatus.NOT_RUN;
		}
		Path signaturesFile = getSignaturesPathInternal(mcVersion, minecraftJar);
		if (Files.exists(signaturesFile) && validateSignatures(signaturesFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(signaturesFile);
		Path signaturesJarFile = getSignaturesJarPath(mcVersion, minecraftJar);
		StepStatus downloadStatus = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(sparrowVersion.makeMavenJarUrl(GitCraft.ORNITHE_MAVEN), new RemoteHelper.LocalFileInfo(signaturesJarFile, null, "ornithe sparrow", mcVersion.launcherFriendlyVersionName()));
		try (FileSystem fs = FileSystems.newFileSystem(signaturesJarFile)) {
			Path signaturesPathInJar = fs.getPath("signatures", "mappings.sigs");
			Files.copy(signaturesPathInJar, signaturesFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return StepStatus.merge(downloadStatus, StepStatus.SUCCESS);
	}

	@Override
	protected Path getSignaturesPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta sparrowVersion = getLatestSparrowVersion(mcVersion, minecraftJar);
			if (sparrowVersion == null) {
				return null;
			}
			return sparrowVersion == null ? null : GitCraftPaths.SIGNATURES.resolve(String.format("%s-ornithe-sparrow-build.%d.sigs", versionKey(mcVersion, minecraftJar), sparrowVersion.build()));
		} catch (IOException e) {
			return null;
		}
	}

	private Path getSignaturesJarPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		try {
			GameVersionBuildMeta sparrowVersion = getLatestSparrowVersion(mcVersion, minecraftJar);
			if (sparrowVersion == null) {
				return null;
			}
			return sparrowVersion == null ? null : GitCraftPaths.SIGNATURES.resolve(String.format("%s-ornithe-sparrow-build.%d.jar", versionKey(mcVersion, minecraftJar), sparrowVersion.build()));
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, SigsFile visitor) throws IOException {
		if (!mcVersion.hasSharedVersioning() || mcVersion.hasSharedObfuscation()) {
			minecraftJar = MinecraftJar.MERGED;
		}
		Path sigsPath = getSignaturesPathInternal(mcVersion, minecraftJar);
		try (SigsReader sr = new SigsReader(Files.newBufferedReader(sigsPath))) {
			sr.accept(visitor);
		}
	}
}
