package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
import net.ornithemc.condor.Condor;
import net.ornithemc.condor.Options;

public record LvtPatcher(StepWorker.Config config) implements StepWorker<OrderedVersion, LvtPatcher.Inputs> {

	@Override
	public boolean shouldExecute(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context) {
		return config.lvtPatch();
	}

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, LvtPatcher.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.PATCHED));
		Path librariesDir = pipeline.getStoragePath(PipelineFilesystemStorage.LIBRARIES, context, config);
		if (librariesDir == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.FAILED);
		}
		List<Path> libraries = context.targetVersion().libraries().stream().map(artifact -> artifact.resolve(librariesDir)).toList();
		StepOutput<OrderedVersion> mergedStatus = patchLocalVariableTables(pipeline, context, input.mergedJar().orElse(null), PipelineFilesystemStorage.LVT_PATCHED_MERGED_JAR, libraries);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = patchLocalVariableTables(pipeline, context, input.clientJar().orElse(null), PipelineFilesystemStorage.LVT_PATCHED_CLIENT_JAR, libraries);
		StepOutput<OrderedVersion> serverStatus = patchLocalVariableTables(pipeline, context, input.serverJar().orElse(null), PipelineFilesystemStorage.LVT_PATCHED_SERVER_JAR, libraries);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	private StepOutput<OrderedVersion> patchLocalVariableTables(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StorageKey inputFile, StorageKey outputFile, List<Path> libraries) throws IOException {
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

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}
}
