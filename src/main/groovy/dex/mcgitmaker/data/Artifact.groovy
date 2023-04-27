package dex.mcgitmaker.data

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static dex.mcgitmaker.GitCraft.*
import dex.mcgitmaker.Util

class Artifact {
    String url // Download URL
    String name // File name
    String containingPath
    String sha1sum = null

    File fetchArtifact() {
        def path = getContainingPath().resolve(name)
        ensureArtifactPresence(path)

        return path.toFile()
    }

    void ensureArtifactPresence(Path p) {
        Util.downloadToFileWithChecksumIfNotExists(url, p, sha1sum, "artifact", name)
    }

    Path getContainingPath() {
        return Paths.get(containingPath)
    }

    static String nameFromUrl(String url) {
        if (url == null) return ""
        return url.split("/").last()
    }
}
