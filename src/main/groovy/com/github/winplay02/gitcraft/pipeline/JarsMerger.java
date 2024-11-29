package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;

import com.github.winplay02.gitcraft.types.OrderedVersion;

import net.fabricmc.stitch.merge.JarMerger;

public record JarsMerger(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		OrderedVersion mcVersion = context.minecraftVersion();
		if (!mcVersion.hasClientCode() || !mcVersion.hasServerJar()) {
			return StepStatus.NOT_RUN;
		}
		// obfuscated jars for versions older than 1.3 cannot be merged
		// those versions must be merged after remapping, if the mapping flavour allows it
		boolean obfuscated = (step == Step.MERGE_OBFUSCATED_JARS);
		if (obfuscated != mcVersion.hasSharedObfuscation()) {
			return StepStatus.NOT_RUN;
		}
		if (!obfuscated && config.mappingFlavour().getMappingImpl().supportsMergingPre1_3Versions()) {
			return StepStatus.NOT_RUN;
		}
		StepResult jarFile = obfuscated ? Results.OBFUSCATED_MINECRAFT_MERGED_JAR : Results.REMAPPED_MINECRAFT_MERGED_JAR;
		Path mergedJar = pipeline.initResultFile(step, context, jarFile);
		if (Files.exists(mergedJar) && Files.size(mergedJar) > 22 /* not empty jar */) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mergedJar);
		Path clientJar = pipeline.getMinecraftJar(MinecraftJar.CLIENT);
		Path serverJar = pipeline.getMinecraftJar(MinecraftJar.SERVER);
		try (JarMerger jarMerger = new JarMerger(clientJar.toFile(), serverJar.toFile(), mergedJar.toFile())) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}
		return StepStatus.SUCCESS;
	}

	public enum Results implements StepResult {
		OBFUSCATED_MINECRAFT_MERGED_JAR, REMAPPED_MINECRAFT_MERGED_JAR
	}
}
