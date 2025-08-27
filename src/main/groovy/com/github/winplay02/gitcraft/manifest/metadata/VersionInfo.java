package com.github.winplay02.gitcraft.manifest.metadata;

import java.time.ZonedDateTime;
import java.util.List;

public record VersionInfo(ArtifactMetadata assetIndex, String assets, Downloads downloads, String id,
						  JavaVersion javaVersion, List<LibraryMetadata> libraries, String mainClass,
						  ZonedDateTime releaseTime, ZonedDateTime time, String type) {
	public record Downloads(ArtifactMetadata client, ArtifactMetadata client_mappings, ArtifactMetadata server,
							ArtifactMetadata server_mappings, ArtifactMetadata windows_server, ArtifactMetadata server_zip) {
	}

	public record JavaVersion(int majorVersion) {
	}

	public VersionInfo withUpdatedId(String versionId) {
		if (this.id().equals(versionId)) {
			return this;
		}
		return new VersionInfo(this.assetIndex(), this.assets(), this.downloads(), versionId, this.javaVersion(), this.libraries(), this.mainClass(), this.releaseTime(), this.time(), this.type());
	}
}

