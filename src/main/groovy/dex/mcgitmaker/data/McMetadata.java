package dex.mcgitmaker.data;

import com.github.winplay02.MiscHelper;
import com.github.winplay02.meta.ArtifactMeta;
import com.github.winplay02.meta.AssetsIndexMeta;
import com.github.winplay02.meta.LauncherMeta;
import com.github.winplay02.meta.LibraryMeta;
import com.github.winplay02.SerializationHelper;
import com.github.winplay02.RemoteHelper;
import com.github.winplay02.meta.VersionMeta;
import com.google.gson.reflect.TypeToken;
import dex.mcgitmaker.GitCraft;
import groovy.lang.Tuple2;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class McMetadata {

	public final LinkedHashMap<String, McVersion> metadata;

	public McMetadata() throws IOException {
		metadata = getInitialMetadata();
	}

	McVersion getVersion(String name) {
		return metadata.get(name);
	}

	McVersion getVersionFromSemver(String version) {
		return metadata.values().stream().filter(mcVersion -> Objects.equals(mcVersion.loaderVersion, version)).findFirst().orElse(null);
	}

	static Path getMcArtifactRootPath(String version) {
		return GitCraft.MC_VERSION_STORE.resolve(version);
	}

	private static LinkedHashMap<String, McVersion> getInitialMetadata() throws IOException {
		MiscHelper.println("Creating metadata...");
		MiscHelper.println("Reading metadata from Mojang...");
		LauncherMeta mcLauncherVersions = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(RemoteHelper.MINECRAFT_MAIN_META_URL)), LauncherMeta.class);
		MiscHelper.println("Attempting to read metadata from file...");
		LinkedHashMap<String, McVersion> versionMeta = new LinkedHashMap<>();
		// Read stored versions
		if (GitCraft.METADATA_STORE.toFile().exists()) {
			versionMeta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(GitCraft.METADATA_STORE), SerializationHelper.TYPE_LINKED_HASH_MAP_STRING_VERSION);
		}
		// Add unknown versions
		for (LauncherMeta.LauncherVersionEntry version : mcLauncherVersions.versions()) {
			if (!versionMeta.containsKey(version.id())) {
				versionMeta.put(version.id(), createVersionDataFromLauncherMeta(version.id(), version.url(), version.sha1()));
			}
		}
		// Import extra verions
		for (File extra_version : Objects.requireNonNull(GitCraft.SOURCE_EXTRA_VERSIONS.toFile().listFiles())) {
			if (extra_version.getName().equals(".gitkeep")) { // silently ignore gitkeep
				continue;
			}
			if (!extra_version.toPath().toString().endsWith(".json")) {
				MiscHelper.println("Skipped extra version '%s' as it is not a .json file", extra_version.toPath());
				continue;
			}
			McVersion extra_version_object = createVersionDataFromExtra(extra_version.toPath(), versionMeta);
			if (extra_version_object != null) {
				versionMeta.put(extra_version_object.version, extra_version_object);
				MiscHelper.println("Applied extra version '%s'", extra_version_object.version);
			}
		}
		return versionMeta;
	}

	private static McVersion createVersionDataFromExtra(Path pExtraFile, LinkedHashMap<String, McVersion> dataVersions) throws IOException {
		VersionMeta meta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(pExtraFile), VersionMeta.class);
		if (dataVersions.containsKey(meta.id())) {
			return null;
		}
		return createVersionData(meta, pExtraFile, null);
	}

	private static McVersion createVersionDataFromLauncherMeta(String metaID, String metaURL, String metaSha1) throws IOException {
		Path metaFile = GitCraft.META_CACHE.resolve(metaID + ".json");
		RemoteHelper.downloadToFileWithChecksumIfNotExists(metaURL, metaFile, metaSha1, "version meta", metaID);
		VersionMeta meta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(metaFile), VersionMeta.class);
		return createVersionData(meta, metaFile, metaSha1);
	}

	private static McVersion createVersionData(VersionMeta meta, Path sourcePath, String metaSha1) throws IOException {
		Set<Artifact> libs = new HashSet<>();
		// Ignores natives, not needed as we don't have a runtime
		for (LibraryMeta library : meta.libraries()) {
			ArtifactMeta artifactMeta = library.downloads().artifact();
			if (artifactMeta != null) {
				libs.add(new Artifact(artifactMeta.url(), Artifact.nameFromUrl(artifactMeta.url()), GitCraft.LIBRARY_STORE, artifactMeta.sha1()));
			}
		}
		// Fetch Assets index
		fetchAssetsIndexByMeta(meta);

		int javaVersion = meta.javaVersion() != null ? meta.javaVersion().majorVersion() : 8;
		McArtifacts artifacts = getMcArtifacts(meta);

		if (artifacts.hasMappings()) {
			getMcArtifactRootPath(meta.id()).toFile().mkdirs();
			Path targetPath = getMcArtifactRootPath(meta.id()).resolve("version.json");
			Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			if (!RemoteHelper.checksumCheckFileIsValidAndExists(targetPath.toFile(), metaSha1, "stored version meta", meta.id(), false)) {
				throw new RuntimeException("A valid stored version meta for %s does not exist".formatted(meta.id()));
			}
		}

		return new McVersion(meta.id(), null, Objects.equals(meta.type(), "snapshot") || Objects.equals(meta.type(), "pending"), artifacts.hasMappings(), javaVersion, artifacts, libs, meta.mainClass(), null, meta.time(), meta.assets());
	}

	private static AssetsIndexMeta fetchAssetsIndex(String assetsId, String url, String sha1Hash) throws IOException {
		File targetFile = GitCraft.ASSETS_INDEX.resolve(assetsId + ".json").toFile();

		if (!RemoteHelper.checksumCheckFileIsValidAndExists(targetFile, sha1Hash, "assets index", assetsId, false) && url == null) {
			throw new RuntimeException("assets index %s is expected to be already downloaded but it is missing".formatted(assetsId));
		}

		RemoteHelper.downloadToFileWithChecksumIfNotExists(url, targetFile.toPath(), sha1Hash, "assets index", assetsId);

		return SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(targetFile.toPath()), AssetsIndexMeta.class);
	}

	private static void fetchAssetsIndexByMeta(VersionMeta meta) throws IOException {
		fetchAssetsIndex(meta.id() + "_" + meta.assets(), meta.assetIndex().url(), meta.assetIndex().sha1());
	}

	public static AssetsIndexMeta fetchAssetsOnly(McVersion version) throws IOException {
		AssetsIndexMeta assetsIndex = fetchAssetsIndex(version.version + "_" + version.assets_index, null, null);
		// Fetch Assets
		for (AssetsIndexMeta.AssetsIndexEntry info : assetsIndex.objects().values()) {
			new Artifact(RemoteHelper.makeAssetUrl(info.hash()), info.hash(), GitCraft.ASSETS_OBJECTS, info.hash()).fetchArtifact();
		}
		return assetsIndex;
	}

	public static void copyExternalAssetsToRepo(McVersion version, Path dest_root) throws IOException {
		AssetsIndexMeta assetsIndex = fetchAssetsOnly(version);
		// Copy Assets
		Path targetRoot = dest_root.resolve("minecraft").resolve("external-resources").resolve("assets");
		if(GitCraft.config.useHardlinks) {
			for (Map.Entry<String, AssetsIndexMeta.AssetsIndexEntry> entry : assetsIndex.objects().entrySet()) {
				Path sourcePath = GitCraft.ASSETS_OBJECTS.resolve(entry.getValue().hash());
				Path targetPath = targetRoot.resolve(entry.getKey());
				targetPath.getParent().toFile().mkdirs();
				Files.createLink(targetPath, sourcePath);
			}
		} else {
			for (Map.Entry<String, AssetsIndexMeta.AssetsIndexEntry> entry : assetsIndex.objects().entrySet()) {
				Path sourcePath = GitCraft.ASSETS_OBJECTS.resolve(entry.getValue().hash());
				Path targetPath = targetRoot.resolve(entry.getKey());
				targetPath.getParent().toFile().mkdirs();
				Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private static McArtifacts getMcArtifacts(VersionMeta meta) {
		Tuple2<Path, String> client = getMcArtifactData(meta.id(), meta.downloads().client().url());
		String cmUrl = meta.downloads().client_mappings() != null ? meta.downloads().client_mappings().url() : "";
		String cmSha1 = meta.downloads().client_mappings() != null ? meta.downloads().client_mappings().sha1() : null;
		String smUrl = meta.downloads().server_mappings() != null ? meta.downloads().server_mappings().url() : "";
		String smSha1 = meta.downloads().server_mappings() != null ? meta.downloads().server_mappings().sha1() : null;
		Tuple2<Path, String> client_mapping = getMcArtifactData(meta.id(), cmUrl);
		Tuple2<Path, String> server = getMcArtifactData(meta.id(), meta.downloads().server() != null ? meta.downloads().server().url() : "");
		Tuple2<Path, String> server_mapping = getMcArtifactData(meta.id(), smUrl);

		Artifact clientArtifact = new Artifact(meta.downloads().client().url(), client.getV2(), client.getV1(), meta.downloads().client().sha1());
		Artifact clientMappingArtifact = new Artifact(cmUrl, client_mapping.getV2(), client_mapping.getV1(), cmSha1);
		Artifact serverArtifact = new Artifact(meta.downloads().server() != null ? meta.downloads().server().url() : "", server.getV2(), server.getV1(), meta.downloads().server() != null ? meta.downloads().server().sha1() : "");
		Artifact serverMappingArtifact = new Artifact(smUrl, server_mapping.getV2(), server_mapping.getV1(), smSha1);

		boolean hasMappings = !Objects.equals(cmUrl, "") && !Objects.equals(smUrl, "");
		return new McArtifacts(clientArtifact, clientMappingArtifact, serverArtifact, serverMappingArtifact, hasMappings);
	}

	// containing path, file name
	private static Tuple2<Path, String> getMcArtifactData(String version, String url) {
		return new Tuple2<>(getMcArtifactRootPath(version), Artifact.nameFromUrl(url));
	}
}
