package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.ornithemc.preen.Preen;

public record Preener(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		Files.createDirectories(pipeline.initResultFile(step, context, Results.PREENED_DIRECTORY));
		StepStatus mergedStatus = preenJar(pipeline, context, MinecraftJar.MERGED, Results.MINECRAFT_MERGED_JAR);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = preenJar(pipeline, context, MinecraftJar.CLIENT, Results.MINECRAFT_CLIENT_JAR);
		StepStatus serverStatus = preenJar(pipeline, context, MinecraftJar.SERVER, Results.MINECRAFT_SERVER_JAR);
		return StepStatus.merge(clientStatus, serverStatus);
	}

	private StepStatus preenJar(Pipeline pipeline, Context context, MinecraftJar inFile, StepResult outFile) throws IOException {
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
		Preen.splitMergedBridgeMethods(jarOut);
		return StepStatus.SUCCESS;
	}

	public enum Results implements StepResult {
		PREENED_DIRECTORY, MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_MERGED_JAR
	}
}
