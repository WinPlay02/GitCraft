package com.github.winplay02.gitcraft.pipeline.workers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepConfig;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loom.util.FileSystemUtil;

public record ArtifactsUnpacker(GitCraftStepConfig config) implements GitCraftStepWorker<ArtifactsUnpacker.Inputs> {

	static final String SERVER_ZIP_JAR_NAME = "minecraft-server.jar";

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		ArtifactsUnpacker.Inputs input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		if (input.serverZip().isEmpty()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		// If an actual JAR exists, do not proceed
		Optional<StorageKey> fetchedServerJarKey = results.getKeyIfExists(GitCraftPipelineFilesystemStorage.ARTIFACTS_SERVER_JAR);
		if (fetchedServerJarKey.isPresent()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path serverZip = pipeline.getStoragePath(input.serverZip().orElseThrow(), context, this.config);
		Path unpackedServerJar = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.UNPACKED_SERVER_JAR);

		if (Files.exists(unpackedServerJar) && !MiscHelper.isJarEmpty(unpackedServerJar)) {
			return new StepOutput<>(StepStatus.UP_TO_DATE, results);
		}
		if (Files.exists(unpackedServerJar)) {
			Files.delete(unpackedServerJar);
		}

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(serverZip)) {
			Files.copy(Files.newInputStream(fs.get().getPath(SERVER_ZIP_JAR_NAME)), unpackedServerJar);
		}
		return new StepOutput<>(StepStatus.SUCCESS, results);
	}

	public record Inputs(Optional<StorageKey> serverZip) implements StepInput {
	}
}
