package dex.mcgitmaker.data;

import com.github.winplay02.gitcraft.util.RemoteHelper;

import java.io.File;
import java.nio.file.Path;

/**
 * @param url            Download URL
 * @param name           File name
 * @param containingPath
 * @param sha1sum
 */
public record Artifact(String url, String name, Path containingPath, String sha1sum) {
	public Artifact(String url, String name, Path containingPath) {
		this(url, name, containingPath, null);
	}

	public File fetchArtifact() {
		Path path = containingPath().resolve(name);
		ensureArtifactPresence(path);
		return path.toFile();
	}

	void ensureArtifactPresence(Path p) {
		RemoteHelper.downloadToFileWithChecksumIfNotExists(url, new RemoteHelper.LocalFileInfo(p, sha1sum, "artifact", name), RemoteHelper.SHA1);
	}

	static String nameFromUrl(String url) {
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
