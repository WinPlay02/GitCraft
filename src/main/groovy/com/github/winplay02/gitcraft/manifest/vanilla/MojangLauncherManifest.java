package com.github.winplay02.gitcraft.manifest.vanilla;

import java.util.List;

import com.github.winplay02.gitcraft.manifest.VersionsManifest;

public record MojangLauncherManifest(List<VersionEntry> versions) implements VersionsManifest<MojangLauncherManifest.VersionEntry> {
	public record VersionEntry(String id, String url, String sha1) implements VersionsManifest.VersionEntry {
	}
}
