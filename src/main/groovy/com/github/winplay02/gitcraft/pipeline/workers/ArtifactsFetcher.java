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

import java.nio.file.Files;
import java.nio.file.Path;

public record ArtifactsFetcher(StepWorker.Config config) implements StepWorker<StepInput.Empty> {

	@Override
	public StepOutput run(Pipeline pipeline, Context context, StepInput.Empty input, StepResults results) throws Exception {
		Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, PipelineFilesystemStorage.ARTIFACTS));

		StepOutput clientJarStatus = null;
		StepOutput serverJarStatus = null;
		StepOutput serverExeStatus = null;
		StepOutput serverZipStatus = null;

		OrderedVersion mcVersion = context.minecraftVersion();

		if (mcVersion.hasClientCode()) {
			clientJarStatus = fetchArtifact(pipeline, context, mcVersion.clientJar(), PipelineFilesystemStorage.ARTIFACTS_CLIENT_JAR, "client jar");
		}
		if (mcVersion.hasServerJar()) {
			serverJarStatus = fetchArtifact(pipeline, context, mcVersion.serverDist().serverJar(), PipelineFilesystemStorage.ARTIFACTS_SERVER_JAR, "server jar");
		}
		if (mcVersion.hasServerWindows()) {
			serverExeStatus = fetchArtifact(pipeline, context, mcVersion.serverDist().windowsServer(), PipelineFilesystemStorage.ARTIFACTS_SERVER_EXE, "server exe");
		}
		if (mcVersion.hasServerZip()) {
			serverZipStatus = fetchArtifact(pipeline, context, mcVersion.serverDist().serverZip(), PipelineFilesystemStorage.ARTIFACTS_SERVER_ZIP, "server zip");
		}

		return StepOutput.merge(results, clientJarStatus, serverJarStatus, serverExeStatus, serverZipStatus);
	}

	static StepOutput fetchArtifact(Pipeline pipeline, StepWorker.Context context, Artifact artifact, StorageKey resultFile, String artifactKind) {
		Path resultPath = pipeline.getStoragePath(resultFile, context);
		StepStatus status = artifact.fetchArtifactToFile(resultPath, artifactKind);
		if (status.hasRun()) {
			return StepOutput.ofSingle(status, resultFile);
		}
		return StepOutput.ofEmptyResultSet(status);
	}
}
