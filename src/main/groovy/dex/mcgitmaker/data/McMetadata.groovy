package dex.mcgitmaker.data

import dex.mcgitmaker.Util
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static dex.mcgitmaker.GitCraft.*

class McMetadata {
    static final def MC_META_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
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
            def version = v as McVersion
            data.put(s, version)
        }

        mcVersions.versions.each { v ->
            if (!data.containsKey(v.id)) {
                data.put(v.id, createVersionData(v.id, v.url, v.sha1))
            }
        }
        
        for (File extra_version : SOURCE_EXTRA_VERSIONS.toFile().listFiles()) {
            if (extra_version.getName().equals(".gitkeep")) { // silently ignore gitkeep
                continue
            }
            if (!extra_version.toPath().toString().endsWith('.json')) {
                println 'Skipped extra version \'' + extra_version.toPath().toString() + '\' as it is not a .json file'
                continue
            }
            def extra_version_object = createVersionDataFromExtra(extra_version.toPath(), data);
            if(extra_version_object != null) {
                data.put(extra_version_object.version, extra_version_object)
                println 'Applied extra version \'' + extra_version_object.version + '\''
            }
        }

        return data
    }

    private static McVersion createVersionDataFromExtra(Path pExtraFile, LinkedHashMap<String, McVersion> dataVersions) {
        def meta = new JsonSlurper().parseText(pExtraFile.toFile().text)
        if (dataVersions.containsKey(meta.id)) {
            return null;
        }
        return createVersionData(meta, pExtraFile, null)
    }

    private static McVersion createVersionData(String metaID, String metaURL, String metaSha1) {
        def metaFile = META_CACHE.resolve(metaID + ".json").toFile()
        Util.downloadToFileWithChecksumIfNotExists(metaURL, metaFile.toPath(), metaSha1, "version meta", metaID)
        def meta = new JsonSlurper().parseText(metaFile.text)
        return createVersionData(meta, metaFile.toPath(), metaSha1)
    }
        
    private static McVersion createVersionData(def meta, Path sourcePath, String metaSha1) {
        def libs = new HashSet<Artifact>()

        // Ignores natives, not needed as we don't have a runtime
        meta.libraries.each { d ->
            if (d.downloads.artifact != null)
                libs.add(new Artifact(name: Artifact.nameFromUrl(d.downloads.artifact.url), url: d.downloads.artifact.url, containingPath: LIBRARY_STORE, sha1sum: d.downloads.artifact.sha1))
        }

        fetchAssets(meta)

        def javaVersion = meta.javaVersion != null ? meta.javaVersion.majorVersion ?: 8 : 8
        def artifacts = getMcArtifacts(meta)

        if (artifacts.hasMappings) {
            getMcArtifactRootPath(meta.id).toFile().mkdirs()
            def targetPath = getMcArtifactRootPath(meta.id).resolve('version.json')
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            if (!Util.checksumCheckFileIsValidAndExists(targetPath.toFile(), metaSha1, "stored version meta", meta.id, false)) {
                throw new RuntimeException("A valid stored version meta for ${meta.id} does not exist")
            }
        }

        return new McVersion(version: meta.id, javaVersion: javaVersion,
                mainClass: meta.mainClass, snapshot: meta.type == 'snapshot' || meta.type == 'pending', artifacts: artifacts,
                hasMappings: artifacts.hasMappings, libraries: libs, time: meta.time, assets_index: meta.assets)
    }

    private static def fetchAssetsIndex(String assetsId, String url, String sha1Hash) {
        def targetFile = ASSETS_INDEX.resolve(assetsId + ".json").toFile()

        if (!Util.checksumCheckFileIsValidAndExists(targetFile, sha1Hash, "assets index", assetsId, false) && url == null) {
            throw new RuntimeException("assets index ${assetsId} is expected to be already downloaded but it is missing")
        }

        Util.downloadToFileWithChecksumIfNotExists(url, targetFile.toPath(), sha1Hash, "assets index", assetsId)

        def assetsIndex = new JsonSlurper().parseText(targetFile.text)
        return assetsIndex
    }

    private static def fetchAssets(def meta) {
        def assetsIndex = fetchAssetsIndex(meta.id + "_" + meta.assets, meta.assetIndex.url, meta.assetIndex.sha1)
        assetsIndex.objects.each{file_name, info -> 
            new Artifact(name: info.hash, url: "https://resources.download.minecraft.net/" + info.hash.substring(0, 2) + "/" + info.hash, containingPath: ASSETS_OBJECTS, sha1sum: info.hash).fetchArtifact()
        }
    }

    public static def copyExternalAssetsToRepo(McVersion version) {
        def assetsIndex = fetchAssetsIndex(version.version + "_" + version.assets_index, null, null)
        def targetRoot = REPO.resolve('minecraft').resolve('external-resources').resolve('assets')
        assetsIndex.objects.each{file_name, info -> 
            def sourcePath = ASSETS_OBJECTS.resolve(info.hash)
            def targetPath = targetRoot.resolve(file_name)
            targetPath.parent.toFile().mkdirs()
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private static McArtifacts getMcArtifacts(def meta) {
        Tuple2<Path, String> client = getMcArtifactData(meta.id, meta.downloads.client.url)
        def cmUrl = meta.downloads.client_mappings != null ? meta.downloads.client_mappings.url : ''
        def cmSha1 = meta.downloads.client_mappings != null ? meta.downloads.client_mappings.sha1 : null
        def smUrl = meta.downloads.server_mappings != null ? meta.downloads.server_mappings.url : ''
        def smSha1 = meta.downloads.server_mappings != null ? meta.downloads.server_mappings.sha1 : null
        Tuple2<Path, String> client_mapping = getMcArtifactData(meta.id, cmUrl)
        Tuple2<Path, String> server = getMcArtifactData(meta.id, meta.downloads.server != null ? meta.downloads.server.url : '')
        Tuple2<Path, String> server_mapping = getMcArtifactData(meta.id, smUrl)

        def clientArtifact = new Artifact(containingPath: client.getV1(), url: meta.downloads.client.url, name: client.getV2(), sha1sum: meta.downloads.client.sha1)
        def clientMappingArtifact = new Artifact(containingPath: client_mapping.getV1(), url: cmUrl, name: client_mapping.getV2(), sha1sum: cmSha1)
        def serverArtifact = new Artifact(containingPath: server.getV1(), url: meta.downloads.server != null ? meta.downloads.server.url : '', name: server.getV2(), sha1sum: meta.downloads.server != null ? meta.downloads.server.sha1 : '')
        def serverMappingArtifact = new Artifact(containingPath: server_mapping.getV1(), url: smUrl, name: server_mapping.getV2(), sha1sum: smSha1)

        boolean hasMappings = smUrl != '' && smUrl != ''
        return new McArtifacts(hasMappings: hasMappings, clientJar: clientArtifact, clientMappings: clientMappingArtifact, serverJar: serverArtifact, serverMappings: serverMappingArtifact)
    }

    // containing path, file name
    private static Tuple2<String, String> getMcArtifactData(String version, String url) {
        return new Tuple2<>(getMcArtifactRootPath(version).toString(), Artifact.nameFromUrl(url))
    }
}
