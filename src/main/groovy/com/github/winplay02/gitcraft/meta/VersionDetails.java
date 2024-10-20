package com.github.winplay02.gitcraft.meta;

import java.time.ZonedDateTime;
import java.util.List;

public record VersionDetails(String id, String normalizedVersion, List<String> next, List<String> previous,
							 List<ManifestEntry> manifests, boolean client, boolean server, boolean sharedMappings) {
	public record ManifestEntry(String url, String type, ZonedDateTime time, ZonedDateTime lastModified, String hash, int downloadsId, String downloads, String assetIndex, String assetHash) {
	}
}
