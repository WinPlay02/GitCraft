package dex.mcgitmaker.data

import java.nio.file.Path
import java.nio.file.Paths

class Artifact {
    String url // Download URL
    String name // File name
    String containingPath

    File fetchArtifact() {
        def path = getContainingPath().resolve(name)
        ensureArtifactPresence(path)

        return path.toFile()
    }

    void ensureArtifactPresence(Path p) {
        def f = p.toFile()
        if (!f.exists()) {
            p.parent.toFile().mkdirs()
            println 'Missing artifact: ' + name + ' At: ' + containingPath
            println 'Attempting to download...'

            p.withOutputStream { it << new URL(url).newInputStream() }
        }
    }

    Path getContainingPath() {
        return Paths.get(containingPath)
    }

    static String nameFromUrl(String url) {
        if (url == null) return ""
        return url.split("/").last()
    }
}
