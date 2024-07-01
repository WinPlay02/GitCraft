package com.github.winplay02.gitcraft.manifest.skyrising;

import java.util.List;

import com.github.winplay02.gitcraft.manifest.VersionsManifest;

public record SkyrisingManifest(LatestVersions latest, List<VersionEntry> versions) implements VersionsManifest<SkyrisingManifest.VersionEntry> {
	public record LatestVersions(String old_alpha, String classic_server, String alpha_server, String old_beta, String release, String snapshot, String pending) {
	}

	public record VersionEntry(String id, String url, String sha1, String details) implements VersionsManifest.VersionEntry {
	}
}
