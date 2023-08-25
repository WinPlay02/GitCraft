package com.github.winplay02.meta;

import java.util.List;

public record VersionMeta(ArtifactMeta assetIndex, String assets, VersionDownloadsMeta downloads, String id,
						  JavaVersionMeta javaVersion, List<LibraryMeta> libraries, String mainClass,
						  String releaseTime, String time, String type) {

	public record VersionDownloadsMeta(ArtifactMeta client, ArtifactMeta client_mappings, ArtifactMeta server,
									   ArtifactMeta server_mappings) {

	}

	public record JavaVersionMeta(int majorVersion) {

	}
}

