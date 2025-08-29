package com.github.winplay02.gitcraft.launcher;

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
import net.fabricmc.loom.util.FileSystemUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public record LaunchStepCopyStrippedResources(StepWorker.Config config) implements StepWorker<OrderedVersion, LaunchStepCopyStrippedResources.Inputs> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Inputs input, StepResults<OrderedVersion> results) throws Exception {
		Path clientOriginalPath = pipeline.getStoragePath(PipelineFilesystemStorage.ARTIFACTS_CLIENT_JAR, context, this.config);
		Path clientModifiedPath = pipeline.getStoragePath(input.clientJar().orElse(null), context, this.config);
		Path clientOutputPath = results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCHABLE_CLIENT_JAR);
		Files.createDirectories(clientOutputPath.getParent());
		if (Files.exists(clientOutputPath) && !MiscHelper.isJarEmpty(clientOutputPath)) {
			return new StepOutput<>(StepStatus.UP_TO_DATE, results);
		}
		try (
			FileSystemUtil.Delegate originalClient = FileSystemUtil.getJarFileSystem(clientOriginalPath);
			FileSystemUtil.Delegate modifiedClient = FileSystemUtil.getJarFileSystem(clientModifiedPath);
			FileSystemUtil.Delegate outputClient = FileSystemUtil.getJarFileSystem(clientOutputPath, true);
			Stream<Path> modifiedClientFileStream = Files.list(modifiedClient.getRoot());
			Stream<Path> originalClientFileStream = Files.list(originalClient.getRoot());
		) {
			for (Path modifiedPath : modifiedClientFileStream.toList()) {
				Path outputPath = outputClient.getRoot().resolve(modifiedClient.getRoot().relativize(modifiedPath));
				if (modifiedPath.getFileName().toString().equals("META-INF") && Files.exists(modifiedPath.resolve("MANIFEST.MF"))) {
					continue;
				}
				if (Files.isRegularFile(modifiedPath)) {
					Files.copy(modifiedPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
				} else {
					MiscHelper.copyLargeDir(modifiedPath, outputPath);
				}
			}
			for (Path modifiedPath : originalClientFileStream.toList()) {
				Path outputPath = outputClient.getRoot().resolve(originalClient.getRoot().relativize(modifiedPath));
				if (modifiedPath.getFileName().toString().equals("META-INF")) {
					Files.createDirectories(outputPath);
					Path manifestFile = outputPath.resolve("MANIFEST.MF");
					Files.writeString(manifestFile, "Manifest-Version: 1.0\r\n", StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
					continue;
				}
				if (Files.isRegularFile(modifiedPath) && !modifiedPath.getFileName().toString().endsWith(".class")) {
					Files.copy(modifiedPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
				} else if (Files.isDirectory(modifiedPath) && !Files.exists(outputPath)) {
					MiscHelper.copyLargeDirExceptNoFileExt(modifiedPath, outputPath, List.of(), Set.of("class"));
				}
			}
		} catch (Exception e) {
			Files.deleteIfExists(clientOutputPath);
			throw e;
		}
		return new StepOutput<>(StepStatus.SUCCESS, results);
	}

	public record Inputs(Optional<StorageKey> clientJar) implements StepInput {
	}
}
