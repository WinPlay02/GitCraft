package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FFNIODirectoryResultSaver;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.loom.decompilers.vineflower.TinyJavadocProvider;
import net.fabricmc.loom.util.FileSystemUtil;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

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

public class DecompileStep extends Step {

	private final Path rootPath;

	public DecompileStep(Path rootPath) {
		this.rootPath = rootPath;
	}

	public DecompileStep() {
		this(GitCraftPaths.DECOMPILED_WORKINGS);
	}

	@Override
	public String getName() {
		return Step.STEP_DECOMPILE;
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return this.rootPath.resolve(String.format("%s-%s.jar", mcVersion.launcherFriendlyVersionName(), mappingFlavour.toString()));
	}

	private static final PrintStream NULL_IS = new PrintStream(OutputStream.nullOutputStream());

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		Path decompiledPath = getInternalArtifactPath(mcVersion, mappingFlavour);
		if (Files.exists(decompiledPath) && Files.size(decompiledPath) > 22 /* not empty jar */) {
			return StepResult.UP_TO_DATE;
		}
		if (Files.exists(decompiledPath)) {
			Files.delete(decompiledPath);
		}

		Path remappedPath = pipelineCache.getForKey(Step.STEP_REMAP);
		// TODO if remapping did not happen, do something useful; Maybe decompile raw?
		if (remappedPath == null) {
			MiscHelper.panic("A remapped JAR for version %s does not exist", mcVersion.launcherFriendlyVersionName());
		}

		Path libraryPath = pipelineCache.getForKey(Step.STEP_FETCH_LIBRARIES);
		if (libraryPath == null) {
			MiscHelper.panic("Libraries for version %s do not exist", mcVersion.launcherFriendlyVersionName());
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
		if (mappingFlavour.getMappingImpl().supportsComments()) {
			options.put(IFabricJavadocProvider.PROPERTY_NAME, new TinyJavadocProvider(mappingFlavour.getMappingImpl().getMappingsPath(mcVersion).orElseThrow().toFile()));
		}

		List<FileSystemUtil.Delegate> openFileSystems = new ArrayList<>();
		FileSystemUtil.Delegate decompiledJar = FileSystemUtil.getJarFileSystem(decompiledPath, true);
		openFileSystems.add(decompiledJar);
		Iterator<Path> resultFsIt = decompiledJar.get().getRootDirectories().iterator();
		if (!resultFsIt.hasNext()) {
			throw new RuntimeException("Zip FileSystem does not have any root directories");
		}
		Path targetJarRootPath = resultFsIt.next();

		Fernflower ff = new Fernflower(new FFNIODirectoryResultSaver(targetJarRootPath), options, new PrintStreamLogger(NULL_IS)); // System.out

		MiscHelper.println("Adding libraries...");
		for (Artifact library : mcVersion.libraries()) {
			Path lib_file = library.resolve(libraryPath);
			openFileSystems.add(FileSystemUtil.getJarFileSystem(lib_file, false));
			ff.addLibrary(lib_file.toFile());
		}


		openFileSystems.add(FileSystemUtil.getJarFileSystem(remappedPath, false));
		ff.addSource(remappedPath.toFile());

		MiscHelper.executeTimedStep("Decompiling...", ff::decompileContext);

		MiscHelper.println("Writing dependencies file...");
		writeLibraries(targetJarRootPath, mcVersion);

		MiscHelper.println("Closing temporary FileSystems delayed...");
		for (FileSystemUtil.Delegate fs : openFileSystems) {
			fs.close();
		}
		return StepResult.SUCCESS;
	}

	private static void writeLibraries(Path parentDirectory, OrderedVersion mcVersion) throws IOException {
		Path p = parentDirectory.resolve("dependencies.json");

		List<Artifact.DependencyArtifact> c = Stream.concat(
						Arrays.stream(new Artifact.DependencyArtifact[]{Artifact.DependencyArtifact.ofVirtual("Java " + mcVersion.javaVersion())}),
						mcVersion.libraries().stream().map(Artifact.DependencyArtifact::new).sorted(Comparator.comparing(artifact -> String.join("", artifact.name().split("-")))))
				.collect(Collectors.toList());

		SerializationHelper.writeAllToPath(p, SerializationHelper.serialize(c));
	}
}
