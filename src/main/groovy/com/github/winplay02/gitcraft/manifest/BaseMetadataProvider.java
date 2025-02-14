package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class BaseMetadataProvider<M extends VersionsManifest<E>, E extends VersionsManifest.VersionEntry> implements MetadataProvider {
	protected final Path manifestMetadata;
	protected final Path remoteMetadata;
	protected final Path localMetadata;
	protected final List<MetadataSources.RemoteVersionsManifest<M, E>> manifestSources;
	protected final List<MetadataSources.RemoteMetadata<E>> metadataSources;
	protected final List<MetadataSources.LocalRepository> repositorySources;
	protected final LinkedHashMap<String, OrderedVersion> versionsById = new LinkedHashMap<>();
	protected final TreeMap<String, String> semverCache = new TreeMap<>();
	private boolean versionsLoaded;

	protected BaseMetadataProvider() {
		this.manifestMetadata = GitCraftPaths.MC_VERSION_META_STORE.resolve(this.getInternalName());
		this.remoteMetadata = GitCraftPaths.MC_VERSION_META_DOWNLOADS.resolve(this.getInternalName());
		this.localMetadata = GitCraftPaths.SOURCE_EXTRA_VERSIONS.resolve(this.getInternalName());
		this.manifestSources = new ArrayList<>();
		this.metadataSources = new ArrayList<>();
		this.repositorySources = new ArrayList<>();
		this.loadSemverCache();
	}

	protected void addManifestSource(String url, Class<M> manifestClass) {
		this.manifestSources.add(MetadataSources.RemoteVersionsManifest.of(url, manifestClass));
	}

	protected void addMetadataSource(E manifestEntry) {
		this.metadataSources.add(MetadataSources.RemoteMetadata.of(manifestEntry));
	}

	protected void addMetadataRepository(String directory) {
		this.repositorySources.add(MetadataSources.LocalRepository.of(this.localMetadata.resolve(directory)));
	}

	protected final Path getSemverCachePath() {
		return GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", this.getInternalName()));
	}

	protected final void loadSemverCache() {
		Path cachePath = this.getSemverCachePath();
		if (Files.exists(cachePath)) {
			try {
				this.semverCache.putAll(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(cachePath), SerializationHelper.TYPE_TREE_MAP_STRING_STRING));
			} catch (IOException e) {
				MiscHelper.println("This is not a fatal error: %s", e);
			}
		}
	}

	protected final void writeSemverCache() {
		Map<String, String> semverCache = new TreeMap<>(this.versionsById.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().semanticVersion())));
		try {
			SerializationHelper.writeAllToPath(this.getSemverCachePath(), SerializationHelper.serialize(semverCache));
		} catch (IOException e) {
			MiscHelper.println("This is not a fatal error: %s", e);
		}
	}

	/**
	 * @return A map containing all available versions, keyed by a unique name (see {@linkplain VersionInfo#id VersionInfo.id}).
	 */
	@Override
	public final Map<String, OrderedVersion> getVersions() throws IOException {
		if (!this.versionsLoaded) {
			this.loadVersions();
			this.postLoadVersions();
			this.writeSemverCache();
		}
		return Collections.unmodifiableMap(this.versionsById);
	}

	private void loadVersions() throws IOException {
		this.versionsById.clear();
		MiscHelper.println("Loading available versions from '%s'...", this.getName());
		for (MetadataSources.RemoteVersionsManifest<M, E> manifestSource : this.manifestSources) {
			MiscHelper.println("Reading versions manifest from %s...", manifestSource.url());
			M manifest = this.fetchVersionsManifest(manifestSource);
			for (E versionEntry : manifest.versions()) {
				if (!this.versionsById.containsKey(versionEntry.id())) {
					OrderedVersion version = this.loadVersionFromManifest(versionEntry, this.manifestMetadata);
					if (!this.shouldExclude(version)) {
						this.versionsById.put(versionEntry.id(), version);
					}
				} else {
					if (this.isExistingVersionMetadataValid(versionEntry, this.manifestMetadata)) {
						MiscHelper.println("WARNING: Found duplicate manifest version entry: %s (Matches previous entry)", versionEntry.id());
					} else {
						MiscHelper.panic("Found duplicate manifest version enryt: %s (Differs from previous)", versionEntry.id());
					}
				}
			}
		}
		for (MetadataSources.RemoteMetadata<E> metadataSource : this.metadataSources) {
			MiscHelper.println("Reading extra metadata for %s...", metadataSource.versionEntry().id());
			E versionEntry = metadataSource.versionEntry();
			if (!this.versionsById.containsKey(versionEntry.id())) {
				OrderedVersion version = this.loadVersionFromManifest(versionEntry, this.remoteMetadata);
				if (!this.shouldExclude(version)) {
					this.versionsById.put(versionEntry.id(), version);
				}
			} else {
				MiscHelper.panic("Found duplicate extra version entry: %s (Differs from previous)", versionEntry.id());
			}
		}
		for (MetadataSources.LocalRepository repository : this.repositorySources) {
			MiscHelper.println("Reading extra metadata repository from %s...", repository.directory());
			Path dir = repository.directory();
			this.loadVersionsFromRepository(dir, mcVersion -> {
				if (!this.shouldExclude(mcVersion)) {
					String versionId = mcVersion.launcherFriendlyVersionName();
					if (!this.versionsById.containsKey(versionId)) {
						this.versionsById.put(versionId, mcVersion);
					} else {
						MiscHelper.panic("Found duplicate repository version entry: %s", versionId);
					}
				}
			});
		}
		this.versionsLoaded = true;
	}

	protected void postLoadVersions() {
	}

	private final M fetchVersionsManifest(MetadataSources.RemoteVersionsManifest<M, E> manifestSource) throws IOException {
		try {
			return SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URI(manifestSource.url()).toURL()), manifestSource.manifestClass());
		} catch (MalformedURLException | URISyntaxException e) {
			throw new IOException("unable to fetch versions manifest", e);
		}
	}

	/**
	 * Fetch all the metadata provided by this manifest entry and return an {@link OrderedVersion}
	 * representing this version.
	 *
	 * @param manifestEntry   Version Entry from Versions Manifest
	 * @return OrderedVersion Version
	 * @throws IOException on failure
	 */
	protected abstract OrderedVersion loadVersionFromManifest(E manifestEntry, Path targetDir) throws IOException;

	/**
	 * Fetch all the metadata provided by this repository and pass them to the given version loader.
	 */
	protected abstract void loadVersionsFromRepository(Path dir, Consumer<OrderedVersion> loader) throws IOException;

	protected final <T> T fetchVersionMetadata(String id, String url, String sha1, Path targetDir, String targetFileKind, Class<T> metadataClass) throws IOException {
		String fileName = url.substring(url.lastIndexOf('/') + 1);
		Path filePath = targetDir.resolve(fileName);
		RemoteHelper.downloadToFileWithChecksumIfNotExists(url, new RemoteHelper.LocalFileInfo(filePath, sha1, targetFileKind, id), RemoteHelper.SHA1);
		return this.loadVersionMetadata(filePath, metadataClass);
	}

	protected final <T> T loadVersionMetadata(Path targetFile, Class<T> metadataClass) throws IOException {
		String fileName = targetFile.getFileName().toString();
		if (!fileName.endsWith(".json")) {
			if (fileName.endsWith(".zip")) {
				fileName = fileName.substring(0, fileName.length() - ".zip".length()) + ".json";
				try (FileSystem fs = FileSystems.newFileSystem(targetFile)) {
					Optional<Path> zipFile = MiscHelper.findRecursivelyByName(fs.getPath("."), fileName);
					if (zipFile.isPresent()) {
						return this.loadVersionMetadata(zipFile.get(), metadataClass);
					}
				}
			} else {
				MiscHelper.panic("unknown metadata file extension: %s", targetFile);
			}
		}
		return SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(targetFile), metadataClass);
	}

	/**
	 * Check whether existing (cached) version metadata files are valid.
	 * Check whether an existing (cached) version meta file is valid. This file was read previously from another meta source or a single file.
	 *
	 * @param manifestEntry Version Entry from Versions Manifest
	 * @return True if the read file is compatible with the new source provided by the passed launcher meta version entry, otherwise false.
	 */
	protected abstract boolean isExistingVersionMetadataValid(E manifestEntry, Path targetDir) throws IOException;

	protected final boolean isExistingVersionMetadataValid(String id, String url, String sha1, Path targetDir) throws IOException {
		String fileName = url.substring(url.lastIndexOf('/') + 1);
		Path filePath = targetDir.resolve(fileName);
		return Files.exists(filePath) && (sha1 == null || RemoteHelper.SHA1.fileMatchesChecksum(filePath, sha1));
	}

	@Override
	public final OrderedVersion getVersionByVersionID(String versionId) {
		try {
			return this.getVersions().get(versionId);
		} catch (Exception e) {
			MiscHelper.panicBecause(e, "Could not fetch version information by id '%s'", versionId);
			return null;
		}
	}

	@Override
	public boolean shouldExclude(OrderedVersion mcVersion) {
		return false;
	}

	@Override
	public boolean shouldExcludeFromMainBranch(OrderedVersion mcVersion) {
		return mcVersion.isPending();
	}
}
