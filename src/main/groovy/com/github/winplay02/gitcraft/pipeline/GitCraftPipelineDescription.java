package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.launcher.LaunchPrepareLaunchableFile;
import com.github.winplay02.gitcraft.launcher.LaunchStepLaunch;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.workers.ArtifactsUnpacker;
import com.github.winplay02.gitcraft.pipeline.workers.Committer;
import com.github.winplay02.gitcraft.pipeline.workers.DataGenerator;
import com.github.winplay02.gitcraft.pipeline.workers.Decompiler;
import com.github.winplay02.gitcraft.pipeline.workers.JarsExceptor;
import com.github.winplay02.gitcraft.pipeline.workers.JarsMerger;
import com.github.winplay02.gitcraft.pipeline.workers.JarsNester;
import com.github.winplay02.gitcraft.pipeline.workers.JarsSignatureChanger;
import com.github.winplay02.gitcraft.pipeline.workers.LvtPatcher;
import com.github.winplay02.gitcraft.pipeline.workers.Preener;
import com.github.winplay02.gitcraft.pipeline.workers.Remapper;
import com.github.winplay02.gitcraft.pipeline.workers.Unpicker;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.unpick.UnpickFlavour;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage.*;

public final class GitCraftPipelineDescription {

	public static IStepContext.SimpleStepContext<OrderedVersion> getContext(OrderedVersion version, RepoWrapper repository, AbstractVersionGraph<OrderedVersion> versionGraph, ExecutorService executorService) {
		return new IStepContext.SimpleStepContext<>(repository, versionGraph, version, executorService);
	}

	private static Map<MinecraftJar, String> createIdentifierMap(OrderedVersion version) {
		Map<MinecraftJar, String> map = new HashMap<>();
		if (version.hasClientCode()) {
			map.put(MinecraftJar.CLIENT, version.clientJar().sha1sum().substring(0, 8));
		}
		if (version.hasServerCode()) {
			map.put(MinecraftJar.SERVER, MiscHelper.coalesce(version.serverDist().serverJar(), version.serverDist().windowsServer(), version.serverDist().serverZip()).sha1sum().substring(0, 8));
		}
		if (version.hasClientCode() && version.hasServerCode()) {
			map.put(MinecraftJar.MERGED, String.format("%s-%s", map.get(MinecraftJar.CLIENT), map.get(MinecraftJar.SERVER)));
		}
		return map;
	}

	public static GitCraftStepConfig getConfig(OrderedVersion version) {
		return new GitCraftStepConfig(
			createIdentifierMap(version),
			GitCraft.getApplicationConfiguration().getMappingsForMinecraftVersion(version).orElse(MappingFlavour.IDENTITY_UNMAPPED),
			GitCraft.getApplicationConfiguration().getUnpickForMinecraftVersion(version).orElse(UnpickFlavour.NONE),
			GitCraft.getApplicationConfiguration().getExceptionsForMinecraftVersion(version).orElse(ExceptionsFlavour.NONE),
			GitCraft.getApplicationConfiguration().getSignaturesForMinecraftVersion(version).orElse(SignaturesFlavour.NONE),
			GitCraft.getApplicationConfiguration().getNestsForMinecraftVersion(version).orElse(NestsFlavour.NONE),
			GitCraft.getApplicationConfiguration().patchLvt(),
			GitCraft.getApplicationConfiguration().enablePreening()
		);
	}

	// Reset is not in the default pipeline, as parallelization would be even trickier, since every step (more or less) depends on it
	public static final PipelineDescription<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> RESET_PIPELINE = new PipelineDescription<>(
		"Reset",
		List.of(GitCraftStep.RESET),
		Map.of(GitCraftStep.RESET, PipelineDescription.emptyInputProvider()),
		Map.of(GitCraftStep.RESET, StepDependencies.ofInterVersion(GitCraftStep.RESET)),
		GitCraftPipelineDescription::getContext,
		GitCraftPipelineDescription::getConfig
	);

	public static final PipelineDescription<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> DEFAULT_PIPELINE = new PipelineDescription<>("Default",
		List.of(
			GitCraftStep.FETCH_ARTIFACTS,
			GitCraftStep.FETCH_LIBRARIES,
			GitCraftStep.FETCH_ASSETS,
			GitCraftStep.UNPACK_ARTIFACTS,
			GitCraftStep.MERGE_OBFUSCATED_JARS,
			GitCraftStep.DATAGEN,
			GitCraftStep.PROVIDE_MAPPINGS,
			GitCraftStep.PROVIDE_UNPICK,
			GitCraftStep.PROVIDE_EXCEPTIONS,
			GitCraftStep.PROVIDE_SIGNATURES,
			GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES,
			GitCraftStep.APPLY_EXCEPTIONS,
			GitCraftStep.APPLY_SIGNATURES,
			GitCraftStep.REMAP_JARS,
			GitCraftStep.MERGE_REMAPPED_JARS,
			GitCraftStep.UNPICK_JARS,
			GitCraftStep.PROVIDE_NESTS,
			GitCraftStep.APPLY_NESTS,
			GitCraftStep.PREEN_JARS,
			GitCraftStep.DECOMPILE_JARS,
			GitCraftStep.COMMIT
		),
		MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				GitCraftStep.FETCH_ARTIFACTS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.FETCH_LIBRARIES, PipelineDescription.emptyInputProvider(),
				GitCraftStep.FETCH_ASSETS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.UNPACK_ARTIFACTS, (storage, results) -> new ArtifactsUnpacker.Inputs(results.getKeyIfExists(ARTIFACTS_SERVER_ZIP)),
				GitCraftStep.MERGE_OBFUSCATED_JARS, (storage, results) -> new JarsMerger.Inputs(results.getKeyIfExists(ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				GitCraftStep.DATAGEN, (storage, results) -> new DataGenerator.Inputs(results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR).orElseThrow(), results.getKeyByPriority(ARTIFACTS_MERGED_JAR, ARTIFACTS_CLIENT_JAR).orElseThrow()),
				GitCraftStep.PROVIDE_MAPPINGS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.PROVIDE_UNPICK, PipelineDescription.emptyInputProvider(),
				GitCraftStep.PROVIDE_EXCEPTIONS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.PROVIDE_SIGNATURES, PipelineDescription.emptyInputProvider()
			),
			Map.of(
				GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, (storage, results) -> new GitCraftStepWorker.JarTupleInput(results.getKeyIfExists(ARTIFACTS_MERGED_JAR), results.getKeyIfExists(ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				GitCraftStep.APPLY_EXCEPTIONS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(results.getKeyByPriority(LVT_PATCHED_MERGED_JAR, ARTIFACTS_MERGED_JAR), results.getKeyByPriority(LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				GitCraftStep.APPLY_SIGNATURES, (storage, results) -> new GitCraftStepWorker.JarTupleInput(results.getKeyByPriority(EXCEPTIONS_PATCHED_MERGED_JAR, LVT_PATCHED_MERGED_JAR, ARTIFACTS_MERGED_JAR), results.getKeyByPriority(EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(EXCEPTIONS_PATCHED_SERVER_JAR, LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				GitCraftStep.REMAP_JARS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(results.getKeyByPriority(SIGNATURES_PATCHED_MERGED_JAR, EXCEPTIONS_PATCHED_MERGED_JAR, LVT_PATCHED_MERGED_JAR, ARTIFACTS_MERGED_JAR), results.getKeyByPriority(SIGNATURES_PATCHED_CLIENT_JAR, EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(SIGNATURES_PATCHED_SERVER_JAR, EXCEPTIONS_PATCHED_SERVER_JAR, LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				GitCraftStep.MERGE_REMAPPED_JARS, (storage, results) -> new JarsMerger.Inputs(results.getKeyIfExists(REMAPPED_CLIENT_JAR), results.getKeyIfExists(REMAPPED_SERVER_JAR)),
				GitCraftStep.UNPICK_JARS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(results.getKeyIfExists(REMAPPED_MERGED_JAR), results.getKeyIfExists(REMAPPED_CLIENT_JAR), results.getKeyIfExists(REMAPPED_SERVER_JAR)),
				GitCraftStep.PROVIDE_NESTS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.APPLY_NESTS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(results.getKeyByPriority(UNPICKED_MERGED_JAR, REMAPPED_MERGED_JAR), results.getKeyByPriority(UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), results.getKeyByPriority(UNPICKED_SERVER_JAR, REMAPPED_SERVER_JAR)),
				GitCraftStep.PREEN_JARS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(results.getKeyByPriority(NESTED_MERGED_JAR, UNPICKED_MERGED_JAR, REMAPPED_MERGED_JAR), results.getKeyByPriority(NESTED_CLIENT_JAR, UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), results.getKeyByPriority(NESTED_SERVER_JAR, UNPICKED_SERVER_JAR, REMAPPED_SERVER_JAR))
			),
			Map.of(
				GitCraftStep.DECOMPILE_JARS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(results.getKeyByPriority(PREENED_MERGED_JAR, NESTED_MERGED_JAR, UNPICKED_MERGED_JAR, REMAPPED_MERGED_JAR), results.getKeyByPriority(PREENED_CLIENT_JAR, NESTED_CLIENT_JAR, UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), results.getKeyByPriority(PREENED_SERVER_JAR, NESTED_SERVER_JAR, UNPICKED_SERVER_JAR, REMAPPED_SERVER_JAR)),
				GitCraftStep.COMMIT, (storage, results) -> new Committer.Inputs(
					results.getKeyIfExists(DECOMPILED_MERGED_JAR), results.getKeyIfExists(DECOMPILED_CLIENT_JAR), results.getKeyIfExists(DECOMPILED_SERVER_JAR),
					results.getKeyIfExists(ARTIFACTS_SERVER_ZIP), results.getKeyByPriority(ARTIFACTS_MERGED_JAR, ARTIFACTS_CLIENT_JAR),
					results.getKeyIfExists(DATAGEN_REPORTS_ARCHIVE), results.getKeyIfExists(ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP), results.getKeyIfExists(DATAGEN_SNBT_ARCHIVE),
					results.getKeyIfExists(ASSETS_INDEX_JSON), results.getKeyIfExists(ASSETS_OBJECTS)
				)
			)
		),
		MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				GitCraftStep.UNPACK_ARTIFACTS, StepDependencies.ofHardIntraVersionOnly(GitCraftStep.FETCH_ARTIFACTS),
				GitCraftStep.MERGE_OBFUSCATED_JARS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS), Set.of(GitCraftStep.UNPACK_ARTIFACTS)),
				GitCraftStep.DATAGEN, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS), Set.of(GitCraftStep.UNPACK_ARTIFACTS, GitCraftStep.MERGE_OBFUSCATED_JARS)),
				GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.FETCH_LIBRARIES), Set.of(GitCraftStep.UNPACK_ARTIFACTS, GitCraftStep.MERGE_OBFUSCATED_JARS)),
				GitCraftStep.APPLY_EXCEPTIONS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.PROVIDE_EXCEPTIONS), Set.of(GitCraftStep.UNPACK_ARTIFACTS, GitCraftStep.MERGE_OBFUSCATED_JARS, GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES)),
				GitCraftStep.APPLY_SIGNATURES, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.PROVIDE_SIGNATURES), Set.of(GitCraftStep.UNPACK_ARTIFACTS, GitCraftStep.MERGE_OBFUSCATED_JARS, GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, GitCraftStep.APPLY_EXCEPTIONS))
			),
			Map.of(
				GitCraftStep.REMAP_JARS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.PROVIDE_MAPPINGS), Set.of(GitCraftStep.UNPACK_ARTIFACTS, GitCraftStep.MERGE_OBFUSCATED_JARS, GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, GitCraftStep.APPLY_EXCEPTIONS, GitCraftStep.APPLY_SIGNATURES)),
				GitCraftStep.MERGE_REMAPPED_JARS, StepDependencies.ofHardIntraVersionOnly(GitCraftStep.REMAP_JARS),
				GitCraftStep.UNPICK_JARS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_LIBRARIES, GitCraftStep.PROVIDE_UNPICK, GitCraftStep.PROVIDE_MAPPINGS, GitCraftStep.REMAP_JARS), Set.of(GitCraftStep.MERGE_REMAPPED_JARS)),
				GitCraftStep.PROVIDE_NESTS, StepDependencies.ofHardIntraVersionOnly(GitCraftStep.PROVIDE_MAPPINGS),
				GitCraftStep.APPLY_NESTS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.REMAP_JARS, GitCraftStep.PROVIDE_NESTS), Set.of(GitCraftStep.MERGE_REMAPPED_JARS, GitCraftStep.UNPICK_JARS)),
				GitCraftStep.PREEN_JARS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.REMAP_JARS), Set.of(GitCraftStep.MERGE_REMAPPED_JARS, GitCraftStep.UNPICK_JARS, GitCraftStep.APPLY_NESTS)),
				GitCraftStep.DECOMPILE_JARS, StepDependencies.merge(StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.FETCH_LIBRARIES), Set.of(GitCraftStep.UNPACK_ARTIFACTS, GitCraftStep.MERGE_OBFUSCATED_JARS, GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, GitCraftStep.APPLY_EXCEPTIONS, GitCraftStep.APPLY_SIGNATURES, GitCraftStep.REMAP_JARS, GitCraftStep.MERGE_REMAPPED_JARS, GitCraftStep.UNPICK_JARS, GitCraftStep.APPLY_NESTS, GitCraftStep.PREEN_JARS)), StepDependencies.ofInterVersion(GitCraftStep.DECOMPILE_JARS)), // only allow one decompile job concurrently
				GitCraftStep.COMMIT, StepDependencies.merge(StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.DECOMPILE_JARS), Set.of(GitCraftStep.UNPACK_ARTIFACTS, GitCraftStep.FETCH_ASSETS, GitCraftStep.DATAGEN)), StepDependencies.ofInterVersion(GitCraftStep.COMMIT))
			)
		),
		(graph, versionCtx) -> versionCtx.repository() != null && versionCtx.repository().existsRevWithCommitMessageNoExcept(versionCtx.targetVersion().toCommitMessage()),
		GitCraftPipelineDescription::getContext,
		GitCraftPipelineDescription::getConfig
	);

	// GC does not need to be in the default pipeline, as it is sufficient to only gc at the end once
	public static final PipelineDescription<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> GC_PIPELINE = new PipelineDescription<>(
		"GC",
		List.of(GitCraftStep.REPO_GARBAGE_COLLECTOR),
		Map.of(GitCraftStep.REPO_GARBAGE_COLLECTOR, PipelineDescription.emptyInputProvider()),
		Map.of(GitCraftStep.REPO_GARBAGE_COLLECTOR, StepDependencies.ofInterVersion(GitCraftStep.REPO_GARBAGE_COLLECTOR)),
		(graph, versionCtx) -> !graph.getRootVersions().stream().findFirst().map(versionCtx.targetVersion()::equals).orElse(false), // only run once (for 'first' root)
		GitCraftPipelineDescription::getContext,
		GitCraftPipelineDescription::getConfig
	);

	public static final PipelineDescription<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> LAUNCH_PIPELINE = new PipelineDescription<>(
		"Launch Client",
		List.of(
			GitCraftStep.FETCH_ARTIFACTS,
			GitCraftStep.FETCH_LIBRARIES,
			GitCraftStep.FETCH_ASSETS,
			GitCraftStep.LAUNCH_PREPARE_HARDLINK_ASSETS,
			GitCraftStep.PROVIDE_MAPPINGS,
			GitCraftStep.PROVIDE_UNPICK,
			GitCraftStep.PROVIDE_EXCEPTIONS,
			GitCraftStep.PROVIDE_SIGNATURES,
			GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES,
			GitCraftStep.APPLY_EXCEPTIONS,
			GitCraftStep.APPLY_SIGNATURES,
			GitCraftStep.REMAP_JARS,
			GitCraftStep.UNPICK_JARS,
			GitCraftStep.PROVIDE_NESTS,
			GitCraftStep.APPLY_NESTS,
			GitCraftStep.PREEN_JARS,
			GitCraftStep.LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE,
			GitCraftStep.LAUNCH_CLIENT
		),
		MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				GitCraftStep.FETCH_ARTIFACTS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.FETCH_LIBRARIES, PipelineDescription.emptyInputProvider(),
				GitCraftStep.FETCH_ASSETS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.LAUNCH_PREPARE_HARDLINK_ASSETS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.PROVIDE_MAPPINGS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.PROVIDE_UNPICK, PipelineDescription.emptyInputProvider(),
				GitCraftStep.PROVIDE_EXCEPTIONS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.PROVIDE_SIGNATURES, PipelineDescription.emptyInputProvider()
			),
			Map.of(
				GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, (storage, results) -> new GitCraftStepWorker.JarTupleInput(Optional.empty(), results.getKeyIfExists(ARTIFACTS_CLIENT_JAR), Optional.empty()),
				GitCraftStep.APPLY_EXCEPTIONS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(Optional.empty(), results.getKeyByPriority(LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), Optional.empty()),
				GitCraftStep.APPLY_SIGNATURES, (storage, results) -> new GitCraftStepWorker.JarTupleInput(Optional.empty(), results.getKeyByPriority(EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), Optional.empty()),
				GitCraftStep.REMAP_JARS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(Optional.empty(), results.getKeyByPriority(SIGNATURES_PATCHED_CLIENT_JAR, EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), Optional.empty()),
				GitCraftStep.UNPICK_JARS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(Optional.empty(), results.getKeyIfExists(REMAPPED_CLIENT_JAR), Optional.empty()),
				GitCraftStep.PROVIDE_NESTS, PipelineDescription.emptyInputProvider(),
				GitCraftStep.APPLY_NESTS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(Optional.empty(), results.getKeyByPriority(UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), Optional.empty()),
				GitCraftStep.PREEN_JARS, (storage, results) -> new GitCraftStepWorker.JarTupleInput(Optional.empty(), results.getKeyByPriority(NESTED_CLIENT_JAR, UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), Optional.empty()),
				GitCraftStep.LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE, (storage, results) -> new LaunchPrepareLaunchableFile.Inputs(results.getKeyByPriority(PREENED_CLIENT_JAR, NESTED_CLIENT_JAR, UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR, SIGNATURES_PATCHED_CLIENT_JAR, EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR)),
				GitCraftStep.LAUNCH_CLIENT, (storage, results) -> new LaunchStepLaunch.Inputs(results.getKeyByPriority(LAUNCHABLE_CLIENT_JAR, ARTIFACTS_CLIENT_JAR))
			)
		),
		MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				GitCraftStep.LAUNCH_PREPARE_HARDLINK_ASSETS, StepDependencies.ofHardIntraVersionOnly(GitCraftStep.FETCH_ASSETS),
				GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, StepDependencies.ofHardIntraVersionOnly(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.FETCH_LIBRARIES),
				GitCraftStep.APPLY_EXCEPTIONS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.PROVIDE_EXCEPTIONS), Set.of(GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES)),
				GitCraftStep.APPLY_SIGNATURES, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.PROVIDE_SIGNATURES), Set.of(GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, GitCraftStep.APPLY_EXCEPTIONS)),
				GitCraftStep.REMAP_JARS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.PROVIDE_MAPPINGS), Set.of(GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES, GitCraftStep.APPLY_EXCEPTIONS, GitCraftStep.APPLY_SIGNATURES))
			),
			Map.of(
				GitCraftStep.UNPICK_JARS, StepDependencies.ofHardIntraVersionOnly(GitCraftStep.FETCH_LIBRARIES, GitCraftStep.PROVIDE_UNPICK, GitCraftStep.PROVIDE_MAPPINGS, GitCraftStep.REMAP_JARS),
				GitCraftStep.PROVIDE_NESTS, StepDependencies.ofHardIntraVersionOnly(GitCraftStep.PROVIDE_MAPPINGS),
				GitCraftStep.APPLY_NESTS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.REMAP_JARS, GitCraftStep.PROVIDE_NESTS), Set.of(GitCraftStep.UNPICK_JARS)),
				GitCraftStep.PREEN_JARS, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.REMAP_JARS), Set.of(GitCraftStep.UNPICK_JARS, GitCraftStep.APPLY_NESTS)),
				GitCraftStep.LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE, StepDependencies.ofIntraVersion(Set.of(), Set.of(GitCraftStep.PREEN_JARS, GitCraftStep.APPLY_NESTS, GitCraftStep.UNPICK_JARS, GitCraftStep.REMAP_JARS, GitCraftStep.APPLY_SIGNATURES, GitCraftStep.APPLY_EXCEPTIONS, GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES)),
				GitCraftStep.LAUNCH_CLIENT, StepDependencies.ofIntraVersion(Set.of(GitCraftStep.LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE, GitCraftStep.LAUNCH_PREPARE_HARDLINK_ASSETS, GitCraftStep.FETCH_LIBRARIES, GitCraftStep.FETCH_ARTIFACTS, GitCraftStep.FETCH_ASSETS), Set.of(GitCraftStep.PREEN_JARS, GitCraftStep.APPLY_NESTS, GitCraftStep.UNPICK_JARS, GitCraftStep.REMAP_JARS, GitCraftStep.APPLY_SIGNATURES, GitCraftStep.APPLY_EXCEPTIONS, GitCraftStep.PATCH_LOCAL_VARIABLE_TABLES))
			)
		),
		(graph, versionCtx) -> !graph.getRootVersions().stream().findFirst().map(versionCtx.targetVersion()::equals).orElse(false), // only run once (for 'first' root)
		GitCraftPipelineDescription::getContext,
		GitCraftPipelineDescription::getConfig
	);
}
