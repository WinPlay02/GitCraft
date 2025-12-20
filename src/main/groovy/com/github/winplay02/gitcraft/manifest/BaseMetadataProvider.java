package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.GitCraftApplication;
import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.LibraryPaths;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.github.winplay02.gitcraft.util.SerializationTypes;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class BaseMetadataProvider<M extends VersionsManifest<E>, E extends VersionsManifest.VersionEntry> implements MetadataProvider<OrderedVersion> {
	protected final Path manifestMetadata;
	protected final Path remoteMetadata;
	protected final Path localMetadata;
	protected final List<MetadataSources.RemoteVersionsManifest<M, E>> manifestSources;
	protected final List<MetadataSources.RemoteMetadata<E>> metadataSources;
	protected final List<MetadataSources.LocalRepository> repositorySources;
	protected final LinkedHashMap<String, OrderedVersion> versionsById = new LinkedHashMap<>();
	protected final TreeMap<String, String> semverCache = new TreeMap<>();
	protected final boolean singleSideVersionsOnMainBranch = GitCraftApplication.getApplicationConfiguration().singleSideVersionsOnMainBranch();
	protected boolean versionsLoaded;

	protected BaseMetadataProvider() {
		this.manifestMetadata = GitCraftPipelineFilesystemRoot.getMcMetaStore().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(this.getInternalName());
		this.remoteMetadata = GitCraftPipelineFilesystemRoot.getMcMetaDownloads().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(this.getInternalName());
		this.localMetadata = GitCraftPipelineFilesystemRoot.getMcExtraVersionStore().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(this.getInternalName());
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
		return LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", this.getInternalName()));
	}

	protected final void loadSemverCache() {
		Path cachePath = this.getSemverCachePath();
		if (Files.exists(cachePath)) {
			try {
				this.semverCache.putAll(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(cachePath), SerializationTypes.TYPE_TREE_MAP_STRING_STRING));
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
	 * Loads versions using given executor if they were not already loaded. Executor must be not <c>null</c>.
	 * @return A map containing all available versions, keyed by a unique name (see {@linkplain VersionInfo#id()}).
	 */
	@Override
	public final Map<String, OrderedVersion> getVersions(Executor executor) throws IOException {
		this.initializeAndLoadVersions(executor);
		return Collections.unmodifiableMap(this.versionsById);
	}

	/**
	 * When calling the versions must be already loaded. Crashes if not.
	 * @return A map containing all available versions, keyed by a unique name (see {@linkplain VersionInfo#id()}).
	 */
	@Override
	public final Map<String, OrderedVersion> getVersionsAssumeLoaded() {
		if (!this.versionsLoaded) {
			MiscHelper.panic("getVersionsAssumeLoaded() called but the versions are not loaded");
		}
		return Collections.unmodifiableMap(this.versionsById);
	}

	public final void initializeAndLoadVersions(Executor executor) throws IOException {
		synchronized (this) {
			if (!this.versionsLoaded) {
				if (executor == null) {
					MiscHelper.panic("Cannot load versions because provided executor is null");
				}
				this.loadVersions(executor);
				this.postLoadVersions();
				this.writeSemverCache();
				this.versionsLoaded = true;
			}
		}
	}

	protected void loadVersions(Executor executor) throws IOException {
		this.versionsById.clear();
		MiscHelper.println("Loading available versions from '%s'...", this.getName());
		for (MetadataSources.RemoteVersionsManifest<M, E> manifestSource : this.manifestSources) {
			MiscHelper.println("Reading versions manifest from %s...", manifestSource.url());
			M manifest = this.fetchVersionsManifest(manifestSource);
			Map<String, CompletableFuture<OrderedVersion>> futureVersions = new HashMap<>();
			for (E versionEntry : manifest.versions()) {
				if (!this.versionsById.containsKey(versionEntry.id()) && !futureVersions.containsKey(versionEntry.id())) {
					futureVersions.put(versionEntry.id(), this.loadVersionFromManifest(executor, versionEntry, this.manifestMetadata));
				} else {
					if (this.isExistingVersionMetadataValid(versionEntry, this.manifestMetadata)) {
						MiscHelper.println("WARNING: Found duplicate manifest version entry: %s (Matches previous entry)", versionEntry.id());
					} else {
						MiscHelper.panic("Found duplicate manifest version entry: %s (Differs from previous)", versionEntry.id());
					}
				}
			}
			CompletableFuture.allOf(futureVersions.values().toArray(CompletableFuture[]::new)).join();
			for(Map.Entry<String, CompletableFuture<OrderedVersion>> idVersion : futureVersions.entrySet()) {
				OrderedVersion version = idVersion.getValue().getNow(null);
				if (!this.shouldExclude(version)) {
					this.versionsById.put(idVersion.getKey(), version);
				}
			}
		}
		for (MetadataSources.RemoteMetadata<E> metadataSource : this.metadataSources) {
			MiscHelper.println("Reading extra metadata for %s...", metadataSource.versionEntry().id());
			E versionEntry = metadataSource.versionEntry();
			if (!this.versionsById.containsKey(versionEntry.id())) {
				OrderedVersion version = this.loadVersionFromManifest(executor, versionEntry, this.remoteMetadata).join();
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
			this.loadVersionsFromRepository(executor, dir, mcVersion -> {
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
	}

	protected void postLoadVersions() {
	}

	private final M fetchVersionsManifest(MetadataSources.RemoteVersionsManifest<M, E> manifestSource) throws IOException {
		try {
			return SerializationHelper.deserialize(FileSystemNetworkManager.fetchAllFromURLSync(new URI(manifestSource.url()).toURL()), manifestSource.manifestClass());
		} catch (MalformedURLException | URISyntaxException | InterruptedException e) {
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
	protected abstract CompletableFuture<OrderedVersion> loadVersionFromManifest(Executor executor, E manifestEntry, Path targetDir) throws IOException;

	/**
	 * Fetch all the metadata provided by this repository and pass them to the given version loader.
	 */
	protected abstract void loadVersionsFromRepository(Executor executor, Path dir, Consumer<OrderedVersion> loader) throws IOException;

	/**
	 * @return The highest number of concurrent HTTP requests this provider supports, -1 if not limited.
	 */
	public int getConcurrentRequestLimit() {
		return -1;
	}

	protected final <T> CompletableFuture<T> fetchVersionMetadata(Executor executor, String id, String url, String sha1, Path targetDir, String targetFileKind, Class<T> metadataClass) throws IOException {
		URI uri = null;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		String fileName = url.substring(url.lastIndexOf('/') + 1);
		String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
		String fileExt = fileName.lastIndexOf(".") != -1 ? fileName.substring(fileName.lastIndexOf(".") + 1) : "";
		Path filePath = sha1 != null ? targetDir.resolve(String.format("%s_%s.%s", fileNameWithoutExt, sha1, fileExt)) : targetDir.resolve(fileName); // allow multiple files with same hash to coexist (in case of reuploads with same meta name, referenced from different versions)
		CompletableFuture<StepStatus> status = FileSystemNetworkManager.fetchRemoteSerialFSAccess(executor, uri, new FileSystemNetworkManager.LocalFileInfo(filePath, sha1, sha1 != null ? Library.IA_SHA1 : null, targetFileKind, id), true, false, this.getConcurrentRequestLimit());
		return status.thenApply($ -> {
			try {
				return this.loadVersionMetadata(filePath, metadataClass, fileName);
			} catch (Exception e) {
				MiscHelper.panicBecause(e, "Error while fetching version metadata");
			}
			return null;
		});
	}

	protected final <T> CompletableFuture<T> fetchVersionMetadataFilename(Executor executor, String filename, String id, String url, String sha1, Path targetDir, String targetFileKind, Class<T> metadataClass) throws IOException {
		URI uri = null;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		Path filePath = targetDir.resolve(filename);
		CompletableFuture<StepStatus> status = FileSystemNetworkManager.fetchRemoteSerialFSAccess(executor, uri, new FileSystemNetworkManager.LocalFileInfo(filePath, sha1, sha1 != null ? Library.IA_SHA1 : null, targetFileKind, id), true, false, this.getConcurrentRequestLimit());
		return status.thenApply($ -> {
			try {
				return this.loadVersionMetadata(filePath, metadataClass, filePath.getFileName().toString());
			} catch (Exception e) {
				MiscHelper.panicBecause(e, "Error while fetching version metadata");
			}
			return null;
		});
	}

	protected final <T> T loadVersionMetadata(Path targetFile, Class<T> metadataClass, String originalFileName) throws IOException {
		String fileName = targetFile.getFileName().toString();
		if (!fileName.endsWith(".json")) {
			if (fileName.endsWith(".zip")) {
				originalFileName = originalFileName.substring(0, originalFileName.length() - ".zip".length()) + ".json";
				try (FileSystem fs = FileSystems.newFileSystem(targetFile)) {
					Optional<Path> zipFile = MiscHelper.findRecursivelyByName(fs.getPath("."), originalFileName);
					if (zipFile.isPresent()) {
						return this.loadVersionMetadata(zipFile.get(), metadataClass, originalFileName);
					} else {
						MiscHelper.panic("cannot find metadata file json (%s) inside %s", originalFileName, targetFile);
					}
				}
			} else {
				MiscHelper.panic("unknown file extension for metadata file %s", targetFile);
			}
		}
		T metadata = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(targetFile), metadataClass);
		if (metadata == null) {
			MiscHelper.panic("unable to load metadata type %s from file %s", metadataClass.getName(), targetFile);
		}
		return metadata;
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
		return Files.exists(filePath) && (sha1 == null || Library.IA_SHA1.fileMatchesChecksum(filePath, sha1));
	}

	@Override
	public final OrderedVersion getVersionByVersionID(String versionId) {
		return this.getVersionsAssumeLoaded().get(versionId);
	}

	@Override
	public boolean shouldExclude(OrderedVersion mcVersion) {
		return false;
	}

	@Override
	public boolean shouldExcludeFromMainBranch(OrderedVersion mcVersion) {
		return mcVersion.isPending() || (!this.singleSideVersionsOnMainBranch && mcVersion.hasSideMissing());
	}
}
