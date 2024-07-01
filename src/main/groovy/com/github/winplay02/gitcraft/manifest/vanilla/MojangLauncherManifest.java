package com.github.winplay02.gitcraft.manifest.vanilla;

import java.util.List;

import com.github.winplay02.gitcraft.manifest.VersionsManifest;

public record MojangLauncherManifest(LatestVersions latest, List<VersionEntry> versions) implements VersionsManifest<MojangLauncherManifest.VersionEntry> {
	public record LatestVersions(String release, String snapshot) {
	}

	public record VersionEntry(String id, String url, String sha1) implements VersionsManifest.VersionEntry {
	}
}
