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
import java.util.regex.Pattern

import dex.mcgitmaker.loom.FileSystemUtil
import static dex.mcgitmaker.GitCraft.*
import dex.mcgitmaker.data.McMetadata

class RepoManager {
    Git git
    static String MAINLINE_LINEAR_BRANCH = "master"
    static Pattern LINEAR_SNAPSHOT_REGEX = ~/(^\d\dw\d\d[a-z]$)|(^\d.\d+(.\d+)?(-(pre|rc)\d|\_[a-z\_\-]+snapshot-\d+)$)/

    RepoManager() {
        this.git = setupRepo()
    }

    def finish() {
        git.close()
    }
    
    static boolean isVersionNonLinearSnapshot(McVersion mcVersion) {
        return mcVersion.snapshot && !(mcVersion.version ==~ RepoManager.LINEAR_SNAPSHOT_REGEX)
    }

    String findBaseForNonLinearVersionString(McVersion mcVersion) {
        switch(mcVersion.loaderVersion) {
            // Combat
            case "1.15-rc.3.combat.4": {
                return '1.15-pre3\n\nSemVer: 1.15-rc.3'
            }
            case "1.15.2-rc.2.combat.5": {
                return '1.15.2-pre2\n\nSemVer: 1.15.2-rc.2'
            }
            case "1.16.2-beta.3.combat.6": {
                return '1.16.2-pre3\n\nSemVer: 1.16.2-beta.3'
            }
            case "1.16.3-combat.7": {
                return '1.16.2\n\nSemVer: 1.16.2'
            }
            case "1.16.3-combat.7.b": {
                return '1.16_combat-1\n\nSemVer: 1.16.3-combat.7'
            }
            case "1.16.3-combat.7.c": {
                return '1.16_combat-2\n\nSemVer: 1.16.3-combat.7.b'
            }
            case "1.16.3-combat.8": {
                return '1.16_combat-3\n\nSemVer: 1.16.3-combat.7.c'
            }
            case "1.16.3-combat.8.b": {
                return '1.16_combat-4\n\nSemVer: 1.16.3-combat.8'
            }
            case "1.16.3-combat.8.c": {
                return '1.16_combat-5\n\nSemVer: 1.16.3-combat.8.b'
            }
            // April
            case "1.16-alpha.20.13.inf": {
                return '20w13b\n\nSemVer: 1.16-alpha.20.13.b'
            }
            case "1.19-alpha.22.13.oneblockatatime": {
                return '22w13a\n\nSemVer: 1.19-alpha.22.13.a'
            }
            case "1.20-alpha.23.13.ab": {
                return '23w13a\n\nSemVer: 1.20-alpha.23.13.a'
            }
            default: {
                return null
            }
        }
    }

    RevCommit findBaseForNonLinearVersion(McVersion mcVersion) {
        def msg = findBaseForNonLinearVersionString(mcVersion)
        if (msg == null) {
            return null
        }
        def branchPointIter = git.log().all().setRevFilter(new CommitMsgFilter(msg)).call().iterator()
        if (branchPointIter.hasNext()) {
            return branchPointIter.next()
        }
        return null
    }

    void commitDecompiled(McVersion mcVersion) {
        // add april fools clasification to outlet-database?
        def msg = mcVersion.version + '\n\nSemVer: ' + mcVersion.loaderVersion

        def target_branch = MAINLINE_LINEAR_BRANCH
        if (git.getRepository().resolve(Constants.HEAD) != null) { // Don't run on empty repo
            target_branch = this.isVersionNonLinearSnapshot(mcVersion) ? mcVersion.version : MAINLINE_LINEAR_BRANCH
            if (git.getRepository().getBranch() != target_branch) {
                def target_ref = git.getRepository().getRefDatabase().findRef(target_branch)
                if (target_ref == null) {
                    def branchPoint = findBaseForNonLinearVersion(mcVersion)
                    if (branchPoint == null) {
                        println 'Could not find branching point for non-linear version: ' + mcVersion.version + ' (' + mcVersion.loaderVersion + ')'
                        return
                    }
                    git.branchCreate().setStartPoint(branchPoint).setName(target_branch).call()
                } else {
                    def tip_commit = target_ref.getObjectId()
                    if (git.log().add(tip_commit).setRevFilter(new CommitMsgFilter(msg)).call().size() > 0) return
                }
                git.checkout().setName(target_branch).call()
            } else {
                if (git.log().setRevFilter(new CommitMsgFilter(msg)).call().size() > 0) return
            }
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
        try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcVersion.decompiledMc().toPath())) {
            copyLargeDir(fs.get().getPath("."), GitCraft.REPO.resolve('minecraft').resolve('src'))
        }
        
        // Copy assets & data (it makes sense to track them, atleast the data)
        try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcVersion.mergedJarPath())) {
            if (CONFIG_LOAD_ASSETS) {
                copyLargeDir(fs.get().getPath("assets"), GitCraft.REPO.resolve('minecraft').resolve('resources').resolve('assets'))
            }
            copyLargeDir(fs.get().getPath("data"), GitCraft.REPO.resolve('minecraft').resolve('resources').resolve('data'))
        }

        if (CONFIG_LOAD_ASSETS && CONFIG_LOAD_ASSETS_EXTERN) {
            McMetadata.copyExternalAssetsToRepo(mcVersion)
        }

        // Make commit
        git.add().addFilepattern(".").call()
        java.util.Date version_date = new java.util.Date(java.time.OffsetDateTime.parse(mcVersion.time).toInstant().toEpochMilli())
        PersonIdent author = new PersonIdent("Mojang", "gitcraft@decompiled.mc", version_date, TimeZone.getTimeZone("UTC"))
        git.commit().setAll(true).setMessage(msg).setAuthor(author).call()

        println 'Commited ' + mcVersion.version + ' to the repository! (Target Branch is ' + target_branch + (this.isVersionNonLinearSnapshot(mcVersion) ? " (non-linear)" : "") + ")"
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
