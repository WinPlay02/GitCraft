package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
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
import net.ornithemc.exceptor.Exceptor;

public record JarsExceptor(StepWorker.Config config) implements StepWorker<OrderedVersion, JarsExceptor.Inputs> {

	@Override
	public boolean shouldExecute(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context) {
		return this.config().exceptionsFlavour() != ExceptionsFlavour.NONE; // optimization
	}

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, JarsExceptor.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.PATCHED));
		StepOutput<OrderedVersion> mergedStatus = patchJar(pipeline, context, MinecraftJar.MERGED, input.mergedJar().orElse(null), PipelineFilesystemStorage.EXCEPTIONS_PATCHED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = patchJar(pipeline, context, MinecraftJar.CLIENT, input.clientJar().orElse(null), PipelineFilesystemStorage.EXCEPTIONS_PATCHED_CLIENT_JAR);
		StepOutput<OrderedVersion> serverStatus = patchJar(pipeline, context, MinecraftJar.SERVER, input.serverJar().orElse(null), PipelineFilesystemStorage.EXCEPTIONS_PATCHED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	private StepOutput<OrderedVersion> patchJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, MinecraftJar inFile, StorageKey inputFile, StorageKey outputFile) throws IOException {
		if (!config.exceptionsFlavour().canBeUsedOn(context.targetVersion(), inFile)) {
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
		Files.copy(jarIn, jarOut);
		Exceptor.apply(jarOut, config.exceptionsFlavour().getExceptions(context.targetVersion(), inFile));
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}
}
