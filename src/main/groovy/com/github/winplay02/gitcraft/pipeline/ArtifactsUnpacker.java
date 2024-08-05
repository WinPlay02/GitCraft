package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loom.util.FileSystemUtil;

public record ArtifactsUnpacker(Step step, Config config) implements StepWorker {

	static final String SERVER_ZIP_JAR_NAME = "minecraft-server.jar";

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		if (!context.minecraftVersion().hasServerWindows()) {
			return StepStatus.NOT_RUN;
		}
		Path serverZip = pipeline.getResultFile(ArtifactsFetcher.Results.MINECRAFT_SERVER_ZIP);
		Path serverJar = pipeline.initResultFile(step, context, Results.MINECRAFT_SERVER_JAR);
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(serverZip)) {
			Files.copy(Files.newInputStream(fs.get().getPath(SERVER_ZIP_JAR_NAME)), serverJar);
		}
		return StepStatus.SUCCESS;
	}

	public enum Results implements StepResult {
		MINECRAFT_SERVER_JAR
	}
}
