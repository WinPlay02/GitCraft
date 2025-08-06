package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import io.github.gaming32.signaturechanger.cli.ApplyAction;

public record JarsSignatureChanger(StepWorker.Config config) implements StepWorker<OrderedVersion, JarsSignatureChanger.Inputs> {

	@Override
	public boolean shouldExecute(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context) {
		return this.config().signaturesFlavour() != SignaturesFlavour.NONE; // optimization
	}

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, JarsSignatureChanger.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.PATCHED));
		StepOutput<OrderedVersion> mergedStatus = patchJar(pipeline, context, MinecraftJar.MERGED, input.mergedJar().orElse(null), PipelineFilesystemStorage.SIGNATURES_PATCHED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = patchJar(pipeline, context, MinecraftJar.CLIENT, input.clientJar().orElse(null), PipelineFilesystemStorage.SIGNATURES_PATCHED_CLIENT_JAR);
		StepOutput<OrderedVersion> serverStatus = patchJar(pipeline, context, MinecraftJar.SERVER, input.serverJar().orElse(null), PipelineFilesystemStorage.SIGNATURES_PATCHED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	private StepOutput<OrderedVersion> patchJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, MinecraftJar inFile, StorageKey inputFile, StorageKey outputFile) throws IOException {
		if (!config.signaturesFlavour().canBeUsedOn(context.targetVersion(), inFile)) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
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
		ApplyAction.run(config.signaturesFlavour().getSignatures(context.targetVersion(), inFile), List.of(jarOut));
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}
}
