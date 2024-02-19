package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.meta.ILauncherMeta;
import com.github.winplay02.gitcraft.meta.ILauncherMetaVersionEntry;
import com.github.winplay02.gitcraft.meta.VersionMeta;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public abstract class ManifestProvider<T extends ILauncherMeta<M>, M extends ILauncherMetaVersionEntry> {
	protected final Path rootPath;
	protected List<DescribedURL> manifestSourceUrls;
	protected List<Artifact> singleMetaUrls;
	protected Class<T> metaClass;
	protected TreeMap<String, String> semverCache = null;
	protected LinkedHashMap<String, OrderedVersion> versionMeta = null;

	protected ManifestProvider(DescribedURL[] manifestSourceUrls, Class<T> metaClass) {
		this(manifestSourceUrls, new Artifact[0], metaClass);
	}

	protected ManifestProvider(DescribedURL[] manifestSourceUrls, Artifact[] singleMetaUrls, Class<T> metaClass) {
		this.rootPath = GitCraftPaths.MC_VERSION_META_STORE.resolve(getInternalName());
		this.manifestSourceUrls = new ArrayList<>(List.of(manifestSourceUrls));
		this.singleMetaUrls = new ArrayList<>(List.of(singleMetaUrls));
		this.metaClass = metaClass;
		this.loadSemverCache();
	}

	/**
	 * @return A human-readable string that identifies this manifest provider.
	 */
	public abstract String getName();

	/**
	 * A string that uniquely identifies this manifest provider.
	 * This string should be usable in a filesystem path
	 *
	 * @return Name that identifies this manifest provider
	 */
	public abstract String getInternalName();

	protected final Path getSemverCachePath() {
		return GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", getInternalName()));
	}

	protected final void loadSemverCache() {
		if (semverCache == null) {
			Path cachePath = getSemverCachePath();
			if (Files.exists(cachePath)) {
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

	protected final void writeSemverCache() {
		Map<String, String> semverCache = new TreeMap<>(this.versionMeta.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().semanticVersion())));
		try {
			SerializationHelper.writeAllToPath(getSemverCachePath(), SerializationHelper.serialize(semverCache));
		} catch (IOException e) {
			MiscHelper.println("This is not a fatal error: %s", e);
		}
	}

	/**
	 * Lookup a semantic version string for a given version meta.
	 *
	 * @param versionMeta Version Meta to lookup semantic version for
	 * @return semantic version string
	 */
	protected abstract String lookupSemanticVersion(VersionMeta versionMeta);

	protected final List<Path> collectLocalExtraVersions() throws IOException {
		return MiscHelper.listDirectly(GitCraftPaths.SOURCE_EXTRA_VERSIONS.resolve(getInternalName())).stream().filter(path -> !path.toString().endsWith(".gitkeep")).collect(Collectors.toList()); // silently ignore gitkeep
	}

	/**
	 * @return A map containing all available versions, keyed by a unique name (id field of {@link com.github.winplay02.gitcraft.meta.VersionMeta)}).
	 */
	public final Map<String, OrderedVersion> getVersionMeta() throws IOException {
		if (this.versionMeta != null) {
			return Collections.unmodifiableMap(this.versionMeta);
		}
		this.versionMeta = new LinkedHashMap<>();
		MiscHelper.println("Loading available versions from '%s'...", getName());
		// Read all meta entries for each source
		for (DescribedURL metaEntry : this.manifestSourceUrls) {
			MiscHelper.println("Reading %s from %s...", metaEntry.description(), metaEntry.url());
			T launcherMeta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(metaEntry.url())), metaClass);
			// Load version corresponding to each meta entry
			unpackLauncherMeta(launcherMeta);
		}
		MiscHelper.println("Applying extra version from local (%s)...", GitCraftPaths.SOURCE_EXTRA_VERSIONS.resolve(getInternalName()));
		Files.createDirectories(GitCraftPaths.SOURCE_EXTRA_VERSIONS.resolve(getInternalName()));
		for (Path localExtraVersion : collectLocalExtraVersions()) {
			if (!localExtraVersion.toString().endsWith(".json")) {
				MiscHelper.println("Skipped extra version '%s' as it is not a .json file", localExtraVersion);
				continue;
			}
			OrderedVersion extra_version_object = loadVersionDataExtra(localExtraVersion, false);
			this.versionMeta.put(extra_version_object.launcherFriendlyVersionName(), extra_version_object);
			MiscHelper.println("Applied extra version '%s'", extra_version_object.launcherFriendlyVersionName());
		}
		if (!this.singleMetaUrls.isEmpty()) {
			MiscHelper.println("Applying extra version from remote...");
			Path metaExtraDownloadPaths = GitCraftPaths.MC_VERSION_META_DOWNLOADS.resolve(getInternalName());
			Files.createDirectories(metaExtraDownloadPaths);
			for (Artifact metaEntryExtra : this.singleMetaUrls) {
				Path srcPath = metaEntryExtra.resolve(metaExtraDownloadPaths);
				metaEntryExtra.fetchArtifact(metaExtraDownloadPaths);
				// Try ZIP
				try (FileSystem fs = FileSystems.newFileSystem(srcPath)) {
					for (Path potentialMetaFile : MiscHelper.listRecursivelyFilteredExtension(fs.getPath("."), ".json")) {
						OrderedVersion extra_version_object = loadVersionDataExtra(potentialMetaFile, true);
						this.versionMeta.put(extra_version_object.launcherFriendlyVersionName(), extra_version_object);
						MiscHelper.println("Applied version '%s'", extra_version_object.launcherFriendlyVersionName());
					}
					continue;
				} catch (IOException | ProviderNotFoundException ignored) {
				}
				// Try JSON
				try {
					OrderedVersion extra_version_object = loadVersionDataExtra(srcPath, true);
					this.versionMeta.put(extra_version_object.launcherFriendlyVersionName(), extra_version_object);
					MiscHelper.println("Applied version '%s'", extra_version_object.launcherFriendlyVersionName());
				} catch (JsonSyntaxException exception) {
					MiscHelper.println("Extra version at %s is neither a zip file nor a json file", metaEntryExtra.url());
				}
			}
		}
		writeSemverCache();
		return Collections.unmodifiableMap(this.versionMeta);
	}

	/**
	 * Fetch an {@link OrderedVersion} from a version entry contained in the launcher meta.
	 *
	 * @param launcherMetaEntry Launcher Meta Version Entry
	 * @return Ordered Version
	 * @throws IOException on failure
	 */
	protected abstract OrderedVersion fetchVersionMeta(M launcherMetaEntry) throws IOException;

	/**
	 * Check whether an existing (cached) version meta file is valid. This file was read previously from another meta source or a single file.
	 *
	 * @param launcherMetaEntry Launcher Meta Version Entry
	 * @return True if the read file is compatible with the new source provided by the passed launcher meta version entry, otherwise false.
	 */
	protected abstract boolean isExistingVersionMetaValid(M launcherMetaEntry) throws IOException;

	protected final void unpackLauncherMeta(T launcherMeta) throws IOException {
		for (M version : launcherMeta.versions()) {
			if (!versionMeta.containsKey(version.id())) {
				versionMeta.put(version.id(), fetchVersionMeta(version));
			} else {
				if (isExistingVersionMetaValid(version)) {
					MiscHelper.println("WARNING: Found duplicate version meta for version: %s (Matches previous entry)", version.id());
				} else {
					MiscHelper.panic("Found duplicate version meta for version: %s (Differs from previous)", version.id());
				}
			}
		}
	}

	protected final OrderedVersion loadVersionData(Path versionMeta, String versionId, String versionUrl, String versionSha1) throws IOException {
		RemoteHelper.downloadToFileWithChecksumIfNotExists(versionUrl, new RemoteHelper.LocalFileInfo(versionMeta, versionSha1, "version meta", versionId), RemoteHelper.SHA1);
		VersionMeta meta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(versionMeta), VersionMeta.class);
		String semver = this.lookupSemanticVersion(meta);
		return OrderedVersion.from(meta, semver);
	}

	protected final OrderedVersion loadVersionDataExtra(Path extraVersionMeta, boolean remote) throws IOException {
		VersionMeta meta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(extraVersionMeta), VersionMeta.class);
		Path versionMetaPath = this.rootPath.resolve(meta.id() + ".json");
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
		String semver = this.lookupSemanticVersion(meta);
		return OrderedVersion.from(meta, semver);
	}

	/**
	 * Finds parent nodes to the provided version. Used to construct the {@link com.github.winplay02.gitcraft.MinecraftVersionGraph}.
	 *
	 * @param mcVersion Subject version
	 * @return List of parent versions, or an empty list, if the provided version is the root version. {@code null} is returned, if the default ordering should be used (specified by {@link OrderedVersion})
	 */
	public abstract List<String> getParentVersion(OrderedVersion mcVersion);

	public final OrderedVersion getVersionByVersionID(String versionId) {
		try {
			return this.getVersionMeta().get(versionId);
		} catch (IOException e) {
			MiscHelper.panicBecause(e, "Could not fetch version information by id '%s'", versionId);
			return null;
		}
	}
}
