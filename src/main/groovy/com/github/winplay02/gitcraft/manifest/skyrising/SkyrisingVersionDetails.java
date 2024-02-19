package com.github.winplay02.gitcraft.manifest.skyrising;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

public record SkyrisingVersionDetails(String id, List<SkyrisingVersionDetailsManifest> manifests, List<String> next,
									  List<String> previous, String normalizedVersion, boolean client, boolean server,
									  boolean sharedMappings) {
	public static final Comparator<SkyrisingVersionDetailsManifest> COMPARATOR_MANIFEST_LAST_MODIFIED = Comparator.comparing(SkyrisingVersionDetailsManifest::lastModified);

	public SkyrisingVersionDetailsManifest getOldestManifest() {
		return manifests.stream().min(COMPARATOR_MANIFEST_LAST_MODIFIED).orElseThrow();
	}

	public record SkyrisingVersionDetailsManifest(String url, String type, ZonedDateTime time,
												  ZonedDateTime lastModified, String hash) {
	}
}
