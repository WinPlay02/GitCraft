package dex.mcgitmaker.loom;

import com.github.winplay02.MiscHelper;
import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.NIODirectoryResultSaver;
import dex.mcgitmaker.data.Artifact;
import dex.mcgitmaker.data.McVersion;
import groovy.json.JsonGenerator;
import groovy.json.JsonOutput;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Decompiler {
	private static final PrintStream NULL_IS = new PrintStream(OutputStream.nullOutputStream());

	public static Path decompiledPath(McVersion mcVersion) {
		return GitCraft.DECOMPILED_WORKINGS.resolve(mcVersion.version + "-mojmap.jar"); // TODO other mappings?
	}

	// Adapted from loom-quiltflower by Juuxel
	public static void decompile(McVersion mcVersion) throws IOException {
		MiscHelper.println("Decompiling: %s...", mcVersion.version);
		MiscHelper.println("Decompiler log output is suppressed!");
		Map<String, Object> options = new HashMap<>();

		options.put(IFernflowerPreferences.INDENT_STRING, "\t");
		options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
		options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
		options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
		options.put(IFernflowerPreferences.LOG_LEVEL, "trace");
		options.put(IFernflowerPreferences.THREADS, Integer.toString(GitCraft.config.decompilingThreads));
		//options.put(IFabricJavadocProvider.PROPERTY_NAME, new QfTinyJavadocProvider(metaData.javaDocs().toFile()));

		// Experimental QF preferences
		options.put(IFernflowerPreferences.PATTERN_MATCHING, "1");
		options.put(IFernflowerPreferences.TRY_LOOP_FIX, "1");
		//options.putAll(ReflectionUtil.<Map<String, String>>maybeGetFieldOrRecordComponent(metaData, "options").orElse(Map.of()));

		List<FileSystemUtil.Delegate> openFileSystems = new ArrayList<>();
		FileSystemUtil.Delegate decompiledJar = FileSystemUtil.getJarFileSystem(decompiledPath(mcVersion), true);
		openFileSystems.add(decompiledJar);
		Iterator<Path> resultFsIt = decompiledJar.get().getRootDirectories().iterator();
		if (!resultFsIt.hasNext()) {
			throw new RuntimeException("Zip FileSystem does not have any root directories");
		}
		Path targetJarRootPath = resultFsIt.next();

		Fernflower ff = new Fernflower(Zips::getBytes, new NIODirectoryResultSaver(targetJarRootPath), options, new PrintStreamLogger(NULL_IS)); // System.out

		MiscHelper.println("Adding libraries...");
		for (Artifact library : mcVersion.libraries) {
			File lib_file = library.fetchArtifact();
			openFileSystems.add(FileSystemUtil.getJarFileSystem(lib_file, false));
			ff.addLibrary(lib_file);
		}

		Path mc_file = mcVersion.remappedJar();
		openFileSystems.add(FileSystemUtil.getJarFileSystem(mc_file, false));
		ff.addSource(mc_file.toFile());

		MiscHelper.executeTimedStep("Decompiling...", ff::decompileContext);

		MiscHelper.println("Writing dependencies file...");
		writeLibraries(targetJarRootPath, mcVersion);

		MiscHelper.println("Closing temporary FileSystems delayed...");
		for (FileSystemUtil.Delegate fs : openFileSystems) {
			fs.close();
		}
	}

	private static void writeLibraries(Path parentDirectory, McVersion mcVersion) throws IOException {
		Path p = parentDirectory.resolve("dependencies.json");
		JsonGenerator generator = new JsonGenerator.Options()
				.excludeFieldsByName("containingPath")
				.build();

		List<Artifact> c = mcVersion.libraries.stream().sorted(Comparator.comparing(artifact -> String.join("", artifact.name().split("-")))).collect(Collectors.toList());
		c.add(Artifact.ofVirtual("Java " + mcVersion.javaVersion));

		Files.writeString(p, JsonOutput.prettyPrint(generator.toJson(c)), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
