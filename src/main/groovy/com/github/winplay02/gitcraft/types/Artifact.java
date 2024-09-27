package com.github.winplay02.gitcraft.types;

import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.util.RemoteHelper;

import java.nio.file.Path;

/**
 * Single artifact
 *
 * @param url     Download URL
 * @param name    Artifact File name
 * @param sha1sum SHA1 Checksum
 */
public record Artifact(String url, String name, String sha1sum) {
	public Artifact(String url, String name) {
		this(url, name, null);
	}

	public static Artifact fromURL(String url, String sha1sum) {
		return new Artifact(url, nameFromUrl(url), sha1sum);
	}

	public Path resolve(Path containingPath) {
		return containingPath.resolve(name);
	}

	public StepStatus fetchArtifact(Path containingPath) {
		return fetchArtifact(containingPath, "artifact");
	}

	public StepStatus fetchArtifact(Path containingPath, String artifactKind) {
		Path path = resolve(containingPath);
		return RemoteHelper.downloadToFileWithChecksumIfNotExists(url, new RemoteHelper.LocalFileInfo(path, sha1sum, artifactKind, name), RemoteHelper.SHA1);
	}

	public static String nameFromUrl(String url) {
		if (url == null) {
			return "";
		}
		String[] urlParts = url.split("/");
		return urlParts[urlParts.length - 1];
	}

	public record DependencyArtifact(String sha1sum, String name, String url) {
		public DependencyArtifact(Artifact artifact) {
			this(artifact.sha1sum(), artifact.name(), artifact.url());
		}

		public static DependencyArtifact ofVirtual(String name) {
			return new DependencyArtifact(null, name, null);
		}
	}
}
