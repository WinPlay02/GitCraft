package com.github.winplay02.gitcraft.pipeline.workers;

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
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.util.FFNIODirectoryResultSaver;
import com.github.winplay02.gitcraft.util.MiscHelper;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.loom.decompilers.vineflower.TinyJavadocProvider;
import net.fabricmc.loom.util.FileSystemUtil;

public record Decompiler(StepWorker.Config config) implements StepWorker<OrderedVersion, Decompiler.Inputs> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Decompiler.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		Files.createDirectories(pipeline.getStoragePath(PipelineFilesystemStorage.DECOMPILED, context));
		StepOutput<OrderedVersion> mergedStatus = decompileJar(pipeline, context, input.mergedJar().orElse(null), "merged", PipelineFilesystemStorage.DECOMPILED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = decompileJar(pipeline, context, input.clientJar().orElse(null), "client", PipelineFilesystemStorage.DECOMPILED_CLIENT_JAR);
		StepOutput<OrderedVersion> serverStatus = decompileJar(pipeline, context, input.serverJar().orElse(null), "server", PipelineFilesystemStorage.DECOMPILED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}

	private static final PrintStream NULL_IS = new PrintStream(OutputStream.nullOutputStream());

	private StepOutput<OrderedVersion> decompileJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StorageKey inputFile, String artifactKind, StorageKey outputFile) throws IOException {
		if (inputFile == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarIn = pipeline.getStoragePath(inputFile, context);
		if (jarIn == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarOut = pipeline.getStoragePath(outputFile, context);

		if (Files.exists(jarOut) && !MiscHelper.isJarEmpty(jarOut)) {
			return StepOutput.ofSingle(StepStatus.UP_TO_DATE, outputFile);
		}
		if (Files.exists(jarOut)) {
			Files.delete(jarOut);
		}
		Path librariesDir = pipeline.getStoragePath(PipelineFilesystemStorage.LIBRARIES, context);
		if (librariesDir == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.FAILED);
		}
		// Adapted from loom-quiltflower by Juuxel
		Map<String, Object> options = new HashMap<>();

		options.put(IFernflowerPreferences.INDENT_STRING, "\t");
		options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
		options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
		options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
		options.put(IFernflowerPreferences.LOG_LEVEL, "trace");
		options.put(IFernflowerPreferences.THREADS, Integer.toString(Library.CONF_GLOBAL.decompilingThreads()));

		// Experimental QF preferences
		options.put(IFernflowerPreferences.PATTERN_MATCHING, "1");
		options.put(IFernflowerPreferences.TRY_LOOP_FIX, "1");
		if (config.mappingFlavour().supportsComments()) {
			// TODO: this will break for mapping flavours that support unpicking but for the client and server separately
			options.put(IFabricJavadocProvider.PROPERTY_NAME, new TinyJavadocProvider(config.mappingFlavour().getPath(context.targetVersion(), MinecraftJar.MERGED).orElseThrow().toFile()));
		}

		try (FileSystemUtil.Delegate decompiledJar = FileSystemUtil.getJarFileSystem(jarOut, true)) {
			Iterator<Path> resultFsIt = decompiledJar.get().getRootDirectories().iterator();
			if (!resultFsIt.hasNext()) {
				throw new RuntimeException("Zip FileSystem does not have any root directories");
			}
			Path targetJarRootPath = resultFsIt.next();

			Fernflower ff = new Fernflower(new FFNIODirectoryResultSaver(targetJarRootPath, null), options, new PrintStreamLogger(NULL_IS)); // System.out
			if (librariesDir != null) {
				for (Artifact library : context.targetVersion().libraries()) {
					Path lib_file = library.resolve(librariesDir);
					// TODO add library via NIO
					ff.addLibrary(lib_file.toFile());
				}
			}
			// TODO add source via NIO
			ff.addSource(jarIn.toFile());
			MiscHelper.executeTimedStep(String.format("Decompiling %s...", artifactKind), ff::decompileContext);
			// Should release file handles, if exists
			ff.clearContext();

			MiscHelper.println("Writing dependencies file...");
			Path p = targetJarRootPath.resolve("dependencies.json");

			List<Artifact.DependencyArtifact> c = Stream.concat(
							Arrays.stream(new Artifact.DependencyArtifact[]{Artifact.DependencyArtifact.ofVirtual("Java " + context.targetVersion().javaVersion())}),
							context.targetVersion().libraries().stream().map(Artifact.DependencyArtifact::new).sorted(Comparator.comparing(artifact -> String.join("", artifact.name().split("-")))))
					.collect(Collectors.toList());

			SerializationHelper.writeAllToPath(p, SerializationHelper.serialize(c));
		}
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}
}
