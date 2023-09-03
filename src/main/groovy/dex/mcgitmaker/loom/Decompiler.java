package dex.mcgitmaker.loom;

import com.github.winplay02.MappingHelper;
import com.github.winplay02.MiscHelper;
import com.github.winplay02.SerializationHelper;
import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.NIODirectoryResultSaver;
import dex.mcgitmaker.data.Artifact;
import dex.mcgitmaker.data.McVersion;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Decompiler {
	private static final PrintStream NULL_IS = new PrintStream(OutputStream.nullOutputStream());

	public static Path decompiledPath(McVersion mcVersion, MappingHelper.MappingFlavour mappingFlavour) {
		return GitCraft.DECOMPILED_WORKINGS.resolve(String.format("%s-%s.jar", mcVersion.version, mappingFlavour.toString()));
	}

	// Adapted from loom-quiltflower by Juuxel
	public static void decompile(McVersion mcVersion, MappingHelper.MappingFlavour mappingFlavour) throws IOException {
		MiscHelper.println("Decompiling: %s...", mcVersion.version);
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
		FileSystemUtil.Delegate decompiledJar = FileSystemUtil.getJarFileSystem(decompiledPath(mcVersion, mappingFlavour), true);
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

		Path mc_file = Remapper.doRemap(mcVersion, mappingFlavour);
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

		List<Artifact.DependencyArtifact> c = Stream.concat(
						Arrays.stream(new Artifact.DependencyArtifact[]{Artifact.DependencyArtifact.ofVirtual("Java " + mcVersion.javaVersion)}),
						mcVersion.libraries.stream().map(Artifact.DependencyArtifact::new).sorted(Comparator.comparing(artifact -> String.join("", artifact.name().split("-")))))
				.collect(Collectors.toList());

		SerializationHelper.writeAllToPath(p, SerializationHelper.serialize(c));
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
