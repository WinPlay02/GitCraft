package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public record ArtifactsFetcher(StepWorker.Config config) implements StepWorker<OrderedVersion, StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, StepInput.Empty input, StepResults<OrderedVersion> results) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.ARTIFACTS));

		OrderedVersion mcVersion = context.targetVersion();

		List<Callable<StepOutput<OrderedVersion>>> outputTasks = new ArrayList<>(4);

		if (mcVersion.hasClientCode()) {
			outputTasks.add(() -> fetchArtifact(pipeline, context, mcVersion.clientJar(), PipelineFilesystemStorage.ARTIFACTS_CLIENT_JAR, "client jar"));
		}
		if (mcVersion.hasServerJar()) {
			outputTasks.add(() -> fetchArtifact(pipeline, context, mcVersion.serverDist().serverJar(), PipelineFilesystemStorage.ARTIFACTS_SERVER_JAR, "server jar"));
		}
		if (mcVersion.hasServerWindows()) {
			outputTasks.add(() -> fetchArtifact(pipeline, context, mcVersion.serverDist().windowsServer(), PipelineFilesystemStorage.ARTIFACTS_SERVER_EXE, "server exe"));
		}
		if (mcVersion.hasServerZip()) {
			outputTasks.add(() -> fetchArtifact(pipeline, context, mcVersion.serverDist().serverZip(), PipelineFilesystemStorage.ARTIFACTS_SERVER_ZIP, "server zip"));
		}
		return StepOutput.merge(results, StepOutput.merge(MiscHelper.runTasksInParallelAndAwaitResult(
			4,
			context.executorService(),
			outputTasks
		)));
	}

	static StepOutput<OrderedVersion> fetchArtifact(Pipeline<OrderedVersion> pipeline, StepWorker.Context<OrderedVersion> context, Artifact artifact, StorageKey resultFile, String artifactKind) {
		Path resultPath = pipeline.getStoragePath(resultFile, context);
		StepStatus status = artifact.fetchArtifactToFile(context.executorService(), resultPath, artifactKind);
		if (status.hasRun()) {
			return StepOutput.ofSingle(status, resultFile);
		}
		return StepOutput.ofEmptyResultSet(status);
	}
}
