package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.ornithemc.preen.Preen;

public record Preener(StepWorker.Config config) implements StepWorker<OrderedVersion, Preener.Inputs> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Preener.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		if (!GitCraft.getApplicationConfiguration().enablePreening()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.REMAPPED)); // this directory might be confusing?
		StepOutput<OrderedVersion> mergedStatus = preenJar(pipeline, context, input.mergedJar().orElse(null), PipelineFilesystemStorage.PREENED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = preenJar(pipeline, context, input.clientJar().orElse(null), PipelineFilesystemStorage.PREENED_CLIENT_JAR);
		StepOutput<OrderedVersion> serverStatus = preenJar(pipeline, context, input.serverJar().orElse(null), PipelineFilesystemStorage.PREENED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	private StepOutput<OrderedVersion> preenJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StorageKey inputFile, StorageKey outputFile) throws IOException {
		Path jarIn = pipeline.getStoragePath(inputFile, context);
		if (jarIn == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarOut = pipeline.getStoragePath(outputFile, context);
		if (Files.exists(jarOut) && !MiscHelper.isJarEmpty(jarOut)) {
			return StepOutput.ofSingle(StepStatus.UP_TO_DATE, outputFile);
		}
		Files.deleteIfExists(jarOut);
		Files.copy(jarIn, jarOut);
		Preen.splitMergedBridgeMethods(jarOut);
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}
}
