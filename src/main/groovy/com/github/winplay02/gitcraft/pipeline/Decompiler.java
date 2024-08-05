package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.util.FFNIODirectoryResultSaver;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.loom.decompilers.vineflower.TinyJavadocProvider;
import net.fabricmc.loom.util.FileSystemUtil;

public record Decompiler(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		StepStatus mergedStatus = decompileJar(pipeline, context, MinecraftJar.MERGED, Results.MINECRAFT_MERGED_JAR);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = decompileJar(pipeline, context, MinecraftJar.CLIENT, Results.MINECRAFT_CLIENT_JAR);
		StepStatus serverStatus = decompileJar(pipeline, context, MinecraftJar.SERVER, Results.MINECRAFT_SERVER_JAR);
		return StepStatus.merge(clientStatus, serverStatus);
	}

	private static final PrintStream NULL_IS = new PrintStream(OutputStream.nullOutputStream());

	private StepStatus decompileJar(Pipeline pipeline, Context context, MinecraftJar inFile, StepResult outFile) throws IOException {
		Path jarIn = pipeline.getMinecraftJar(inFile);
		if (jarIn == null) {
			return StepStatus.NOT_RUN;
		}
		Path jarOut = pipeline.initResultFile(step, context, outFile);
		if (Files.exists(jarOut) && Files.size(jarOut) > 22 /* not empty jar */) {
			return StepStatus.UP_TO_DATE;
		}
		if (Files.exists(jarOut)) {
			Files.delete(jarOut);
		}
		Path librariesDir = pipeline.getResultFile(LibrariesFetcher.Results.LIBRARIES_DIRECTORY);
		if (librariesDir == null) {
			return StepStatus.FAILED;
		}
		// Adapted from loom-quiltflower by Juuxel
		Map<String, Object> options = new HashMap<>();

		options.put(IFernflowerPreferences.INDENT_STRING, "\t");
		options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
		options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
		options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
		options.put(IFernflowerPreferences.LOG_LEVEL, "trace");
		options.put(IFernflowerPreferences.THREADS, Integer.toString(GitCraft.config.decompilingThreads));

		// Experimental QF preferences
		options.put(IFernflowerPreferences.PATTERN_MATCHING, "1");
		options.put(IFernflowerPreferences.TRY_LOOP_FIX, "1");
		if (config.mappingFlavour().getMappingImpl().supportsComments()) {
			// TODO: this will break for mapping flavours that support unpicking but for the client and server separately
			options.put(IFabricJavadocProvider.PROPERTY_NAME, new TinyJavadocProvider(config.mappingFlavour().getMappingImpl().getMappingsPath(context.minecraftVersion(), MinecraftJar.MERGED).orElseThrow().toFile()));
		}

		try (FileSystemUtil.Delegate decompiledJar = FileSystemUtil.getJarFileSystem(jarOut, true)) {
			Iterator<Path> resultFsIt = decompiledJar.get().getRootDirectories().iterator();
			if (!resultFsIt.hasNext()) {
				throw new RuntimeException("Zip FileSystem does not have any root directories");
			}
			Path targetJarRootPath = resultFsIt.next();

			Fernflower ff = new Fernflower(new FFNIODirectoryResultSaver(targetJarRootPath, null), options, new PrintStreamLogger(NULL_IS)); // System.out
			if (librariesDir != null) {
				for (Artifact library : context.minecraftVersion().libraries()) {
					Path lib_file = library.resolve(librariesDir);
					// TODO add library via NIO
					ff.addLibrary(lib_file.toFile());
				}
			}
			// TODO add source via NIO
			ff.addSource(jarIn.toFile());
			MiscHelper.executeTimedStep(String.format("Decompiling %s...", inFile.name().toLowerCase()), ff::decompileContext);
			// Should release file handles, if exists
			ff.clearContext();

			MiscHelper.println("Writing dependencies file...");
			Path p = targetJarRootPath.resolve("dependencies.json");

			List<Artifact.DependencyArtifact> c = Stream.concat(
							Arrays.stream(new Artifact.DependencyArtifact[]{Artifact.DependencyArtifact.ofVirtual("Java " + context.minecraftVersion().javaVersion())}),
							context.minecraftVersion().libraries().stream().map(Artifact.DependencyArtifact::new).sorted(Comparator.comparing(artifact -> String.join("", artifact.name().split("-")))))
					.collect(Collectors.toList());

			SerializationHelper.writeAllToPath(p, SerializationHelper.serialize(c));
		}
		return StepStatus.SUCCESS;
	}

	public enum Results implements StepResult {
		DECOMPILED_JARS_DIRECTORY, MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_MERGED_JAR
	}
}
