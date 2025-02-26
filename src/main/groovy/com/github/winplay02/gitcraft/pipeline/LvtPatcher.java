package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.winplay02.gitcraft.GitCraft;

import net.ornithemc.condor.Condor;

public record LvtPatcher(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		if (!GitCraft.config.patchLvt) {
			return StepStatus.NOT_RUN;
		}
		Files.createDirectories(pipeline.initResultFile(step, context, Results.LVT_PATCHED_DIRECTORY));
		Path librariesDir = pipeline.getResultFile(LibrariesFetcher.Results.LIBRARIES_DIRECTORY);
		if (librariesDir == null) {
			return StepStatus.FAILED;
		}
		List<Path> libraries = context.minecraftVersion().libraries().stream().map(artifact -> artifact.resolve(librariesDir)).toList();
		StepStatus mergedStatus = patchLocalVariableTables(pipeline, context, MinecraftJar.MERGED, Results.MINECRAFT_MERGED_JAR, libraries);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = patchLocalVariableTables(pipeline, context, MinecraftJar.CLIENT, Results.MINECRAFT_CLIENT_JAR, libraries);
		StepStatus serverStatus = patchLocalVariableTables(pipeline, context, MinecraftJar.SERVER, Results.MINECRAFT_SERVER_JAR, libraries);
		return StepStatus.merge(clientStatus, serverStatus);
	}

	private StepStatus patchLocalVariableTables(Pipeline pipeline, Context context, MinecraftJar inFile, StepResult outFile, List<Path> libraries) throws IOException {
		Path jarIn = pipeline.getMinecraftJar(inFile);
		if (jarIn == null) {
			return StepStatus.NOT_RUN;
		}
		Path jarOut = pipeline.initResultFile(step, context, outFile);
		if (Files.exists(jarOut) && Files.size(jarOut) > 22 /* not empty jar */) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(jarOut);
		Files.copy(jarIn, jarOut);
		Condor.run(jarOut, libraries);
		return StepStatus.SUCCESS;
	}

	public enum Results implements StepResult {
		LVT_PATCHED_DIRECTORY, MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_MERGED_JAR
	}
}
