package com.github.winplay02.gitcraft.manifest.skyrising;

import com.github.winplay02.gitcraft.manifest.DescribedURL;
import com.github.winplay02.gitcraft.manifest.ManifestProvider;
import com.github.winplay02.gitcraft.meta.VersionMeta;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SkyrisingManifest extends ManifestProvider<SkyrisingMeta, SkyrisingMeta.SkyrisingVersionEntry> {

	private static final Map<String, String> invalidAcceptedSha1Hashes = Map.of(
		"https://skyrising.github.io/mc-versions/manifest/e/8/4ab19484489434ba6e28c8e8f3acca4551f58c/b1.9-pre4-1440.json", "8dabc17ac6ef3938c98878fb15bffc900bef4221"
	);
	Path detailsRoot;

	Map<OrderedVersion, SkyrisingVersionDetails> versionDetailsMap = new HashMap<>();
	Map<String, String> versionToSemverOfficial = new HashMap<>();
	Map<String, String> versionToSemverSkyrising = new HashMap<>();

	public SkyrisingManifest() {
		super(new DescribedURL[]{new DescribedURL("https://skyrising.github.io/mc-versions/version_manifest.json", "Launcher Meta")}, SkyrisingMeta.class);
		this.detailsRoot = this.rootPath.resolve("details");
		try {
			Files.createDirectories(this.detailsRoot);
		} catch (IOException e) {
			MiscHelper.panicBecause(e, "Could not create details directory for skyrising version meta");
		}
	}

	@Override
	public String getName() {
		return "Skyrising Version Meta (https://skyrising.github.io/mc-versions/)";
	}

	@Override
	public String getInternalName() {
		return "skyrising";
	}

	@Override
	protected String lookupSemanticVersion(VersionMeta versionMeta) {
		return this.versionToSemverOfficial.get(versionMeta.id());
	}

	private Path getPathOfVersionDetailsMeta(SkyrisingMeta.SkyrisingVersionEntry launcherMetaEntry) {
		return this.detailsRoot.resolve(launcherMetaEntry.id() + ".json");
	}

	private Path getPathOfVersionMeta(SkyrisingVersionDetails versionDetails, List<SkyrisingVersionDetails.SkyrisingVersionDetailsManifest> manifestDetails) {
		return this.rootPath.resolve(String.format("%s-%s.json", versionDetails.id(), Stream.concat(manifestDetails.stream().map(SkyrisingVersionDetails.SkyrisingVersionDetailsManifest::hash), Optional.ofNullable(manifestDetails.size() > 1 ? "merged" : null).stream()).collect(Collectors.joining("-"))));
	}

	private SkyrisingVersionDetails getVersionDetailsForVersion(SkyrisingMeta.SkyrisingVersionEntry launcherMetaEntry) throws IOException {
		Path detailsPath = getPathOfVersionDetailsMeta(launcherMetaEntry);
		// API rate limit may be a problem here for access with checksum verification
		// RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryGitHub("skyrising/mc-versions", "main", String.format("data/version/%s.json", launcherMetaEntry.id()), new RemoteHelper.LocalFileInfo(detailsPath, null, "skyrising details file", launcherMetaEntry.id()));
		RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetry(launcherMetaEntry.details(), new RemoteHelper.LocalFileInfo(detailsPath, null, "skyrising details file", launcherMetaEntry.id()), null);
		SkyrisingVersionDetails versionDetails = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(detailsPath), SkyrisingVersionDetails.class);
		this.versionToSemverSkyrising.put(launcherMetaEntry.id(), versionDetails.normalizedVersion());
		return versionDetails;
	}

	private String getHashForURLWithQuirks(String versionMetaUrl, String proposedHash) {
		if (invalidAcceptedSha1Hashes.containsKey(versionMetaUrl)) {
			return invalidAcceptedSha1Hashes.get(versionMetaUrl);
		}
		return proposedHash;
	}

	@Override
	protected OrderedVersion fetchVersionMeta(SkyrisingMeta.SkyrisingVersionEntry launcherMetaEntry) throws IOException {
		SkyrisingVersionDetails versionDetails = getVersionDetailsForVersion(launcherMetaEntry);
		List<SkyrisingVersionDetails.SkyrisingVersionDetailsManifest> oldestManifests = versionDetails.getOldestManifests();
		List<Path> manifestPaths = new ArrayList<>(oldestManifests.size());

		for (SkyrisingVersionDetails.SkyrisingVersionDetailsManifest oldestManifest : oldestManifests) {
			Path versionManifestPath = getPathOfVersionMeta(versionDetails, List.of(oldestManifest));
			manifestPaths.add(versionManifestPath);
			String uri = oldestManifest.targetURI().toString();
			RemoteHelper.downloadToFileWithChecksumIfNotExists(uri, new RemoteHelper.LocalFileInfo(versionManifestPath, getHashForURLWithQuirks(uri, oldestManifest.hash()), "version meta", versionDetails.id()), RemoteHelper.SHA1);
		}
		Path versionManifestPathMerged = getPathOfVersionMeta(versionDetails, oldestManifests);
		if (oldestManifests.size() > 1) {
			// Merge multiple manifests
			List<VersionMeta> metaList = new ArrayList<>(oldestManifests.size());
			for (final Path path : manifestPaths) {
				metaList.add(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(path), VersionMeta.class));
			}
			VersionMeta mergedMeta = VersionMeta.merge(versionDetails.id(), metaList); // Version ID provided here as there may be mismatches between multiple manifests of the same version
			SerializationHelper.writeAllToPath(versionManifestPathMerged, SerializationHelper.serialize(mergedMeta));
		}
		VersionMeta meta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(versionManifestPathMerged), VersionMeta.class);
		this.versionToSemverOfficial.put(meta.id(), versionDetails.normalizedVersion());
		String semver = lookupSemanticVersion(meta);
		OrderedVersion orderedVersion = OrderedVersion.from(meta, semver);
		this.versionDetailsMap.put(orderedVersion, versionDetails);
		return orderedVersion;
	}

	@Override
	protected boolean isExistingVersionMetaValid(SkyrisingMeta.SkyrisingVersionEntry launcherMetaEntry) throws IOException {
		SkyrisingVersionDetails versionDetails = getVersionDetailsForVersion(launcherMetaEntry);
		List<SkyrisingVersionDetails.SkyrisingVersionDetailsManifest> oldestManifests = versionDetails.getOldestManifests();
		return oldestManifests.stream().map(oldestManifest -> RemoteHelper.SHA1.fileMatchesChecksum(getPathOfVersionMeta(versionDetails, List.of(oldestManifest)), oldestManifest.hash())).reduce(Boolean::logicalAnd).orElse(true) && Files.exists(getPathOfVersionMeta(versionDetails, oldestManifests));
	}

	@Override
	public List<String> getParentVersion(OrderedVersion mcVersion) {
		List<String> parents = this.versionDetailsMap.get(mcVersion).previous().stream().map(idVersion ->
			this.versionToSemverSkyrising.get(idVersion)).filter(Objects::nonNull).toList();
		return parents;
	}
}
