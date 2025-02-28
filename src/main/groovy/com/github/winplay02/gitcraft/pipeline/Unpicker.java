package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import com.github.winplay02.gitcraft.mappings.Mapping;

public record Unpicker(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		Files.createDirectories(pipeline.initResultFile(step, context, Results.UNPICKED_JARS_DIRECTORY));
		StepStatus mergedStatus = unpickJar(pipeline, context, MinecraftJar.MERGED, Results.MINECRAFT_MERGED_JAR);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = unpickJar(pipeline, context, MinecraftJar.CLIENT, Results.MINECRAFT_CLIENT_JAR);
		StepStatus serverStatus = unpickJar(pipeline, context, MinecraftJar.SERVER, Results.MINECRAFT_SERVER_JAR);
		return StepStatus.merge(clientStatus, serverStatus);
	}

	private StepStatus unpickJar(Pipeline pipeline, Context context, MinecraftJar inFile, StepResult outFile) throws IOException {
		if (!config.mappingFlavour().canBeUsedOn(context.minecraftVersion(), inFile) || !config.mappingFlavour().supportsConstantUnpicking()) {
			return StepStatus.NOT_RUN;
		}
		Map<String, Path> additionalMappingPaths = config.mappingFlavour().getAdditionalInformation(context.minecraftVersion(), inFile);
		if (!additionalMappingPaths.containsKey(Mapping.KEY_UNPICK_CONSTANTS) || !additionalMappingPaths.containsKey(Mapping.KEY_UNPICK_DEFINITIONS)) {
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
		Path librariesDir = pipeline.getResultFile(LibrariesFetcher.Results.LIBRARIES_DIRECTORY);
		if (librariesDir == null) {
			return StepStatus.FAILED;
		}
		ArrayList<String> params = new ArrayList<>(context.minecraftVersion().libraries().size() + 4);
		params.add(jarIn.toAbsolutePath().toString());
		params.add(jarOut.toAbsolutePath().toString());
		params.add(additionalMappingPaths.get(Mapping.KEY_UNPICK_DEFINITIONS).toAbsolutePath().toString());
		params.add(additionalMappingPaths.get(Mapping.KEY_UNPICK_CONSTANTS).toAbsolutePath().toString());
		params.addAll(context.minecraftVersion().libraries().stream().map(artifact -> artifact.resolve(librariesDir)).map(p -> p.toAbsolutePath().toString()).toList());
		daomephsta.unpick.cli.Main.main(params.toArray(new String[0]));
		return StepStatus.SUCCESS;
	}

	public enum Results implements StepResult {
		UNPICKED_JARS_DIRECTORY, MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_MERGED_JAR
	}
}
