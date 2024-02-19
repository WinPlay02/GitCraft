package com.github.winplay02.gitcraft.meta;

import java.time.ZonedDateTime;
import java.util.List;

public record VersionMeta(ArtifactMeta assetIndex, String assets, VersionDownloadsMeta downloads, String id,
						  JavaVersionMeta javaVersion, List<LibraryMeta> libraries, String mainClass,
						  ZonedDateTime releaseTime, ZonedDateTime time, String type) {

	public record VersionDownloadsMeta(ArtifactMeta client, ArtifactMeta client_mappings, ArtifactMeta server,
									   ArtifactMeta server_mappings) {

	}

	public record JavaVersionMeta(int majorVersion) {

	}
}

