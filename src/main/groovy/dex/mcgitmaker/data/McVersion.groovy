package dex.mcgitmaker.data

import dex.mcgitmaker.GitCraft
import dex.mcgitmaker.Util
import dex.mcgitmaker.loom.BundleMetadata
import dex.mcgitmaker.loom.Decompiler
import dex.mcgitmaker.loom.Remapper
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.MappingFormat
import net.fabricmc.mappingio.format.ProGuardReader
import net.fabricmc.mappingio.format.Tiny2Writer
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.stitch.merge.JarMerger
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.TinyUtils

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class McVersion {
    String version // MC version string from launcher
    String loaderVersion // Version from Fabric loader
    boolean snapshot // If the version is a release
    boolean hasMappings // If the version has mappings provided
    int javaVersion = 8
    McArtifacts artifacts
    Collection<Artifact> libraries // The libraries for this version
    String mainClass
    String mergedJar // merged client and server
    
    String time
    String assets_index

    File decompiledMc() {
        def p = Decompiler.decompiledPath(this)
        def f = p.toFile()
        if (!f.exists()) {
            Decompiler.decompile(this)
        }

        return f
    }

    File remappedJar() {
        return Remapper.doRemap(this).toFile()
    }

    Path mergedJarPath() {
        return mergedJar == null ? GitCraft.MC_VERSION_STORE.resolve(version).resolve('merged-jar.jar') : Paths.get(mergedJar)
    }

    File merged() {
        def p = mergedJarPath()
        def f = p.toFile()
        if (!f.exists()) makeMergedJar()
        return f
    }

    IMappingProvider mappingsProvider() {
        return TinyUtils.createTinyMappingProvider(mappingsPath(),
                Util.MappingsNamespace.OFFICIAL.toString(), Util.MappingsNamespace.MOJMAP.toString())
    }

    Path mappingsPath() {
        def mappingsFile = GitCraft.MAPPINGS.resolve(version + '.tiny')

        if (!mappingsFile.toFile().exists()) {
            MemoryMappingTree mappingTree = new MemoryMappingTree()

            // Make official the source namespace
            MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, Util.MappingsNamespace.OFFICIAL.toString())

            try (BufferedReader clientBufferedReader = Files.newBufferedReader(artifacts.clientMappings.fetchArtifact().toPath(), StandardCharsets.UTF_8)
                 BufferedReader serverBufferedReader = Files.newBufferedReader(artifacts.serverMappings.fetchArtifact().toPath(), StandardCharsets.UTF_8)) {
                ProGuardReader.read(clientBufferedReader as Reader, Util.MappingsNamespace.MOJMAP.toString(), Util.MappingsNamespace.OFFICIAL.toString(), nsSwitch)
                ProGuardReader.read(serverBufferedReader as Reader, Util.MappingsNamespace.MOJMAP.toString(), Util.MappingsNamespace.OFFICIAL.toString(), nsSwitch)
            }

            def w = Tiny2Writer.create(mappingsFile, MappingFormat.TINY_2)
            mappingTree.accept(w)
            w.close()
        }

        return mappingsFile
    }

    void makeMergedJar() {
        println 'Merging jars... ' + version
        def client = artifacts.clientJar.fetchArtifact()
        def server2merge = artifacts.serverJar.fetchArtifact()

        def sbm = BundleMetadata.fromJar(server2merge.toPath())
        if (sbm != null) {
            def minecraftExtractedServerJar = GitCraft.MC_VERSION_STORE
                    .resolve(version).resolve('extracted-server.jar')

            if (sbm.versions.size() != 1) {
                throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(serverBundleMetadata.versions().size()));
            }

            sbm.versions.first().unpackEntry(server2merge.toPath(), minecraftExtractedServerJar)
            server2merge = minecraftExtractedServerJar.toFile()
        }

        try (def jarMerger = new JarMerger(client, server2merge, mergedJarPath().toFile())) {
            jarMerger.enableSyntheticParamsOffset()
            jarMerger.merge()
        }

        mergedJar = mergedJarPath().toString()
    }
}
