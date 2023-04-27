package dex.mcgitmaker.loom

import dex.mcgitmaker.GitCraft
import dex.mcgitmaker.data.Artifact
import dex.mcgitmaker.data.McVersion
import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import net.fabricmc.stitch.util.StitchUtil
import org.jetbrains.java.decompiler.main.Fernflower
import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences

import java.nio.file.Files
import java.nio.file.Path

import dex.mcgitmaker.loom.FileSystemUtil

class Decompiler {
    private static final def NULL_IS = new PrintStream(OutputStream.nullOutputStream())

    static Path decompiledPath(McVersion mcVersion) {
        return GitCraft.DECOMPILED_WORKINGS.resolve(mcVersion.version + ".jar")
    }

    // Adapted from loom-quiltflower by Juuxel
    static def decompile(McVersion mcVersion) {
        println 'Decompiling: ' + mcVersion.version + '...'
        println 'Decompiler log output is suppressed!'
        Map<String, Object> options = new HashMap<>()

        options.put(IFernflowerPreferences.INDENT_STRING, "\t");
        options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
        options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
        options.put(IFernflowerPreferences.LOG_LEVEL, "trace");
        options.put(IFernflowerPreferences.THREADS, Integer.toString(Runtime.getRuntime().availableProcessors() - 3));
        //options.put(IFabricJavadocProvider.PROPERTY_NAME, new QfTinyJavadocProvider(metaData.javaDocs().toFile()));

        // Experimental QF preferences
        options.put(IFernflowerPreferences.PATTERN_MATCHING, "1");
        options.put(IFernflowerPreferences.TRY_LOOP_FIX, "1");
        //options.putAll(ReflectionUtil.<Map<String, String>>maybeGetFieldOrRecordComponent(metaData, "options").orElse(Map.of()));

        java.util.List<FileSystemUtil.Delegate> openFileSystems = new java.util.ArrayList<>()
        FileSystemUtil.Delegate decompiledJar = FileSystemUtil.getJarFileSystem(decompiledPath(mcVersion), true)
        openFileSystems.add(decompiledJar)
        def resultFsIt = decompiledJar.get().getRootDirectories().iterator()
        if (!resultFsIt.hasNext()) {
            throw new RuntimeException("Zip FileSystem does not have any root directories")
        }

        Fernflower ff = new Fernflower(Zips::getBytes, new DirectoryResultSaver(resultFsIt.next().toFile()), options, new PrintStreamLogger(NULL_IS)) // System.out

        println 'Adding libraries...'
        for (Artifact library : mcVersion.libraries) {
            def lib_file = library.fetchArtifact()
            openFileSystems.add(FileSystemUtil.getJarFileSystem(lib_file, false))
            ff.addLibrary(lib_file)
        }

        def mc_file = mcVersion.remappedJar()
        openFileSystems.add(FileSystemUtil.getJarFileSystem(mc_file, false))
        ff.addSource(mc_file)

        println 'Decompiling...'
        ff.decompileContext()

        println 'Writing dependencies file...'
        writeLibraries(mcVersion)
        
        println 'Closing temporary FileSystems delayed...'
        for (FileSystemUtil.Delegate fs : openFileSystems) {
            fs.close()
        }
    }

    private static void writeLibraries(McVersion mcVersion) {
        def p = GitCraft.DECOMPILED_WORKINGS.resolve(mcVersion.version).resolve('dependencies.json')
        def generator = new JsonGenerator.Options()
                .excludeFieldsByName('containingPath')
                .build()

        def c = mcVersion.libraries.collect().sort {
            def names = it.name.split('-').dropRight(1)
            names.join("")
        }
        c.push(new Artifact(url: '', name: 'Java ' + mcVersion.javaVersion, containingPath: ''))

        def x = p.toFile()
        x.createNewFile()
        x.write(JsonOutput.prettyPrint(generator.toJson(c)))
    }

    // Adapted from loom-quiltflower by Juuxel
    static final class Zips {
        static byte[] getBytes(String outerPath, String innerPath) throws IOException {
            if (innerPath == null) {
                return Files.readAllBytes(Path.of(outerPath));
            }

            try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(new File(outerPath), false)) {
                return Files.readAllBytes(fs.get().getPath(innerPath));
            }
        }
    }
}
