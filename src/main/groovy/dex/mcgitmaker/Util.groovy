package dex.mcgitmaker

import dex.mcgitmaker.data.Artifact
import dex.mcgitmaker.data.McVersion
import dex.mcgitmaker.data.outlet.McFabric
import dex.mcgitmaker.data.outlet.McOutletMeta
import groovy.json.JsonGenerator
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup

import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.stream.Collectors

class Util {
    static enum MappingsNamespace {
        OFFICIAL,
        MOJMAP

        @Override
        String toString() {
            return name().toLowerCase(Locale.ENGLISH)
        }
    }

    static def saveMetadata(Map<String, McVersion> data) {
        def generator = new JsonGenerator.Options()
                .addConverter(Path) { it.toFile().canonicalPath }
                .build()

        def x = GitCraft.METADATA_STORE.toFile()
        x.createNewFile()
        x.write(generator.toJson(data))
    }

    static String fixupSemver(String proposedSemVer) {
        if (proposedSemVer == "1.19-22.w.13.oneBlockAtATime") {
            return "1.19-alpha.22.13.oneblockatatime"
        }
        if (proposedSemVer == "1.16.2-Combat.Test.8") { // outlet is wrong here, fabric gets it correct
            return "1.16.3-combat.8"
        }
        if (proposedSemVer.contains("-Experimental")) {
            return proposedSemVer.replace("-Experimental", "-alpha.0.0.Experimental")
        }
        return proposedSemVer
    }
    
    static def addLoaderVersion(McVersion mcVersion) {
        if (mcVersion.loaderVersion == null) {
            // Attempt lookup in Outlet database as newer MC versions require a loader update
            def v = Outlet.INSTANCE.outletDatabase.versions.find {
                it.id == mcVersion.version
            }
            if (v != null) {
                mcVersion.loaderVersion = fixupSemver(v.normalized)
                println 'Successfully looked up new semver version for: ' + mcVersion.version + ' as ' + mcVersion.loaderVersion
                return
            }

            println 'Creating new semver version...'
            def x = null
            def x_path = null
            while (x == null) {
                try {
                    x_path = mcVersion.artifacts.clientJar.fetchArtifact().toPath()
                    x = McVersionLookup.getVersion(List.of(x_path), mcVersion.mainClass, null)
                } catch (Exception e) {
                    println 'Semver creation failed. Retrying... ' + e.toString()
                    if (x_path != null) {
                        x_path.toFile().delete()
                    }
                    sleep(250)
                }
            }
            mcVersion.loaderVersion = fixupSemver(x.normalized)
            println 'Semver made for: ' + x.raw + ' as ' + mcVersion.loaderVersion
            println 'If generated semver is incorrect, it will break the order of the generated repo. ' +
                'Consider updating Fabric Loader. (run ./gradlew run --refresh-dependencies)'
        }
    }

    static TreeMap<SemanticVersion, McVersion> orderVersionMap(LinkedHashMap<String, McVersion> metadata) {
        def ORDERED_MAP = new TreeMap<SemanticVersion, McVersion>()
        println 'Sorting on semver MC versions...'
        metadata.values().each {it ->
            if (it.hasMappings && !RepoManager.isVersionNonLinearSnapshot(it)) {
                addLoaderVersion(it)
                ORDERED_MAP.put(SemanticVersion.parse(it.loaderVersion), it)
            }
        }

        return ORDERED_MAP
    }

    static TreeMap<SemanticVersion, McVersion> nonLinearVersionList(LinkedHashMap<String, McVersion> metadata) {
        def NONLINEAR_MAP = new TreeMap<SemanticVersion, McVersion>()
        println 'Putting non-linear MC versions into other list...'
        metadata.values().each {it ->
            if (it.hasMappings && RepoManager.isVersionNonLinearSnapshot(it)) {
                addLoaderVersion(it)
                NONLINEAR_MAP.put(SemanticVersion.parse(it.loaderVersion), it)
            }
        }
        println 'The following versions are considered non-linear: ' + NONLINEAR_MAP.values().stream().map { it ->
            it.version
        }.collect(Collectors.joining(", "))
        return NONLINEAR_MAP
    }

    //todo make work
    static def updateMcVersionPath(McVersion mcVersion) {
        def root = GitCraft.MAIN_ARTIFACT_STORE.parent

        if (mcVersion.mergedJar != null) {
            def p = Paths.get(mcVersion.mergedJar)
            def po = p.toString()
            for (int i in 1..p.getNameCount()-1) {
                if (p.getName(i).toString() == 'artifact-store') {
                    p = p.subpath(i, p.getNameCount())
                }
            }

            mcVersion.mergedJar = root.resolve(p)

            println 'Remapped ' + po + ' to ' + mcVersion.mergedJar
        }

        mcVersion.libraries.each {updateArtifactPath(it as Artifact)}
        updateArtifactPath(mcVersion.artifacts.clientMappings)
        updateArtifactPath(mcVersion.artifacts.clientJar)
        updateArtifactPath(mcVersion.artifacts.serverJar)
        updateArtifactPath(mcVersion.artifacts.serverMappings)
    }

    static def updateArtifactPath(Artifact artifact) {
        def root = GitCraft.MAIN_ARTIFACT_STORE.parent
        if (artifact.containingPath != null) {
            def p = artifact.containingPath
            def po = p.toString()
            for (int i in 1..p.getNameCount()-1) {
                if (p.getName(i).toString() == 'artifact-store') {
                    p = p.subpath(i, p.getNameCount())
                }
            }

            artifact.containingPath = root.resolve(p)

            println 'Remapped ' + po + ' to ' + artifact.containingPath
        }
    }

    enum Outlet {
        INSTANCE();

        public McOutletMeta outletDatabase = new McOutletMeta(lastChanged: Date.from(ZonedDateTime.of(2012, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()), versions: [])
        private final def OUTLET_DATABASE = 'https://raw.githubusercontent.com/dexman545/outlet-database/master/mc2fabric.json'
        Outlet() {
            outletDatabase = new JsonSlurper(type: JsonParserType.INDEX_OVERLAY).parse(new URL(OUTLET_DATABASE)) as McOutletMeta
        }
    }
}
