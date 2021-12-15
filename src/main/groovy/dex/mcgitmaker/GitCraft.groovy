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
    public static final def REPO = MAIN_ARTIFACT_STORE.parent.resolve('minecaft-repo')
    public static final def MC_VERSION_STORE = MAIN_ARTIFACT_STORE.resolve('mc-versions')
    public static final def LIBRARY_STORE = MAIN_ARTIFACT_STORE.resolve('libraries')
    public static final def METADATA_STORE = MAIN_ARTIFACT_STORE.resolve('metadata.json')
    public static final def REMAPPED = MAIN_ARTIFACT_STORE.resolve('remapped-mc')
    McMetadata mcMetadata
    TreeMap<SemanticVersion, McVersion> versions

    static {
        MAIN_ARTIFACT_STORE.toFile().mkdirs()
        DECOMPILED_WORKINGS.toFile().mkdirs()
        MAPPINGS.toFile().mkdirs()
        REPO.toFile().mkdirs()
        MC_VERSION_STORE.toFile().mkdirs()
        LIBRARY_STORE.toFile().mkdirs()
        REMAPPED.toFile().mkdirs()
    }

    GitCraft() {
        this.mcMetadata = new McMetadata()
        Util.saveMetadata(mcMetadata.metadata)
        versions = Util.orderVersionMap(mcMetadata.metadata)
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
