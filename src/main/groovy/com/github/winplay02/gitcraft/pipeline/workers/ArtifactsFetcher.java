package com.github.winplay02.gitcraft.pipeline.workers;

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
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public record ArtifactsFetcher(GitCraftStepConfig config) implements GitCraftStepWorker<StepInput.Empty> {

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		StepInput.Empty input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.ARTIFACTS));

		OrderedVersion mcVersion = context.targetVersion();

		List<Callable<StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig>>> outputTasks = new ArrayList<>(4);

		if (mcVersion.hasClientCode()) {
			outputTasks.add(() -> fetchArtifact(pipeline, context, this.config, mcVersion.clientJar(), GitCraftPipelineFilesystemStorage.ARTIFACTS_CLIENT_JAR, "client jar"));
		}
		if (mcVersion.hasServerJar()) {
			outputTasks.add(() -> fetchArtifact(pipeline, context, this.config, mcVersion.serverDist().serverJar(), GitCraftPipelineFilesystemStorage.ARTIFACTS_SERVER_JAR, "server jar"));
		}
		if (mcVersion.hasServerWindows()) {
			outputTasks.add(() -> fetchArtifact(pipeline, context, this.config, mcVersion.serverDist().windowsServer(), GitCraftPipelineFilesystemStorage.ARTIFACTS_SERVER_EXE, "server exe"));
		}
		if (mcVersion.hasServerZip()) {
			outputTasks.add(() -> fetchArtifact(pipeline, context, this.config, mcVersion.serverDist().serverZip(), GitCraftPipelineFilesystemStorage.ARTIFACTS_SERVER_ZIP, "server zip"));
		}
		return StepOutput.merge(results, StepOutput.merge(MiscHelper.runTasksInParallelAndAwaitResult(
			32,
			context.executorService(),
			outputTasks
		)));
	}

	static StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> fetchArtifact(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		GitCraftStepConfig config,
		Artifact artifact,
		StorageKey resultFile,
		String artifactKind)
	{
		Path resultPath = pipeline.getStoragePath(resultFile, context, config);
		StepStatus status = artifact.fetchArtifactToFile(context.executorService(), resultPath, artifactKind);
		if (status.hasRun()) {
			return StepOutput.ofSingle(status, resultFile);
		}
		return StepOutput.ofEmptyResultSet(status);
	}
}
