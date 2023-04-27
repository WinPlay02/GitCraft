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
        def f = p.toFile()
        if (f.exists() && this.sha1sum != null && CONFIG_VERIFY_CHECKSUMS) {
            def actualSha1 = Util.calculateSHA1Checksum(f)
            if (!actualSha1.equalsIgnoreCase(sha1sum)) {
                println 'Checksum of artifact ' + name + " is " + actualSha1 + ", expected " + sha1sum
            } else {
                if (CONFIG_PRINT_EXISTING_FILE_CHECKSUM_MATCHING) {
                    println 'Reading artifact locally for: ' + name + " (checksums match)"
                }
            }
        }
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
            def actualSha1 = Util.calculateSHA1Checksum(f)
            if (this.sha1sum != null && CONFIG_VERIFY_CHECKSUMS) {
                if (!actualSha1.equalsIgnoreCase(sha1sum)) {
                    println 'Checksum of artifact ' + name + " is " + actualSha1 + ", expected " + sha1sum
                } else {
                    println 'Reading artifact locally for: ' + name + " (checksums match)"
                }
            } else {
                println 'Finished! (no checksum checked)'
            }
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
