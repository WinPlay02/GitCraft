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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SkyrisingManifest extends ManifestProvider<SkyrisingMeta, SkyrisingMeta.SkyrisingVersionEntry> {
	Path detailsRoot;

	Map<String, SkyrisingVersionDetails> versionDetailsMap = new HashMap<>();

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
		return this.versionDetailsMap.get(versionMeta.id()).normalizedVersion();
	}

	private Path getPathOfVersionDetailsMeta(SkyrisingMeta.SkyrisingVersionEntry launcherMetaEntry) {
		return this.detailsRoot.resolve(launcherMetaEntry.id() + ".json");
	}

	private Path getPathOfVersionMeta(SkyrisingVersionDetails versionDetails, SkyrisingVersionDetails.SkyrisingVersionDetailsManifest manifestDetails) {
		return this.rootPath.resolve(String.format("%s-%s.json", versionDetails.id(), manifestDetails.hash()));
	}

	private SkyrisingVersionDetails getVersionDetailsForVersion(SkyrisingMeta.SkyrisingVersionEntry launcherMetaEntry) throws IOException {
		Path detailsPath = getPathOfVersionDetailsMeta(launcherMetaEntry);
		RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryGitHub("skyrising/mc-versions", "main", String.format("data/version/%s", launcherMetaEntry.id()), new RemoteHelper.LocalFileInfo(detailsPath, null, "skyrising details file", launcherMetaEntry.id()));
		SkyrisingVersionDetails versionDetails = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(detailsPath), SkyrisingVersionDetails.class);
		this.versionDetailsMap.put(launcherMetaEntry.id(), versionDetails);
		return versionDetails;
	}

	@Override
	protected OrderedVersion fetchVersionMeta(SkyrisingMeta.SkyrisingVersionEntry launcherMetaEntry) throws IOException {
		SkyrisingVersionDetails versionDetails = getVersionDetailsForVersion(launcherMetaEntry);
		SkyrisingVersionDetails.SkyrisingVersionDetailsManifest oldestManifest = versionDetails.getOldestManifest();
		Path versionManifestPath = getPathOfVersionMeta(versionDetails, oldestManifest);
		RemoteHelper.downloadToFileWithChecksumIfNotExists(oldestManifest.url(), new RemoteHelper.LocalFileInfo(versionManifestPath, oldestManifest.hash(), "version meta", versionDetails.id()), RemoteHelper.SHA1);
		VersionMeta meta = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(versionManifestPath), VersionMeta.class);
		String semver = lookupSemanticVersion(meta);
		return OrderedVersion.from(meta, semver);
	}

	@Override
	protected boolean isExistingVersionMetaValid(SkyrisingMeta.SkyrisingVersionEntry launcherMetaEntry) throws IOException {
		SkyrisingVersionDetails versionDetails = getVersionDetailsForVersion(launcherMetaEntry);
		SkyrisingVersionDetails.SkyrisingVersionDetailsManifest oldestManifest = versionDetails.getOldestManifest();
		return RemoteHelper.SHA1.fileMatchesChecksum(getPathOfVersionMeta(versionDetails, oldestManifest), oldestManifest.hash());
	}

	@Override
	public List<String> getParentVersion(OrderedVersion mcVersion) {
		return this.versionDetailsMap.get(mcVersion.launcherFriendlyVersionName()).previous().stream().map(idVersion ->
			this.versionDetailsMap.get(idVersion).normalizedVersion()).collect(Collectors.toList());
	}
}
