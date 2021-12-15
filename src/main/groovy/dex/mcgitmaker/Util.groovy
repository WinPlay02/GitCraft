package dex.mcgitmaker

import dex.mcgitmaker.data.McVersion
import groovy.json.JsonGenerator
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup

import java.nio.file.Path

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
            def x = McVersionLookup.getVersion(mcVersion.artifacts.clientJar.fetchArtifact().toPath(), new String[]{mcVersion.mainClass}, null)
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
}
