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
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.ornithemc.condor.Condor;
import net.ornithemc.condor.Options;

public record LvtPatcher(GitCraftStepConfig config) implements GitCraftStepWorker<GitCraftStepWorker.JarTupleInput> {

	@Override
	public boolean shouldExecute(IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline, IStepContext.SimpleStepContext<OrderedVersion> context) {
		return config.lvtPatch();
	}

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		GitCraftStepWorker.JarTupleInput input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.PATCHED));
		Path librariesDir = pipeline.getStoragePath(GitCraftPipelineFilesystemStorage.LIBRARIES, context, config);
		if (librariesDir == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.FAILED);
		}
		List<Path> libraries = context.targetVersion().libraries().stream().map(artifact -> artifact.resolve(librariesDir)).toList();
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> mergedStatus = patchLocalVariableTables(pipeline, context, input.mergedJar().orElse(null), GitCraftPipelineFilesystemStorage.LVT_PATCHED_MERGED_JAR, libraries);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> clientStatus = patchLocalVariableTables(pipeline, context, input.clientJar().orElse(null), GitCraftPipelineFilesystemStorage.LVT_PATCHED_CLIENT_JAR, libraries);
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> serverStatus = patchLocalVariableTables(pipeline, context, input.serverJar().orElse(null), GitCraftPipelineFilesystemStorage.LVT_PATCHED_SERVER_JAR, libraries);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	private StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> patchLocalVariableTables(IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
																																	IStepContext.SimpleStepContext<OrderedVersion> context, StorageKey inputFile, StorageKey outputFile, List<Path> libraries) throws IOException {
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
		// this step is applied before remapping, so obfuscate variable names
		// that way Tiny Remapper will take care of fixing them
		Condor.run(jarOut, libraries, Options.builder().removeInvalidEntries().obfuscateNames().build());
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}
}
