package dex.mcgitmaker.data

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
            println 'Downloading ' + url + '...'

            // p.withOutputStream { it << new URL(url).newInputStream() }
            // p.withOutputStream { it <<  }
            def open_stream = new URL(url).openConnection(java.net.Proxy.NO_PROXY).getInputStream()
            Files.copy(open_stream, p, StandardCopyOption.REPLACE_EXISTING)
            open_stream.close()
            println 'Finished!'
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
