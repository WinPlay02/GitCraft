package com.github.winplay02.gitcraft.manifest.skyrising;

import java.util.List;

import com.github.winplay02.gitcraft.manifest.VersionsManifest;

public record SkyrisingManifest(List<VersionEntry> versions) implements VersionsManifest<SkyrisingManifest.VersionEntry> {
	public record VersionEntry(String id, String url, String sha1, String details) implements VersionsManifest.VersionEntry {
	}
}
