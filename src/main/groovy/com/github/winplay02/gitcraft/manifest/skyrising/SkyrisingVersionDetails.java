package com.github.winplay02.gitcraft.manifest.skyrising;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record SkyrisingVersionDetails(String id, List<SkyrisingVersionDetailsManifest> manifests, List<String> next,
									  List<String> previous, String normalizedVersion, boolean client, boolean server,
									  boolean sharedMappings) {
	public static final Comparator<SkyrisingVersionDetailsManifest> COMPARATOR_MANIFEST_LAST_MODIFIED = Comparator.comparing(SkyrisingVersionDetailsManifest::lastModified, Comparator.nullsFirst(Comparator.naturalOrder()));

	public List<SkyrisingVersionDetailsManifest> getOldestManifests() {
		// Version Quirks
		if (this.id().equals("a1.2.0_02") || this.id().equals("a1.2.0") || this.id.equals("a1.0.4") || this.id.equals("rd-160052-launcher")) {
			return List.of(manifests.stream().filter(manifest -> manifest.downloadsId() == 2).min(COMPARATOR_MANIFEST_LAST_MODIFIED).orElseThrow());
		}
		Map<Integer, Optional<SkyrisingVersionDetailsManifest>> oldestManifests = manifests.stream().collect(Collectors.groupingBy(SkyrisingVersionDetailsManifest::downloadsId, Collectors.minBy(COMPARATOR_MANIFEST_LAST_MODIFIED)));
		return oldestManifests.values().stream().filter(Optional::isPresent).map(Optional::orElseThrow).collect(Collectors.toList());
	}

	public record SkyrisingVersionDetailsManifest(String url, String type, ZonedDateTime time,
												  ZonedDateTime lastModified, String hash, int downloadsId) {
		public URI targetURI() {
			try {
				return new URI("https://skyrising.github.io/mc-versions/manifest/").resolve(this.url());
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
