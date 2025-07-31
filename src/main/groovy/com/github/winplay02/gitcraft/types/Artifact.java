package com.github.winplay02.gitcraft.types;

import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.RemoteHelper;

import java.nio.file.Path;
import java.util.concurrent.Executor;

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

	public StepStatus fetchArtifact(Executor executor, Path containingPath) {
		return fetchArtifact(executor, containingPath, "artifact");
	}

	public StepStatus fetchArtifact(Executor executor, Path containingPath, String artifactKind) {
		Path path = resolve(containingPath);
		return RemoteHelper.downloadToFileWithChecksumIfNotExists(executor, url, new FileSystemNetworkManager.LocalFileInfo(path, sha1sum, Library.IA_SHA1, artifactKind, name));
	}

	public StepStatus fetchArtifactToFile(Executor executor, Path filePath, String artifactKind) {
		return RemoteHelper.downloadToFileWithChecksumIfNotExists(executor, url, new FileSystemNetworkManager.LocalFileInfo(filePath, sha1sum, Library.IA_SHA1, artifactKind, name));
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
