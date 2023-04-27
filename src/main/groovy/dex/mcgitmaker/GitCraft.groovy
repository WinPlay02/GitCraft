package dex.mcgitmaker

import dex.mcgitmaker.data.McMetadata
import dex.mcgitmaker.data.McVersion
import dex.mcgitmaker.loom.Decompiler
import net.fabricmc.loader.api.SemanticVersion

import java.nio.file.Paths

class GitCraft {
    public static final def MAIN_ARTIFACT_STORE = Paths.get(new File('.').canonicalPath).resolve('artifact-store')
    public static final def DECOMPILED_WORKINGS = MAIN_ARTIFACT_STORE.resolve('decompiled')
    public static final def MAPPINGS = MAIN_ARTIFACT_STORE.resolve('mappings')
    public static final def REPO = MAIN_ARTIFACT_STORE.parent.resolve('minecraft-repo')
    public static final def MC_VERSION_STORE = MAIN_ARTIFACT_STORE.resolve('mc-versions')
    public static final def LIBRARY_STORE = MAIN_ARTIFACT_STORE.resolve('libraries')
    public static final def METADATA_STORE = MAIN_ARTIFACT_STORE.resolve('metadata.json')
    public static final def REMAPPED = MAIN_ARTIFACT_STORE.resolve('remapped-mc')
    public static final def ASSETS_INDEX = MAIN_ARTIFACT_STORE.resolve('assets-index')
    public static final def ASSETS_OBJECTS = MAIN_ARTIFACT_STORE.resolve('assets-objects')
    
    public static final def REMOTE_CACHE = Paths.get(new File('.').canonicalPath).resolve('remote-cache')
    public static final def META_CACHE = REMOTE_CACHE.resolve('meta-cache')

    public static final def SOURCE_EXTRA_VERSIONS = Paths.get(new File('.').canonicalPath).resolve('extra-versions')

    public static final boolean CONFIG_LOAD_ASSETS = true
    public static final boolean CONFIG_LOAD_ASSETS_EXTERN = true
    public static final boolean CONFIG_VERIFY_CHECKSUMS = true
    public static final boolean CONFIG_CHECKSUM_REMOVE_INVALID_FILES = true
    public static final boolean CONFIG_PRINT_EXISTING_FILE_CHECKSUM_MATCHING = false
    public static final boolean CONFIG_PRINT_EXISTING_FILE_CHECKSUM_MATCHING_SKIPPED = false
    public static final int CONFIG_FAILED_FETCH_RETRY_INTERVAL = 500
    
    McMetadata mcMetadata
    TreeMap<SemanticVersion, McVersion> versions
    TreeMap<SemanticVersion, McVersion> nonLinearVersions

    static {
        MAIN_ARTIFACT_STORE.toFile().mkdirs()
        DECOMPILED_WORKINGS.toFile().mkdirs()
        MAPPINGS.toFile().mkdirs()
        REPO.toFile().mkdirs()
        MC_VERSION_STORE.toFile().mkdirs()
        LIBRARY_STORE.toFile().mkdirs()
        REMAPPED.toFile().mkdirs()
        ASSETS_INDEX.toFile().mkdirs()
        ASSETS_OBJECTS.toFile().mkdirs()
        
        REMOTE_CACHE.toFile().mkdirs()
        META_CACHE.toFile().mkdirs()

        SOURCE_EXTRA_VERSIONS.toFile().mkdirs()
    }

    GitCraft() {
        this.mcMetadata = new McMetadata()
        versions = Util.orderVersionMap(mcMetadata.metadata)
        nonLinearVersions = Util.nonLinearVersionList(mcMetadata.metadata)
        println 'Saving updated metadata...'
        Util.saveMetadata(mcMetadata.metadata)
    }

    static void main(String[] args) {
        def gitCraft = new GitCraft()
        gitCraft.updateRepo()

        println 'Repo can be found at: ' + REPO.toString()
    }

    // Removes files that are unlikely to be needed again that are large
    private def cleanupFiles() {
        MC_VERSION_STORE.deleteDir()
        DECOMPILED_WORKINGS.deleteDir()
        REMAPPED.deleteDir()
    }

    def test() {
        def r = new RepoManager()

        r.commitDecompiled(versions.values().first())
        r.commitDecompiled(versions.values().last())

        r.finish()
    }

    def updateRepo() {
        def r = new RepoManager()

        versions.each {sv, mcv ->
            r.commitDecompiled(mcv)
        }

        // Only commit non-linear versions after linear versions to find correct branching point
        nonLinearVersions.each {sv, mcv ->
            r.commitDecompiled(mcv)
        }

        r.finish()
    }

    /**
     * @param destroyOldVersions Deletes old decompiled directories and decompiles them again
     */
    def decompileAllVersions(boolean destroyOldVersions) {
        println 'Decompiling all versions! This may take some time!'
        if (destroyOldVersions) println 'This will delete old decompiled versions!'

        versions.each {sv, mcv ->
            def d = Decompiler.decompiledPath(mcv).toFile()
            if (d.exists()) {
                if (destroyOldVersions) {
                    d.deleteDir()
                } else {
                    return
                }
            }

            Decompiler.decompile(mcv)
        }
    }

}
