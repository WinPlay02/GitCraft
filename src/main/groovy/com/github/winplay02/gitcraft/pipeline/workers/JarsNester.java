package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.ornithemc.nester.Nester;

public record JarsNester(StepWorker.Config config) implements StepWorker<OrderedVersion, JarsNester.Inputs> {

	@Override
	public boolean shouldExecute(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context) {
		return this.config().nestsFlavour() != NestsFlavour.NONE; // optimization
	}

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, JarsNester.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.REMAPPED));
		StepOutput<OrderedVersion> mergedStatus = nestJar(pipeline, context, MinecraftJar.MERGED, input.mergedJar().orElse(null), PipelineFilesystemStorage.NESTED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = nestJar(pipeline, context, MinecraftJar.CLIENT, input.clientJar().orElse(null), PipelineFilesystemStorage.NESTED_CLIENT_JAR);
		StepOutput<OrderedVersion> serverStatus = nestJar(pipeline, context, MinecraftJar.SERVER, input.serverJar().orElse(null), PipelineFilesystemStorage.NESTED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	private StepOutput<OrderedVersion> nestJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, MinecraftJar inFile, StorageKey inputFile, StorageKey outputFile) throws IOException {
		if (!config.nestsFlavour().canBeUsedOn(context.targetVersion(), inFile, config.mappingFlavour())) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarIn = pipeline.getStoragePath(inputFile, context, this.config);
		if (jarIn == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarOut = pipeline.getStoragePath(outputFile, context, this.config);
		if (Files.exists(jarOut) && !MiscHelper.isJarEmpty(jarOut)) {
			return StepOutput.ofSingle(StepStatus.UP_TO_DATE, outputFile);
		}
		Files.deleteIfExists(jarOut);
		Nester.nestJar(jarIn, jarOut, config.nestsFlavour().getNests(context.targetVersion(), inFile, config.mappingFlavour()));
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}
}
