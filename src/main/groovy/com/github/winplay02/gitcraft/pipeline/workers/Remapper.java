package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.winplay02.gitcraft.mappings.MappingUtils;
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

	private StepOutput<OrderedVersion> remapJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, MinecraftJar type, StorageKey inputFile, StorageKey outputFile) throws IOException {
		if (inputFile == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		if (!config.mappingFlavour().canBeUsedOn(context.targetVersion(), type)) {
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
		IMappingProvider mappingProvider = config.mappingFlavour().getProvider(context.targetVersion(), type);
		TinyRemapper remapper = MappingUtils.createTinyRemapper(mappingProvider);
		MappingUtils.remapJar(remapper, jarIn, jarOut);
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}
}
