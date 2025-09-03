package com.github.winplay02.gitcraft.manifest.skyrising;

import java.time.ZonedDateTime;
import java.util.List;

import com.github.winplay02.gitcraft.manifest.VersionsManifest;

public record SkyrisingManifest(LatestVersions latest, List<VersionEntry> versions) implements VersionsManifest<SkyrisingManifest.VersionEntry> {
	public record LatestVersions(String old_alpha, String classic_server, String alpha_server, String old_beta, String release, String snapshot, String pending) {
	}

	public record VersionEntry(String id, String type, String url, ZonedDateTime time, ZonedDateTime releaseTime, String details) implements VersionsManifest.VersionEntry {
	}
}
