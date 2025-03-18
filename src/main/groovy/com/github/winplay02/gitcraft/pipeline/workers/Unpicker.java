package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

public record Unpicker(StepWorker.Config config) implements StepWorker<OrderedVersion, Unpicker.Inputs> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Unpicker.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		StepOutput<OrderedVersion> mergedStatus = unpickJar(pipeline, context, MinecraftJar.MERGED, input.mergedJar().orElse(null), PipelineFilesystemStorage.UNPICKED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = unpickJar(pipeline, context, MinecraftJar.CLIENT, input.clientJar().orElse(null), PipelineFilesystemStorage.UNPICKED_CLIENT_JAR);
		StepOutput<OrderedVersion> serverStatus = unpickJar(pipeline, context, MinecraftJar.SERVER, input.serverJar().orElse(null), PipelineFilesystemStorage.UNPICKED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}

	private StepOutput<OrderedVersion> unpickJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, MinecraftJar type, StorageKey inputFile, StorageKey outputFile) throws IOException {
		if (inputFile == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Mapping mapping = config.mappingFlavour().getMappingImpl();
		if (!mapping.canMappingsBeUsedOn(context.targetVersion(), type) || !mapping.supportsConstantUnpicking()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Map<String, Path> additionalMappingPaths = mapping.getAdditionalMappingInformation(context.targetVersion(), type);
		if (!additionalMappingPaths.containsKey(Mapping.KEY_UNPICK_CONSTANTS) || !additionalMappingPaths.containsKey(Mapping.KEY_UNPICK_DEFINITIONS)) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarIn = pipeline.getStoragePath(inputFile, context);
		Path jarOut = pipeline.getStoragePath(outputFile, context);
		if (!MiscHelper.isJarEmpty(jarOut)) {
			return StepOutput.ofSingle(StepStatus.UP_TO_DATE, outputFile);
		}
		Files.deleteIfExists(jarOut);
		Path librariesDir = pipeline.getStoragePath(PipelineFilesystemStorage.LIBRARIES, context);
		if (librariesDir == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.FAILED);
		}
		ArrayList<String> params = new ArrayList<>(context.targetVersion().libraries().size() + 4);
		params.add(jarIn.toAbsolutePath().toString());
		params.add(jarOut.toAbsolutePath().toString());
		params.add(additionalMappingPaths.get(Mapping.KEY_UNPICK_DEFINITIONS).toAbsolutePath().toString());
		params.add(additionalMappingPaths.get(Mapping.KEY_UNPICK_CONSTANTS).toAbsolutePath().toString());
		params.addAll(context.targetVersion().libraries().stream().map(artifact -> artifact.resolve(librariesDir)).map(p -> p.toAbsolutePath().toString()).toList());
		daomephsta.unpick.cli.Main.main(params.toArray(new String[0]));
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}
}
