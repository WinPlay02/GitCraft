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

class Decompiler {
    private static final def NULL_IS = new PrintStream(OutputStream.nullOutputStream())

    static Path decompiledPath(McVersion mcVersion) {
        return GitCraft.DECOMPILED_WORKINGS.resolve(mcVersion.version)
    }

    // Adapted from loom-quiltflower by Juuxel
    static def decompile(McVersion mcVersion) {
        println 'Decompiling: ' + mcVersion.version + '...'
        println 'Decompiler log output is suppressed!'
        Map<String, Object> options = new HashMap<>()

        options.put(IFernflowerPreferences.INDENT_STRING, " ".repeat(4)); // space supremacy!
        options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
        options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
        options.put(IFernflowerPreferences.LOG_LEVEL, "trace");
        options.put(IFernflowerPreferences.THREADS, Integer.toString(Runtime.getRuntime().availableProcessors() - 3));
        //options.put(IFabricJavadocProvider.PROPERTY_NAME, new QfTinyJavadocProvider(metaData.javaDocs().toFile()));

        // Experimental QF preferences
        options.put(IFernflowerPreferences.PATTERN_MATCHING, "1");
        options.put(IFernflowerPreferences.EXPERIMENTAL_TRY_LOOP_FIX, "1");
        //options.putAll(ReflectionUtil.<Map<String, String>>maybeGetFieldOrRecordComponent(metaData, "options").orElse(Map.of()));

        Fernflower ff = new Fernflower(Zips::getBytes, new DirectoryResultSaver(decompiledPath(mcVersion).toFile()), options, new PrintStreamLogger(/*System.out*/NULL_IS))

        println 'Adding libraries...'
        for (Artifact library : mcVersion.libraries) {
            ff.addLibrary(library.fetchArtifact())
        }

        ff.addSource(mcVersion.remappedJar())

        println 'Decompiling...'
        ff.decompileContext()

        println 'Writing dependencies file...'
        writeLibraries(mcVersion)
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

            try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(new File(outerPath), false)) {
                return Files.readAllBytes(fs.get().getPath(innerPath));
            }
        }
    }
}
