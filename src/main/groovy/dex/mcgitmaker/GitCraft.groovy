package dex.mcgitmaker

import dex.mcgitmaker.data.McMetadata
import dex.mcgitmaker.data.McVersion
import dex.mcgitmaker.loom.Decompiler
import net.fabricmc.loader.api.SemanticVersion

import java.nio.file.Paths

import groovy.cli.picocli.CliBuilder

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

    public static boolean CONFIG_LOAD_INTEGRATED_DATAPACK = true
    public static boolean CONFIG_LOAD_ASSETS = true
    public static boolean CONFIG_LOAD_ASSETS_EXTERN = true
    public static boolean CONFIG_VERIFY_CHECKSUMS = true
    public static boolean CONFIG_CHECKSUM_REMOVE_INVALID_FILES = true
    public static boolean CONFIG_SKIP_NON_LINEAR = false
    public static boolean CONFIG_NO_REPO = false
    public static boolean CONFIG_PRINT_EXISTING_FILE_CHECKSUM_MATCHING = false
    public static boolean CONFIG_PRINT_EXISTING_FILE_CHECKSUM_MATCHING_SKIPPED = false
    public static int CONFIG_FAILED_FETCH_RETRY_INTERVAL = 500

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
        def cli_args = new CliBuilder(usage:'gradlew run --args="[Options]"', header:'Options:', footer:'If you want to decompile versions which are not part of the default minecraft meta, put the JSON files of these versions (e.g. 1_16_combat-0.json) into the "extra-versions" directory')
        cli_args._(longOpt:'only-version', args:1, argName:'version', 'Specify the only version to decompile. The repository be stored in minecraft-repo-<version>. The normal repository (minecraft-repo) will not be touched. --only-version will take precedence over --min-version. Implies --skip-nonlinear')
        cli_args._(longOpt:'min-version', args:1, argName:'version', 'Specify the min. version to decompile. Each following (mainline) version will be decompiled afterwards. The repository will be stored in minecraft-repo-min-<version>. The normal repository (minecraft-repo) will not be touched. Implies --skip-nonlinear')
        cli_args._(longOpt:'no-verify', 'Disables checksum verification')
        cli_args._(longOpt:'no-datapack', 'Disables data (integrated datapack) versioning')
        cli_args._(longOpt:'no-assets', 'Disables assets versioning (includes external assets)')
        cli_args._(longOpt:'no-external-assets', 'Disables assets versioning for assets not included inside "minecraft".jar (e.g. other languages). Has no effect if --no-assets is specified')
        cli_args._(longOpt:'skip-nonlinear', 'Skips non-linear (e.g. April Fools, Combat Snapshots, ...) versions')
        cli_args._(longOpt:'no-repo', 'Prevents the creation/modification of a repository for versioning, only decompiles the provided (or all) version(s)')
        cli_args.h(longOpt:'help', 'Displays this help screen')
        def cli_args_parsed = cli_args.parse(args)
        CONFIG_LOAD_ASSETS = !cli_args_parsed.hasOption('no-assets')
        CONFIG_LOAD_ASSETS_EXTERN = !cli_args_parsed.hasOption('no-external-assets')
        CONFIG_VERIFY_CHECKSUMS = !cli_args_parsed.hasOption('no-verify')
        CONFIG_SKIP_NON_LINEAR = cli_args_parsed.hasOption('skip-nonlinear')
        CONFIG_NO_REPO = cli_args_parsed.hasOption('no-repo')
        CONFIG_LOAD_INTEGRATED_DATAPACK = !cli_args_parsed.hasOption('no-datapack')
        
        if (cli_args_parsed.hasOption('help')) {
            println(cli_args.usage())
            return;
        }
        println("Integrated datapack versioning is ${CONFIG_LOAD_INTEGRATED_DATAPACK ? "enabled" : "disabled"}")
        println("Asset versioning is ${CONFIG_LOAD_ASSETS ? "enabled" : "disabled"}")
        println("External asset versioning is ${CONFIG_LOAD_ASSETS_EXTERN ? (CONFIG_LOAD_ASSETS ? "enabled" : "implicitely disabled") : "disabled"}")
        println("Checksum verification is ${CONFIG_VERIFY_CHECKSUMS ? "enabled" : "disabled"}")
        println("Non-Linear version are ${CONFIG_SKIP_NON_LINEAR || cli_args_parsed.hasOption('only-version') || cli_args_parsed.hasOption('min-version') ? "skipped" : "included"}")
        println("Repository creation and versioning is ${CONFIG_NO_REPO ? "skipped" : "enabled"}")
        
        def gitCraft = new GitCraft()
        
        if (cli_args_parsed.hasOption('only-version')) {
            def subjectVersion = cli_args_parsed.'only-version'
            println("Decompiling only one Version: ${subjectVersion}")
            gitCraft.updateRepoOneVersion(subjectVersion)
            return;
        }
        
        if (cli_args_parsed.hasOption('min-version')) {
            def subjectVersion = cli_args_parsed.'min-version'
            println("Decompiling starting at version: ${subjectVersion}")
            gitCraft.updateRepoMinVersion(subjectVersion)
            return;
        }
        
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

    def decompileNoRepository(McVersion mcVersion) {
        mcVersion.decompiledMc()
        if (CONFIG_LOAD_ASSETS && CONFIG_LOAD_ASSETS_EXTERN) {
            McMetadata.fetchAssetsOnly(mcVersion)
        }
    }

    def getMinecraftMainlineVersionByName(String version_name) {
        for (value in versions.values()) {
            if(value.version.equalsIgnoreCase(version_name)) {
                return value;
            }
        }
        return null;
    }

    def updateRepoMinVersion(String version_name) {
        def mc_version = getMinecraftMainlineVersionByName(version_name)
        if (mc_version == null) {
            println("${version_name} is invalid")
            System.exit(1)
        }
        def r = null
        if (!CONFIG_NO_REPO) {
            r = new RepoManager(MAIN_ARTIFACT_STORE.parent.resolve('minecraft-repo-min-' + version_name))
        }
        
        def decompile_starting_this_version = false
        for (value in versions.values()) {
            if (value.version.equalsIgnoreCase(version_name)) {
                decompile_starting_this_version = true;
            }
            if (!decompile_starting_this_version) {
                continue;
            }
            if (!CONFIG_NO_REPO) {
                r.commitDecompiled(value)
            } else {
                decompileNoRepository(value)
            }
        }
        if (!CONFIG_NO_REPO) {
            r.finish()
        }
    }

    def updateRepoOneVersion(String version_name) {
        def mc_version = getMinecraftMainlineVersionByName(version_name)
        if (mc_version == null) {
            println("${version_name} is invalid")
            System.exit(1)
        }
        if (!CONFIG_NO_REPO) {
            def r = new RepoManager(MAIN_ARTIFACT_STORE.parent.resolve('minecraft-repo-' + version_name))
            r.commitDecompiled(mc_version)
            r.finish()
        } else {
            decompileNoRepository(mc_version)
        }
    }

    def updateRepo() {
        def r = null
        if (!CONFIG_NO_REPO) {
            r = new RepoManager()
        }

        versions.each {sv, mcv ->
            if (!CONFIG_NO_REPO) {
                r.commitDecompiled(mcv)
            } else {
                decompileNoRepository(mcv)
            }
        }

        // Only commit non-linear versions after linear versions to find correct branching point
        if (!CONFIG_SKIP_NON_LINEAR) {
            nonLinearVersions.each {sv, mcv ->
                if (!CONFIG_NO_REPO) {
                    r.commitDecompiled(mcv)
                } else {
                    decompileNoRepository(mcv)
                }
            }
        }
        if (!CONFIG_NO_REPO) {
            r.finish()
        }
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
