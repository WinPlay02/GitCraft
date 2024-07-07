package com.github.winplay02.gitcraft.meta;

import com.github.winplay02.gitcraft.util.RemoteHelper;

import java.io.IOException;

public record LibraryMetadata(String name, Downloads downloads) {
	public ArtifactMetadata getArtifact() {
		if (this.downloads() != null) {
			return this.downloads().artifact();
		}
		final String mavenUrl = RemoteHelper.createMavenURLFromMavenArtifact("https://libraries.minecraft.net", this.name());
		try {
			return RemoteHelper.createMavenURLFromMavenArtifact(mavenUrl);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public record Downloads(ArtifactMetadata artifact) {
	}
}
