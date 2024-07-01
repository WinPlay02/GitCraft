package com.github.winplay02.gitcraft.manifest.skyrising;

import com.github.winplay02.gitcraft.manifest.BaseMetadataProvider;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.meta.VersionDetails;
import com.github.winplay02.gitcraft.meta.VersionInfo;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class SkyrisingMetadataProvider extends BaseMetadataProvider<SkyrisingManifest, SkyrisingManifest.VersionEntry> {

	private final Map<String, VersionDetails> versionDetails = new HashMap<>();

	public SkyrisingMetadataProvider() {
		this.addManifestSource("https://skyrising.github.io/mc-versions/version_manifest.json", SkyrisingManifest.class);
	}

	@Override
	public ManifestSource getSource() {
		return ManifestSource.SKYRISING;
	}

	@Override
	public String getName() {
		return "Skyrising Version Metadata (https://skyrising.github.io/mc-versions/)";
	}

	@Override
	public String getInternalName() {
		return "skyrising";
	}

	@Override
	protected OrderedVersion loadVersionFromManifest(SkyrisingManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		VersionInfo info = this.fetchVersionMetadata(manifestEntry.id(), manifestEntry.url(), manifestEntry.sha1(), targetDir.resolve("info"), "version info", VersionInfo.class);
		VersionDetails details = this.fetchVersionMetadata(manifestEntry.id(), manifestEntry.details(), null, targetDir.resolve("details"), "version details", VersionDetails.class);
		this.versionDetails.put(details.id(), details);
		return OrderedVersion.from(info, details.normalizedVersion());
	}

	@Override
	protected void loadVersionsFromRepository(Path dir, Consumer<OrderedVersion> loader) throws IOException {
		Set<VersionInfo> infos = new LinkedHashSet<>();
		Map<String, VersionDetails> detailses = new LinkedHashMap<>();

		for (Path file : Files.newDirectoryStream(dir, f -> Files.isRegularFile(f) && (f.endsWith(".json") || f.endsWith(".zip")))) {
			VersionInfo info = this.loadVersionMetadata(file, VersionInfo.class);

			// we could check every field but this ought to be enough
			if (info.id() != null && info.assets() != null) {
				infos.add(info);
			} else {
				VersionDetails details = this.loadVersionMetadata(file, VersionDetails.class);

				// we could check every field but this ought to be enough
				if (details.id() != null && details.normalizedVersion() != null) {
					detailses.put(details.id(), details);
				}
			}
		}

		for (VersionInfo info : infos) {
			VersionDetails details = detailses.remove(info.id());

			if (details != null) {
				this.versionDetails.put(details.id(), details);
				loader.accept(OrderedVersion.from(info, details.normalizedVersion()));
			} else {
				MiscHelper.println("repository contains version info json for %s, but no version details json!", info.id());
			}
		}
		for (VersionDetails details : detailses.values()) {
			MiscHelper.println("repository contains version details json for %s, but no version info json!", details.id());
		}
	}

	@Override
	protected boolean isExistingVersionMetadataValid(SkyrisingManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		return this.isExistingVersionMetadataValid(manifestEntry.id(), manifestEntry.url(), manifestEntry.sha1(), targetDir.resolve("info"))
			&& this.isExistingVersionMetadataValid(manifestEntry.id(), manifestEntry.details(), null, targetDir.resolve("details"));
	}

	@Override
	public List<String> getParentVersion(OrderedVersion mcVersion) {
		return this.getVersionDetails(mcVersion.launcherFriendlyVersionName()).previous().stream()
			.filter(versionId -> switch (versionId) {
				// 12w34a has 1.3.2 as parent while 12w32a has 1.3.1 as parent
				// leading to two valid paths through those versions
				case "1.3.2"     -> false;
				default          -> true;
			})
			.map(versionId -> this.getVersionDetails(versionId).normalizedVersion())
			.filter(Objects::nonNull)
			.toList();
	}

	private VersionDetails getVersionDetails(String versionId) {
		return this.versionDetails.get(versionId);
	}
}
