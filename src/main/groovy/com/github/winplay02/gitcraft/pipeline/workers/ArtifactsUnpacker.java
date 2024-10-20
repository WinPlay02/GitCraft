package com.github.winplay02.gitcraft.pipeline.workers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import net.fabricmc.loom.util.FileSystemUtil;

public record ArtifactsUnpacker(StepWorker.Config config) implements StepWorker<ArtifactsUnpacker.Inputs> {

	static final String SERVER_ZIP_JAR_NAME = "minecraft-server.jar";

	@Override
	public StepOutput run(Pipeline pipeline, Context context, ArtifactsUnpacker.Inputs input, StepResults results) throws Exception {
		if (input.serverZip().isEmpty()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path serverZip = pipeline.getStoragePath(input.serverZip().orElseThrow(), context);
		Path unpackedServerJar = results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.UNPACKED_SERVER_JAR);

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(serverZip)) {
			Files.copy(Files.newInputStream(fs.get().getPath(SERVER_ZIP_JAR_NAME)), unpackedServerJar);
		}
		return new StepOutput(StepStatus.SUCCESS, results);
	}

	public record Inputs(Optional<StorageKey> serverZip) implements StepInput {
	}
}
