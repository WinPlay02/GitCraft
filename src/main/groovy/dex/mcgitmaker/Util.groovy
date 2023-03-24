package dex.mcgitmaker

import dex.mcgitmaker.data.Artifact
import dex.mcgitmaker.data.McVersion
import groovy.json.JsonGenerator
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup

import java.nio.file.Path
import java.nio.file.Paths

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

    static def addLoaderVersion(McVersion mcVersion) {
        if (mcVersion.loaderVersion == null) {
            println 'Creating new semver version...'
            def x = McVersionLookup.getVersion(List.of(mcVersion.artifacts.clientJar.fetchArtifact().toPath()), mcVersion.mainClass, null)
            mcVersion.loaderVersion = x.normalized
            println 'Semver made for: ' + x.raw + ' as ' + x.normalized
        }
    }

    static TreeMap<SemanticVersion, McVersion> orderVersionMap(LinkedHashMap<String, McVersion> metadata) {
        def ORDERED_MAP = new TreeMap<SemanticVersion, McVersion>()
        println 'Sorting on semver MC versions...'
        metadata.values().each {it ->
            if (it.hasMappings) {
                addLoaderVersion(it)
                ORDERED_MAP.put(SemanticVersion.parse(it.loaderVersion), it)
            }
        }

        return ORDERED_MAP
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
}
