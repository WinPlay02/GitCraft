package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.stitch.merge.JarMerger;

public record JarsMerger(boolean obfuscated, GitCraftStepConfig config) implements GitCraftStepWorker<JarsMerger.Inputs> {

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		JarsMerger.Inputs input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		OrderedVersion mcVersion = context.targetVersion();
		if (input.clientJar().isEmpty() || input.serverJar().isEmpty()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		// obfuscated jars for versions older than 1.3 cannot be merged
		// those versions must be merged after remapping, if the mapping flavour allows it
		if (this.obfuscated != mcVersion.hasSharedObfuscation()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		if (!this.obfuscated && !config.mappingFlavour().supportsMergingPre1_3Versions()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path mergedJarPath = results.getPathForKeyAndAdd(pipeline, context, this.config, this.obfuscated ? GitCraftPipelineFilesystemStorage.ARTIFACTS_MERGED_JAR : GitCraftPipelineFilesystemStorage.REMAPPED_MERGED_JAR);
		if (Files.exists(mergedJarPath) && !MiscHelper.isJarEmpty(mergedJarPath)) {
			return new StepOutput<>(StepStatus.UP_TO_DATE, results);
		}
		Files.deleteIfExists(mergedJarPath);
		Path clientJar = pipeline.getStoragePath(input.clientJar().orElseThrow(), context, this.config);
		Path serverJar = pipeline.getStoragePath(input.serverJar().orElseThrow(), context, this.config);

		// unbundle if bundled
		if (this.obfuscated) {
			BundleMetadata sbm = BundleMetadata.fromJar(serverJar);
			if (sbm != null) {
				Path unbundledServerJar = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.UNBUNDLED_SERVER_JAR);

				if (sbm.versions().size() != 1) {
					throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(sbm.versions().size()));
				}

				unpackJarEntry(sbm.versions().getFirst(), serverJar, unbundledServerJar);
				serverJar = unbundledServerJar;
			}
		}

		try (JarMerger jarMerger = new JarMerger(clientJar.toFile(), serverJar.toFile(), mergedJarPath.toFile())) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}
		return new StepOutput<>(StepStatus.SUCCESS, results);
	}

	public record Inputs(Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}

	private void unpackJarEntry(BundleMetadata.Entry entry, Path jar, Path dest) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar); InputStream is = Files.newInputStream(fs.get().getPath(entry.path()))) {
			Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
