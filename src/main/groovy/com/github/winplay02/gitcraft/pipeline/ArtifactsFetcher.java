package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Path;

import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public record ArtifactsFetcher(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		pipeline.initResultFile(step, context, Results.ARTIFACTS_DIRECTORY);

		StepStatus clientJarStatus = null;
		StepStatus serverJarStatus = null;
		StepStatus serverExeStatus = null;
		StepStatus serverZipStatus = null;

		OrderedVersion mcVersion = context.minecraftVersion();

		if (mcVersion.hasClientCode()) {
			clientJarStatus = fetchArtifact(step, pipeline, context, mcVersion.clientJar(), Results.MINECRAFT_CLIENT_JAR);
		}
		if (mcVersion.hasServerJar()) {
			serverJarStatus = fetchArtifact(step, pipeline, context, mcVersion.serverDist().serverJar(), Results.MINECRAFT_SERVER_JAR);
		}
		if (mcVersion.hasServerWindows()) {
			serverExeStatus = fetchArtifact(step, pipeline, context, mcVersion.serverDist().windowsServer(), Results.MINECRAFT_SERVER_EXE);
		}
		if (mcVersion.hasServerZip()) {
			serverZipStatus = fetchArtifact(step, pipeline, context, mcVersion.serverDist().serverZip(), Results.MINECRAFT_SERVER_ZIP);
		}

		return StepStatus.merge(clientJarStatus, serverJarStatus, serverExeStatus, serverZipStatus);
	}

	static StepStatus fetchArtifact(Step step, Pipeline pipeline, StepWorker.Context context, Artifact artifact, StepResult resultFile) {
		Path resultPath = step.getResultFile(resultFile, context);
		Path parentPath = resultPath.getParent();
		String artifactKind = resultFile.toString().toLowerCase();
		StepStatus status = artifact.fetchArtifact(parentPath, artifactKind);
		if (status.hasRun()) {
			pipeline.initResultFile(step, context, resultFile);
		}
		return status;
	}

	public enum Results implements StepResult {
		ARTIFACTS_DIRECTORY, MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_SERVER_EXE, MINECRAFT_SERVER_ZIP
	}
}
