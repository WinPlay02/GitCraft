package com.github.winplay02.gitcraft.meta;

import java.time.ZonedDateTime;
import java.util.List;

public record VersionInfo(MavenArtifactMetadata assetIndex, String assets, Downloads downloads, String id,
						  JavaVersion javaVersion, List<LibraryMetadata> libraries, String mainClass,
						  ZonedDateTime releaseTime, ZonedDateTime time, String type) {
	public record Downloads(MavenArtifactMetadata client, MavenArtifactMetadata client_mappings, MavenArtifactMetadata server,
							MavenArtifactMetadata server_mappings, MavenArtifactMetadata windows_server, MavenArtifactMetadata server_zip) {
	}

	public record JavaVersion(int majorVersion) {
	}
}

