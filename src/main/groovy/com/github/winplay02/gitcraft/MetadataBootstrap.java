package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.meta.LauncherMeta;
import com.github.winplay02.gitcraft.meta.VersionMeta;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.game.minecraft.McVersion;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import net.fabricmc.loom.util.FileSystemUtil;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class MetadataBootstrap {

	private static TreeMap<String, String> semverCache = null;
	protected static LinkedHashMap<String, OrderedVersion> versionMeta = new LinkedHashMap<>();

	public static LinkedHashMap<String, OrderedVersion> initialize() throws IOException {
		return initialize(GitCraft.MC_VERSION_META_STORE);
	}

	protected static LinkedHashMap<String, OrderedVersion> initialize(Path rootMeta) throws IOException {
		loadSemverCache();
		MiscHelper.println("Creating metadata...");
		// Read all meta entries for each source
		for (Map.Entry<String, String> metaEntry : GitCraftConfig.URL_META.entrySet()) {
			MiscHelper.println("Reading %s from %s...", metaEntry.getKey(), metaEntry.getValue());
			LauncherMeta launcherMeta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(metaEntry.getValue())), LauncherMeta.class);
			// Load version corresponding to each meta entry
			for (LauncherMeta.LauncherVersionEntry version : launcherMeta.versions()) {
				Path versionMetaPath = rootMeta.resolve(version.id() + ".json");
				if (!versionMeta.containsKey(version.id())) {
					versionMeta.put(version.id(), loadVersionData(versionMetaPath, version.id(), version.url(), version.sha1()));
				} else {
					if (RemoteHelper.SHA1.fileMatchesChecksum(versionMetaPath, version.sha1())) {
						MiscHelper.println("WARNING: Found duplicate version meta for version: %s (Matches previous entry)", version.id());
					} else {
						MiscHelper.panic("Found duplicate version meta for version: %s (Differs from previous)", version.id());
					}
				}
			}
		}
		MiscHelper.println("Applying extra version from local (%s)...", GitCraft.SOURCE_EXTRA_VERSIONS);

		for (Path localExtraVersion : MiscHelper.listDirectly(GitCraft.SOURCE_EXTRA_VERSIONS)) {
			if (localExtraVersion.toString().endsWith(".gitkeep")) { // silently ignore gitkeep
				continue;
			}
			if (!localExtraVersion.toString().endsWith(".json")) {
				MiscHelper.println("Skipped extra version '%s' as it is not a .json file", localExtraVersion);
				continue;
			}
			OrderedVersion extra_version_object = loadVersionDataExtra(rootMeta, localExtraVersion, false);
			versionMeta.put(extra_version_object.launcherFriendlyVersionName(), extra_version_object);
			MiscHelper.println("Applied extra version '%s'", extra_version_object.launcherFriendlyVersionName());
		}
		MiscHelper.println("Applying extra version from remote...");
		for (Artifact metaEntryExtra : GitCraftConfig.URL_EXTRA_META) {
			Path srcPath = metaEntryExtra.resolve(GitCraft.MC_VERSION_META_DOWNLOADS);
			metaEntryExtra.fetchArtifact(GitCraft.MC_VERSION_META_DOWNLOADS);
			// Try ZIP
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(srcPath)) {
				for (Path potentialMetaFile : MiscHelper.listRecursivelyFilteredExtension(fs.getPath("."), ".json")) {
					OrderedVersion extra_version_object = loadVersionDataExtra(rootMeta, potentialMetaFile, true);
					versionMeta.put(extra_version_object.launcherFriendlyVersionName(), extra_version_object);
					MiscHelper.println("Applied version '%s'", extra_version_object.launcherFriendlyVersionName());
				}
				continue;
			} catch (IOException | ProviderNotFoundException ignored) {
			}
			// Try JSON
			try {
				OrderedVersion extra_version_object = loadVersionDataExtra(rootMeta, srcPath, true);
				versionMeta.put(extra_version_object.launcherFriendlyVersionName(), extra_version_object);
				MiscHelper.println("Applied version '%s'", extra_version_object.launcherFriendlyVersionName());
			} catch (JsonSyntaxException exception) {
				MiscHelper.println("Extra version at %s is neither a zip file nor a json file", metaEntryExtra.url());
			}
		}
		return versionMeta;
	}

	protected static OrderedVersion loadVersionData(Path versionMeta, String versionId, String versionUrl, String versionSha1) throws IOException {
		RemoteHelper.downloadToFileWithChecksumIfNotExists(versionUrl, new RemoteHelper.LocalFileInfo(versionMeta, versionSha1, "version meta", versionId), RemoteHelper.SHA1);
		VersionMeta meta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(versionMeta), VersionMeta.class);
		String semver = lookupLoaderVersion(GitCraft.MC_VERSION_STORE.resolve(meta.id()), meta);
		return OrderedVersion.from(meta, semver);
	}

	protected static OrderedVersion loadVersionDataExtra(Path versionMetaRoot, Path extraVersionMeta, boolean remote) throws IOException {
		VersionMeta meta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(extraVersionMeta), VersionMeta.class);
		Path versionMetaPath = versionMetaRoot.resolve(meta.id() + ".json");
		String versionSha1 = RemoteHelper.SHA1.getChecksumFile(extraVersionMeta);
		if (versionMeta.containsKey(meta.id())) {
			if (RemoteHelper.SHA1.fileMatchesChecksum(versionMetaPath, versionSha1)) {
				MiscHelper.println("WARNING: Found duplicate extra version meta for version: %s (Matches previous entry)", meta.id());
			} else {
				if (remote) {
					MiscHelper.panic("Found duplicate extra version meta for version: %s (Matches previous entry).", extraVersionMeta);
				} else {
					MiscHelper.panic("Found duplicate extra version meta for version: %s (Matches previous entry). Please remove file: %s", extraVersionMeta);
				}
			}
		} else {
			Files.copy(extraVersionMeta, versionMetaPath, StandardCopyOption.REPLACE_EXISTING);
		}
		String semver = lookupLoaderVersion(GitCraft.MC_VERSION_STORE.resolve(meta.id()), meta);
		return OrderedVersion.from(meta, semver);
	}

	protected static String lookupLoaderVersion(Path clientJarArtifactPath, VersionMeta versionMeta) {
		// FIX until fabric-loader is updated
		{
			if (GitCraftConfig.minecraftVersionSemVerOverride.containsKey(versionMeta.id())) {
				return GitCraftConfig.minecraftVersionSemVerOverride.get(versionMeta.id());
			}
		}
		// END FIX
		if (semverCache.containsKey(versionMeta.id())) {
			try {
				SemanticVersion.parse(semverCache.get(versionMeta.id()));
				return semverCache.get(versionMeta.id());
			} catch (VersionParsingException ignored) {
			}
		}

		Artifact clientJar = OrderedVersion.getClientJarFromMeta(versionMeta);
		if (clientJar != null) {
			clientJar.fetchArtifact(clientJarArtifactPath, "client jar");
		}
		McVersion lookedUpVersion = null;
		try {
			lookedUpVersion = McVersionLookup.getVersion(List.of(clientJarArtifactPath), versionMeta.mainClass(), null);
		} catch (Exception ignored1) {
			lookedUpVersion = McVersionLookup.getVersion(List.of(clientJarArtifactPath), null, versionMeta.id());
		}
		String lookedUpSemver = fixupSemver(Objects.equals(lookedUpVersion.getNormalized(), "client") ? versionMeta.id() : lookedUpVersion.getNormalized());
		MiscHelper.println("Semver mapped for: %s as %s", lookedUpVersion.getRaw(), lookedUpSemver);
		return lookedUpSemver;
	}

	private static void loadSemverCache() {
		if (semverCache == null) {
			Path cachePath = GitCraft.CURRENT_WORKING_DIRECTORY.resolve("semver-cache.json");
			if (cachePath.toFile().exists()) {
				try {
					semverCache = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(cachePath), SerializationHelper.TYPE_TREE_MAP_STRING_STRING);
				} catch (IOException e) {
					semverCache = new TreeMap<>();
					MiscHelper.println("This is not a fatal error: %s", e);
				}
			} else {
				semverCache = new TreeMap<>();
			}
		}
	}

	private static String fixupSemver(String proposedSemVer) {
		if (Objects.equals(proposedSemVer, "1.19-22.w.13.oneBlockAtATime")) {
			return "1.19-alpha.22.13.oneblockatatime";
		}
		if (Objects.equals(proposedSemVer, "1.16.2-Combat.Test.8")) { // this is wrong here, fabric gets it correct
			return "1.16.3-combat.8";
		}
		if (Objects.equals(proposedSemVer, "0.30.1.c")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.30.1-c";
		}
		if (Objects.equals(proposedSemVer, "0.0.13.a")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.13-a";
		}
		if (Objects.equals(proposedSemVer, "0.0.13.a.3")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.13-a3";
		}
		if (Objects.equals(proposedSemVer, "0.0.11.a")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.11-a";
		}
		if (Objects.equals(proposedSemVer, "rd-161348")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.161348-rd";
		}
		if (Objects.equals(proposedSemVer, "rd-160052")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.160052-rd";
		}
		if (Objects.equals(proposedSemVer, "rd-20090515")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.132328-rd20090515";
		}
		if (Objects.equals(proposedSemVer, "rd-132328")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.132328-rd";
		}
		if (Objects.equals(proposedSemVer, "rd-132211")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.132211-rd";
		}
		if (proposedSemVer.contains("-Experimental")) {
			return proposedSemVer.replace("-Experimental", "-alpha.0.0.Experimental");
		}
		return proposedSemVer;
	}
}
