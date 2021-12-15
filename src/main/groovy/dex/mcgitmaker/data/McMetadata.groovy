package dex.mcgitmaker.data

import dex.mcgitmaker.Util
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.nio.file.Path

import static dex.mcgitmaker.GitCraft.*

class McMetadata {
    static final def MC_META_URL = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
    public final LinkedHashMap<String, McVersion> metadata

    McMetadata() {
        metadata = getInitialMetadata()
    }

    McVersion getVersion(String name) {
        return metadata.get(name) as McVersion
    }

    McVersion getVersionFromSemver(String version) {
        return metadata.find {c, m ->
            def x = m as McVersion
            x.loaderVersion == version
        }.value as McVersion
    }

    static Path getMcArtifactRootPath(String version) {
        return MC_VERSION_STORE.resolve(version)
    }

    private static LinkedHashMap<String, McVersion> getInitialMetadata() {
        println 'Creating metadata...'
        println 'Reading metadata from Mojang...'
        def mcVersions = new JsonSlurper().parse(new URL(MC_META_URL))
        def data = new LinkedHashMap<String, McVersion>()
        def read = new LinkedHashMap<String, McVersion>()

        println 'Attempting to read metadata from file...'
        if (METADATA_STORE.toFile().exists()) read = new JsonSlurper().parseText(METADATA_STORE.toFile().text) as LinkedHashMap<String, McVersion>

        read.each {s, v ->
            data.put(s, v as McVersion)
        }

        mcVersions.versions.each { v ->
            if (!data.containsKey(v.id)) {
                println 'Creating data for: ' + v.id
                data.put(v.id, createVersionData(v.url))
            }
        }

        return data
    }

    private static McVersion createVersionData(String metaURL) {
        def meta = new JsonSlurper().parse(new URL(metaURL))

        def libs = new HashSet<Artifact>()

        //todo do we need natives?
        // natives:[linux:natives-linux, osx:natives-osx, windows:natives-windows]
        //downloads.classifiers.fromnativesMap
        meta.libraries.each { d ->
            if (d.downloads.artifact != null)
                libs.add(new Artifact(name: Artifact.nameFromUrl(d.downloads.artifact.url), url: d.downloads.artifact.url, containingPath: LIBRARY_STORE))
        }

        def javaVersion = meta.javaVersion != null ? meta.javaVersion.majorVersion ?: 8 : 8
        def artifacts = getMcArtifacts(meta)

        if (artifacts.hasMappings) {
            getMcArtifactRootPath(meta.id).toFile().mkdirs()
            def x = getMcArtifactRootPath(meta.id).resolve('version.json').toFile()
            x.createNewFile()
            x.write(JsonOutput.toJson(meta))
        }

        return new McVersion(version: meta.id, javaVersion: javaVersion,
                mainClass: meta.mainClass, snapshot: meta.type == 'snapshot', artifacts: artifacts,
                hasMappings: artifacts.hasMappings, libraries: libs)
    }

    private static McArtifacts getMcArtifacts(def meta) {
        Tuple2<Path, String> client = getMcArtifactData(meta.id, meta.downloads.client.url)
        def cmUrl = meta.downloads.client_mappings != null ? meta.downloads.client_mappings.url : ''
        def smUrl = meta.downloads.server_mappings != null ? meta.downloads.server_mappings.url : ''
        Tuple2<Path, String> client_mapping = getMcArtifactData(meta.id, cmUrl)
        Tuple2<Path, String> server = getMcArtifactData(meta.id, meta.downloads.server != null ? meta.downloads.server.url : '')
        Tuple2<Path, String> server_mapping = getMcArtifactData(meta.id, smUrl)

        def clientArtifact = new Artifact(containingPath: client.getV1(), url: meta.downloads.client.url, name: client.getV2())
        def clientMappingArtifact = new Artifact(containingPath: client_mapping.getV1(), url: cmUrl, name: client_mapping.getV2())
        def serverArtifact = new Artifact(containingPath: server.getV1(), url: meta.downloads.server != null ? meta.downloads.server.url : '', name: server.getV2())
        def serverMappingArtifact = new Artifact(containingPath: server_mapping.getV1(), url: smUrl, name: server_mapping.getV2())

        boolean hasMappings = smUrl != '' && smUrl != ''
        return new McArtifacts(hasMappings: hasMappings, clientJar: clientArtifact, clientMappings: clientMappingArtifact, serverJar: serverArtifact, serverMappings: serverMappingArtifact)
    }

    // containing path, file name
    private static Tuple2<String, String> getMcArtifactData(String version, String url) {
        return new Tuple2<>(getMcArtifactRootPath(version).toString(), Artifact.nameFromUrl(url))
    }
}
