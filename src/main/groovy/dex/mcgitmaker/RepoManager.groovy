package dex.mcgitmaker

import dex.mcgitmaker.data.McVersion
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream

class RepoManager {
    Git git

    RepoManager() {
        this.git = setupRepo()
    }

    def finish() {
        git.close()
    }

    void commitDecompiled(McVersion mcVersion) {
        //todo detect april fools versions and make each their own branch based on semver
        // match to avoid polluting main history
        // add april fools clasification to outlet-database?
        def msg = mcVersion.version + '\n\nSemVer: ' + mcVersion.loaderVersion

        if (git.getRepository().resolve(Constants.HEAD) != null) { // Don't run on empty repo
            if (git.log().setRevFilter(new CommitMsgFilter(msg)).call().size() > 0) return
        }

        // Run from latest version
        git.reset().setMode(ResetCommand.ResetType.HARD)

        // Clear repo
        GitCraft.REPO.toFile().listFiles().each {
            if (!it.toPath().toString().endsWith('.git')) {
                if (it.isDirectory()) {
                    it.deleteDir()
                } else {
                    it.delete()
                }
            }
        }

        // Copy decompiled MC to repo directory
        println 'Moving files to repo...'
        copyLargeDir(mcVersion.decompiledMc().toPath(), GitCraft.REPO.resolve('minecraft'))

        // Make commit
        git.add().addFilepattern(".").call()
        git.commit().setAll(true).setMessage(msg).setAuthor("Mojang", "gitcraft@decompiled.mc").call()

        println 'Commited ' + mcVersion.version + ' to the repository!'
    }

    def setupRepo() {
        return Git.init().setDirectory(GitCraft.REPO.toFile()).call()
    }

    private static void copyLargeDir(Path source, Path target) {
        if (Files.isDirectory(source)) {
            if (Files.notExists(target)) {
                Files.createDirectories(target)
            }

            try (Stream<Path> paths = Files.list(source)) {
                paths.forEach(p -> copyLargeDir(p, target.resolve(source.relativize(p)))
                )
            }

        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private static final class CommitMsgFilter extends RevFilter {
        String msg

        CommitMsgFilter(String msg) {
            this.msg = msg
        }

        @Override
        boolean include(RevWalk walker, RevCommit c) {
            c.fullMessage == this.msg
        }

        @Override
        RevFilter clone() {
            return this
        }

        @Override
        boolean requiresCommitBody() {
            return true
        }

        @Override
        String toString() {
            return "MSG FILTER"
        }
    }
}
