package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepConfig;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import io.github.gaming32.signaturechanger.cli.ApplyAction;

public record JarsSignatureChanger(GitCraftStepConfig config) implements GitCraftStepWorker<GitCraftStepWorker.JarTupleInput> {

	@Override
	public boolean shouldExecute(IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline, IStepContext.SimpleStepContext<OrderedVersion> context) {
		return this.config().signaturesFlavour() != SignaturesFlavour.NONE; // optimization
	}

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		GitCraftStepWorker.JarTupleInput input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.PATCHED));
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> mergedStatus = patchJar(pipeline, context, MinecraftJar.MERGED, input.mergedJar().orElse(null), GitCraftPipelineFilesystemStorage.SIGNATURES_PATCHED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> clientStatus = patchJar(pipeline, context, MinecraftJar.CLIENT, input.clientJar().orElse(null), GitCraftPipelineFilesystemStorage.SIGNATURES_PATCHED_CLIENT_JAR);
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> serverStatus = patchJar(pipeline, context, MinecraftJar.SERVER, input.serverJar().orElse(null), GitCraftPipelineFilesystemStorage.SIGNATURES_PATCHED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	private StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> patchJar(IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
																													IStepContext.SimpleStepContext<OrderedVersion> context, MinecraftJar inFile, StorageKey inputFile, StorageKey outputFile) throws IOException {
		if (!config.signaturesFlavour().canBeUsedOn(context.targetVersion(), inFile)) {
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
		ApplyAction.run(config.signaturesFlavour().getSignatures(context.targetVersion(), inFile), List.of(jarOut));
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}
}
