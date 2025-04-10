package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import com.github.winplay02.gitcraft.GitCraft;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public record Remapper(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		Files.createDirectories(pipeline.initResultFile(step, context, Results.REMAPPED_JARS_DIRECTORY));
		StepStatus mergedStatus = remapJar(pipeline, context, MinecraftJar.MERGED, Results.MINECRAFT_MERGED_JAR);
		if (mergedStatus.hasRun()) {
			return mergedStatus;
		}
		StepStatus clientStatus = remapJar(pipeline, context, MinecraftJar.CLIENT, Results.MINECRAFT_CLIENT_JAR);
		StepStatus serverStatus = remapJar(pipeline, context, MinecraftJar.SERVER, Results.MINECRAFT_SERVER_JAR);
		return StepStatus.merge(clientStatus, serverStatus);
	}

	// From Fabric-loom
	private static final Pattern MC_LV_PATTERN = Pattern.compile("\\$\\$\\d+");

	private StepStatus remapJar(Pipeline pipeline, Context context, MinecraftJar inFile, StepResult outFile) throws IOException {
		if (!config.mappingFlavour().canBeUsedOn(context.minecraftVersion(), inFile)) {
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
		if (Files.exists(jarOut)) {
			Files.delete(jarOut);
		}
		IMappingProvider mappingProvider = config.mappingFlavour().getProvider(context.minecraftVersion(), inFile);
		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.invalidLvNamePattern(MC_LV_PATTERN)
			.inferNameFromSameLvIndex(true)
			.withMappings(mappingProvider)
			.fixPackageAccess(true)
			.threads(GitCraft.config.remappingThreads);
		TinyRemapper remapper = remapperBuilder.build();
		remapper.readInputs(jarIn);
		try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(jarOut).build()) {
			remapper.apply(consumer, remapper.createInputTag());
		}
		remapper.finish();
		return StepStatus.SUCCESS;
	}

	public enum Results implements StepResult {
		REMAPPED_JARS_DIRECTORY, MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_MERGED_JAR
	}
}
