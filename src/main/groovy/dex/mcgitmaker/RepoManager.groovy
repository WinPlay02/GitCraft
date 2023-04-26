package dex.mcgitmaker

import dex.mcgitmaker.data.McVersion
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream

import dex.mcgitmaker.loom.FileSystemUtil

class RepoManager {
    Git git
    static String MAINLINE_LINEAR_BRANCH = "master"

    RepoManager() {
        this.git = setupRepo()
    }

    def finish() {
        git.close()
    }

    void commitDecompiled(McVersion mcVersion) {
        def msg = mcVersion.version + '\n\nSemVer: ' + mcVersion.loaderVersion

        if (git.getRepository().resolve(Constants.HEAD) != null) { // Don't run on empty repo
            def target_branch = mcVersion.isNonLinearSnapshot() ? mcVersion.version : MAINLINE_LINEAR_BRANCH
            if (git.getRepository().getBranch() != target_branch) {
                def target_ref = git.getRepository().getRefDatabase().findRef(target_branch)
                if (target_ref == null) {
                    git.branchCreate().setName(target_branch).call()
                }
                git.checkout().setName(target_branch).call()
            }
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
        copyLargeDir(mcVersion.decompiledMc().toPath(), GitCraft.REPO.resolve('minecraft').resolve('src'))
        
        // Copy assets & data (it makes sense to track them, atleast the data)
        try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcVersion.mergedJarPath())) {
            copyLargeDir(fs.get().getPath("assets"), GitCraft.REPO.resolve('minecraft').resolve('resources').resolve('assets'))
            copyLargeDir(fs.get().getPath("data"), GitCraft.REPO.resolve('minecraft').resolve('resources').resolve('data'))
        }

        // Make commit
        git.add().addFilepattern(".").call()
        java.util.Date version_date = new java.util.Date(java.time.OffsetDateTime.parse(mcVersion.time).toInstant().toEpochMilli())
        PersonIdent author = new PersonIdent("Mojang", "gitcraft@decompiled.mc", version_date, TimeZone.getTimeZone("UTC"))
        git.commit().setAll(true).setMessage(msg).setAuthor(author).call()

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
                paths.forEach(p -> copyLargeDir(p, target.resolve(source.relativize(p).toString())))
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
