package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loom.util.FileSystemUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DatagenStep extends Step {
	private static final SemanticVersion DATAGEN_AVAILABLE;
	private static final SemanticVersion DATAGEN_BUNDLER;

	static {
		try {
			DATAGEN_AVAILABLE = SemanticVersion.parse("1.13-alpha.18.1.a");
			DATAGEN_BUNDLER = SemanticVersion.parse("1.18-alpha.21.39.a");
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getName() {
		return STEP_DATAGEN;
	}

	@Override
	public boolean ignoresMappings() {
		return true;
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return GitCraft.STEP_FETCH_ARTIFACTS.getInternalArtifactPath(mcVersion, mappingFlavour).resolve("datagenerator");
	}

	@Override
	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		return mcVersion.compareTo(DATAGEN_AVAILABLE) >= 0 && (GitCraft.config.loadDatagenRegistry || (GitCraft.config.readableNbt && GitCraft.config.loadIntegratedDatapack)) && super.preconditionsShouldRun(pipelineCache, mcVersion, mappingFlavour, versionGraph, repo);
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		if (!mcVersion.hasServerCode()) {
			MiscHelper.panic("Cannot execute datagen, no jar available");
		}
		Path executablePath = mcVersion.serverJar().resolve(GitCraft.STEP_FETCH_ARTIFACTS.getInternalArtifactPath(mcVersion, mappingFlavour));
		Path datagenDirectory = getInternalArtifactPath(mcVersion, mappingFlavour);
		Files.createDirectories(datagenDirectory);
		if (GitCraft.config.readableNbt) {
			// Structures (& more)
			{
				Path mergedJarPath = pipelineCache.getForKey(Step.STEP_MERGE);
				if (mergedJarPath == null) { // Client JAR could also work, if merge did not happen
					MiscHelper.panic("A merged JAR for version %s does not exist", mcVersion.launcherFriendlyVersionName());
				}
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mergedJarPath)) {
					MiscHelper.copyLargeDir(fs.get().getPath("data"), getDatagenNBTSourcePathData(datagenDirectory));
				}
			}
			// Delete Output files, as some versions do not work, when files already exist
			Path datagenSnbtOutput = getDatagenSNBTDestinationPath(datagenDirectory);
			MiscHelper.deleteDirectory(datagenSnbtOutput);
			executeDatagen(mcVersion, datagenDirectory, executablePath, "--dev",
				"--input", getDatagenNBTSourcePath(datagenDirectory).toAbsolutePath().toString(),
				"--output", datagenSnbtOutput.toAbsolutePath().toString()
			);
			if (!Files.exists(datagenSnbtOutput) || !Files.isDirectory(datagenSnbtOutput)) {
				MiscHelper.panic("Datagen step was required, but SNBT files were not generated");
			}
		}
		if (GitCraft.config.loadDatagenRegistry) {
			// Datagen
			executeDatagen(mcVersion, datagenDirectory, executablePath, "--reports");
			Path datagenReports = getDatagenReports(datagenDirectory);
			if (!Files.exists(datagenReports) || !Files.isDirectory(datagenReports)) {
				MiscHelper.panic("Datagen step was required, but reports were not generated");
			}
		}
		return StepResult.SUCCESS;
	}

	private void executeDatagen(OrderedVersion mcVersion, Path cwd, Path executable, String... args) throws IOException, InterruptedException {
		if (mcVersion.compareTo(DATAGEN_BUNDLER) >= 0) {
			// >= DATAGEN_BUNDLER: java -DbundlerMainClass=net.minecraft.data.Main -jar minecraft_server.jar
			MiscHelper.createJavaJarSubprocess(executable, cwd, new String[]{"-DbundlerMainClass=net.minecraft.data.Main"}, args);
		} else {
			// < DATAGEN_BUNDLER: java -cp minecraft_server.jar net.minecraft.data.Main
			ArrayList<String> argsList = new ArrayList<>(List.of("net.minecraft.data.Main"));
			argsList.addAll(List.of(args));
			MiscHelper.createJavaCpSubprocess(executable, cwd, new String[0], argsList.toArray(new String[0]));
		}
	}

	protected Path getDatagenReports(Path rootDatagenPath) {
		return rootDatagenPath.resolve("generated").resolve("reports");
	}

	private Path getDatagenNBTSourcePath(Path rootDatagenPath) {
		return rootDatagenPath.resolve("input");
	}

	private Path getDatagenNBTSourcePathData(Path rootDatagenPath) {
		return getDatagenNBTSourcePath(rootDatagenPath).resolve("data");
	}

	private Path getDatagenSNBTDestinationPath(Path rootDatagenPath) {
		return rootDatagenPath.resolve("output");
	}

	protected Path getDatagenSNBTDestinationPathData(Path rootDatagenPath) {
		return getDatagenSNBTDestinationPath(rootDatagenPath).resolve("data");
	}
}
