package com.github.winplay02.gitcraft.manifest.metadata;

import com.github.winplay02.gitcraft.util.RemoteHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record LibraryMetadata(String name, Downloads downloads, List<VersionInfo.VersionArgumentRule> rules, Extract extract, Map<String, String> natives) {
	public List<ArtifactMetadata> getArtifact() {
		if (this.downloads() != null) {
			List<ArtifactMetadata> artifacts = new ArrayList<>();
			if (this.downloads().artifact() != null) {
				artifacts.add(this.downloads().artifact());
			}
			if (this.downloads().classifiers() != null) {
				artifacts.addAll(this.downloads().classifiers().values());
			}
			return artifacts;
		}
		final String mavenUrl = RemoteHelper.createMavenURLFromMavenArtifact("https://libraries.minecraft.net", this.name());
		try {
			return List.of(RemoteHelper.createMavenURLFromMavenArtifact(mavenUrl));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public record Downloads(ArtifactMetadata artifact, Map<String, ArtifactMetadata> classifiers) {
	}

	public record Extract(List<String> exclude) {}
}
