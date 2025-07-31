package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.mappings.Mapping;

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
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public record Remapper(StepWorker.Config config) implements StepWorker<OrderedVersion, Remapper.Inputs> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Remapper.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.REMAPPED));
		StepOutput<OrderedVersion> mergedStatus = remapJar(pipeline, context, MinecraftJar.MERGED, input.mergedJar().orElse(null), PipelineFilesystemStorage.REMAPPED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = remapJar(pipeline, context, MinecraftJar.CLIENT, input.clientJar().orElse(null), PipelineFilesystemStorage.REMAPPED_CLIENT_JAR);
		StepOutput<OrderedVersion> serverStatus = remapJar(pipeline, context, MinecraftJar.SERVER, input.serverJar().orElse(null), PipelineFilesystemStorage.REMAPPED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}

	// From Fabric-loom
	private static final Pattern MC_LV_PATTERN = Pattern.compile("\\$\\$\\d+");

	private StepOutput<OrderedVersion> remapJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, MinecraftJar type, StorageKey inputFile, StorageKey outputFile) throws IOException {
		if (inputFile == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Mapping mapping = config.mappingFlavour().getMappingImpl();
		if (!mapping.canMappingsBeUsedOn(context.targetVersion(), type)) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarIn = pipeline.getStoragePath(inputFile, context);
		Path jarOut = pipeline.getStoragePath(outputFile, context);
		if (Files.exists(jarOut) && !MiscHelper.isJarEmpty(jarOut)) {
			return StepOutput.ofSingle(StepStatus.UP_TO_DATE, outputFile);
		}
		if (Files.exists(jarOut)) {
			Files.delete(jarOut);
		}
		IMappingProvider mappingProvider = mapping.getMappingsProvider(context.targetVersion(), type);
		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.invalidLvNamePattern(MC_LV_PATTERN)
			.inferNameFromSameLvIndex(true)
			.withMappings(mappingProvider)
			.fixPackageAccess(true)
			.threads(Library.CONF_GLOBAL.remappingThreads());
		TinyRemapper remapper = remapperBuilder.build();
		remapper.readInputs(jarIn);
		try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(jarOut).build()) {
			remapper.apply(consumer, remapper.createInputTag());
		}
		remapper.finish();
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}
}
