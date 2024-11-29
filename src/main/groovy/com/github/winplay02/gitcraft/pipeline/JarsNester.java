package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.Nest;

import net.ornithemc.nester.Nester;

public record JarsNester(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		Files.createDirectories(pipeline.initResultFile(step, context, Results.NESTS_APPLIED_DIRECTORY));
		StepStatus mergedStatus = nestJar(pipeline, context, MinecraftJar.MERGED, Results.MINECRAFT_MERGED_JAR);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = nestJar(pipeline, context, MinecraftJar.CLIENT, Results.MINECRAFT_CLIENT_JAR);
		StepStatus serverStatus = nestJar(pipeline, context, MinecraftJar.SERVER, Results.MINECRAFT_SERVER_JAR);
		return StepStatus.merge(clientStatus, serverStatus);
	}

	private StepStatus nestJar(Pipeline pipeline, Context context, MinecraftJar inFile, StepResult outFile) throws IOException {
		Nest nests = config.nestsFlavour().getNestsImpl();
		MappingFlavour mappingFlavour = config.mappingFlavour();
		if (!nests.canNestsBeUsedOn(context.minecraftVersion(), inFile, mappingFlavour)) {
			return StepStatus.NOT_RUN;
		}
		Path jarIn = pipeline.getMinecraftJar(inFile);
		if (jarIn == null) {
			return StepStatus.NOT_RUN;
		}
		Path jarOut = pipeline.initResultFile(step, context, outFile);
		if (Files.exists(jarOut) && Files.size(jarOut) > 22 /* not empty jar */) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(jarOut);
		Nester.nestJar(jarIn, jarOut, nests.getNests(context.minecraftVersion(), inFile, mappingFlavour));
		return StepStatus.SUCCESS;
	}

	public enum Results implements StepResult {
		NESTS_APPLIED_DIRECTORY, MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_MERGED_JAR
	}
}
