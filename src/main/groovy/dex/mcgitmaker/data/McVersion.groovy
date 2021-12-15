package dex.mcgitmaker.data

import dex.mcgitmaker.GitCraft
import dex.mcgitmaker.Util
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
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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
        try (def jarMerger = new JarMerger(client, serverJarToMerge(), mergedJarPath().toFile())) {
            jarMerger.enableSyntheticParamsOffset()
            jarMerger.merge()
        }

        mergedJar = mergedJarPath().toString()
    }

    // From Fabric Loom
    // Extracts the jar from the bundle used in 2138a+
    private File serverJarToMerge() {
        def minecraftExtractedServerJar = GitCraft.MC_VERSION_STORE
                .resolve(version).resolve('extracted-server.jar').toFile()
        def server = artifacts.serverJar.fetchArtifact()
        try (ZipFile zipFile = new ZipFile(server)) {
            ZipEntry versionsListEntry = zipFile.getEntry("META-INF/versions.list");

            if (versionsListEntry == null) {
                // Legacy pre 21w38a jar
                return server
            }

            String versionsList

            try (InputStream is = zipFile.getInputStream(versionsListEntry)) {
                versionsList = new String(is.readAllBytes(), StandardCharsets.UTF_8)
            }

            String jarPath = null
            String[] versions = versionsList.split("\n")

            if (versions.length != 1) {
                throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(versions.length))
            }

            for (String version : versions) {
                if (version.isBlank()) continue

                String[] split = version.split("\t")

                if (split.length != 3) continue

                final String hash = split[0]
                final String id = split[1]
                final String path = split[2]

                // Take the first (only) version we find.
                jarPath = path
                break
            }

            Objects.requireNonNull(jarPath, "Could not find minecraft server jar for " + version)
            ZipEntry serverJarEntry = zipFile.getEntry("META-INF/versions/" + jarPath)
            Objects.requireNonNull(serverJarEntry, "Could not find server jar in boostrap@ " + jarPath)

            try (InputStream is = zipFile.getInputStream(serverJarEntry)) {
                Files.copy(is, minecraftExtractedServerJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            return minecraftExtractedServerJar
        }
    }
}
