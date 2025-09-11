package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.launcher.LaunchPrepareLaunchableFile;
import com.github.winplay02.gitcraft.launcher.LaunchStepLaunch;
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
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.*;

public record PipelineDescription<T extends AbstractVersion<T>>(String descriptionName,
																List<Step> steps,
																Map<Step, BiFunction<PipelineFilesystemStorage<T>, StepResults<T>, StepInput>> stepInputMap,
																Map<Step, StepDependencies> stepDependencies,
																BiFunction<AbstractVersionGraph<T>, StepWorker.Context<T>, Boolean> skipVersion) {

	public PipelineDescription(String descriptionName,
							   List<Step> steps,
							   Map<Step, BiFunction<PipelineFilesystemStorage<T>, StepResults<T>, StepInput>> stepInputMap,
							   Map<Step, StepDependencies> stepDependencies) {
		this(descriptionName, steps, stepInputMap, stepDependencies, ($, $$) -> false);
	}

	public StepDependencies getStepDependencies(Step step) {
		return this.stepDependencies.getOrDefault(step, StepDependencies.EMPTY);
	}

	public void validate() {
		// Validate that each step only appears once
		int nonDuplicateSteps = new HashSet<>(this.steps).size();
		if (nonDuplicateSteps != this.steps.size()) {
			MiscHelper.panic("PipelineDescription %s is invalid, %s steps exists, %s are duplicates", this.descriptionName(), this.steps.size(), this.steps.size() - nonDuplicateSteps);
		}
		// Validate dependencies
		for (Step step : this.steps) {
			validateStepDependencies(step);
		}
		// Validate inputs exist
		for (Step step : this.steps) {
			if (!this.stepInputMap().containsKey(step)) {
				MiscHelper.panic("PipelineDescription %s is invalid, step %s has no declared inputs. If there are no inputs for this step, an EMPTY_INPUT_PROVIDER should be used instead.", this.descriptionName(), step.getName());
			}
		}
	}

	private void validateStepDependencies(Step step) {
		int stepIndex = this.steps.indexOf(step);
		StepDependencies dependencies = this.stepDependencies.getOrDefault(step, StepDependencies.EMPTY);
		for (StepDependency dependency : dependencies) {
			// Validate non-cyclic on itself
			if (dependency.step() == step) {
				MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on itself. This should be declared as an inter-version dependency", this.descriptionName(), dependency.step().getName());
			}
			int dependencyIndex = this.steps.indexOf(dependency.step());
			// Validate dependencies exist, if required
			if (dependencyIndex == -1) {
				if (dependency.relation() == DependencyRelation.REQUIRED) {
					MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on required step %s, which is not part of this pipeline description", this.descriptionName(), step.getName(), dependency.step().getName());
				}
				if (dependency.relation() == DependencyRelation.NOT_REQUIRED) {
					MiscHelper.println("WARNING: (In PipelineDescription %s) Step %s depends on optional step %s, which is not part of this pipeline description", this.descriptionName(), step.getName(), dependency.step().getName());
				}
			}
			// Validate dependencies are executed before subject step
			if (dependencyIndex > stepIndex) {
				MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on future step %s. This should be declared as an inter-version dependency", this.descriptionName(), step.getName(), dependency.step().getName());
			}
		}
	}

	public static final BiFunction<PipelineFilesystemStorage<OrderedVersion>, StepResults<OrderedVersion>, StepInput> EMPTY_INPUT_PROVIDER = (_storage, _results) -> StepInput.EMPTY;

	// Reset is not in the default pipeline, as parallelization would be even trickier, since every step (more or less) depends on it
	public static final PipelineDescription<OrderedVersion> RESET_PIPELINE = new PipelineDescription<>("Reset", List.of(Step.RESET), Map.of(Step.RESET, EMPTY_INPUT_PROVIDER), Map.of());

	public static final PipelineDescription<OrderedVersion> DEFAULT_PIPELINE = new PipelineDescription<>("Default",
		List.of(
			Step.FETCH_ARTIFACTS,
			Step.FETCH_LIBRARIES,
			Step.FETCH_ASSETS,
			Step.UNPACK_ARTIFACTS,
			Step.MERGE_OBFUSCATED_JARS,
			Step.DATAGEN,
			Step.PROVIDE_MAPPINGS,
			Step.PROVIDE_UNPICK,
			Step.PROVIDE_EXCEPTIONS,
			Step.PROVIDE_SIGNATURES,
			Step.PATCH_LOCAL_VARIABLE_TABLES,
			Step.APPLY_EXCEPTIONS,
			Step.APPLY_SIGNATURES,
			Step.REMAP_JARS,
			Step.MERGE_REMAPPED_JARS,
			Step.UNPICK_JARS,
			Step.PROVIDE_NESTS,
			Step.APPLY_NESTS,
			Step.PREEN_JARS,
			Step.DECOMPILE_JARS,
			Step.COMMIT
		),
		MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				Step.FETCH_ARTIFACTS, EMPTY_INPUT_PROVIDER,
				Step.FETCH_LIBRARIES, EMPTY_INPUT_PROVIDER,
				Step.FETCH_ASSETS, EMPTY_INPUT_PROVIDER,
				Step.UNPACK_ARTIFACTS, (storage, results) -> new ArtifactsUnpacker.Inputs(results.getKeyIfExists(ARTIFACTS_SERVER_ZIP)),
				Step.MERGE_OBFUSCATED_JARS, (storage, results) -> new JarsMerger.Inputs(results.getKeyIfExists(ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.DATAGEN, (storage, results) -> new DataGenerator.Inputs(results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR).orElseThrow(), results.getKeyByPriority(ARTIFACTS_MERGED_JAR, ARTIFACTS_CLIENT_JAR).orElseThrow()),
				Step.PROVIDE_MAPPINGS, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_UNPICK, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_EXCEPTIONS, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_SIGNATURES, EMPTY_INPUT_PROVIDER
			),
			Map.of(
				Step.PATCH_LOCAL_VARIABLE_TABLES, (storage, results) -> new LvtPatcher.Inputs(results.getKeyIfExists(ARTIFACTS_MERGED_JAR), results.getKeyIfExists(ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.APPLY_EXCEPTIONS, (storage, results) -> new JarsExceptor.Inputs(results.getKeyByPriority(LVT_PATCHED_MERGED_JAR, ARTIFACTS_MERGED_JAR), results.getKeyByPriority(LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.APPLY_SIGNATURES, (storage, results) -> new JarsSignatureChanger.Inputs(results.getKeyByPriority(EXCEPTIONS_PATCHED_MERGED_JAR, LVT_PATCHED_MERGED_JAR, ARTIFACTS_MERGED_JAR), results.getKeyByPriority(EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(EXCEPTIONS_PATCHED_SERVER_JAR, LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.REMAP_JARS, (storage, results) -> new Remapper.Inputs(results.getKeyByPriority(SIGNATURES_PATCHED_MERGED_JAR, EXCEPTIONS_PATCHED_MERGED_JAR, LVT_PATCHED_MERGED_JAR, ARTIFACTS_MERGED_JAR), results.getKeyByPriority(SIGNATURES_PATCHED_CLIENT_JAR, EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(SIGNATURES_PATCHED_SERVER_JAR, EXCEPTIONS_PATCHED_SERVER_JAR, LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.MERGE_REMAPPED_JARS, (storage, results) -> new JarsMerger.Inputs(results.getKeyIfExists(REMAPPED_CLIENT_JAR), results.getKeyIfExists(REMAPPED_SERVER_JAR)),
				Step.UNPICK_JARS, (storage, results) -> new Unpicker.Inputs(results.getKeyIfExists(REMAPPED_MERGED_JAR), results.getKeyIfExists(REMAPPED_CLIENT_JAR), results.getKeyIfExists(REMAPPED_SERVER_JAR)),
				Step.PROVIDE_NESTS, EMPTY_INPUT_PROVIDER,
				Step.APPLY_NESTS, (storage, results) -> new JarsNester.Inputs(results.getKeyByPriority(UNPICKED_MERGED_JAR, REMAPPED_MERGED_JAR), results.getKeyByPriority(UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), results.getKeyByPriority(UNPICKED_SERVER_JAR, REMAPPED_SERVER_JAR)),
				Step.PREEN_JARS, (storage, results) -> new Preener.Inputs(results.getKeyByPriority(NESTED_MERGED_JAR, UNPICKED_MERGED_JAR, REMAPPED_MERGED_JAR), results.getKeyByPriority(NESTED_CLIENT_JAR, UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), results.getKeyByPriority(NESTED_SERVER_JAR, UNPICKED_SERVER_JAR, REMAPPED_SERVER_JAR))
			),
			Map.of(
				Step.DECOMPILE_JARS, (storage, results) -> new Decompiler.Inputs(results.getKeyByPriority(PREENED_MERGED_JAR, NESTED_MERGED_JAR, UNPICKED_MERGED_JAR, REMAPPED_MERGED_JAR), results.getKeyByPriority(PREENED_CLIENT_JAR, NESTED_CLIENT_JAR, UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), results.getKeyByPriority(PREENED_SERVER_JAR, NESTED_SERVER_JAR, UNPICKED_SERVER_JAR, REMAPPED_SERVER_JAR)),
				Step.COMMIT, (storage, results) -> new Committer.Inputs(
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
				Step.UNPACK_ARTIFACTS, StepDependencies.required(Step.FETCH_ARTIFACTS),
				Step.MERGE_OBFUSCATED_JARS, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS),
						StepDependencies.notRequired(Step.UNPACK_ARTIFACTS)),
				Step.DATAGEN, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS),
						StepDependencies.notRequired(Step.UNPACK_ARTIFACTS, Step.MERGE_OBFUSCATED_JARS)),
				Step.PATCH_LOCAL_VARIABLE_TABLES, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.FETCH_LIBRARIES),
						StepDependencies.notRequired(Step.UNPACK_ARTIFACTS, Step.MERGE_OBFUSCATED_JARS)),
				Step.APPLY_EXCEPTIONS, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.PROVIDE_EXCEPTIONS),
						StepDependencies.notRequired(Step.UNPACK_ARTIFACTS, Step.MERGE_OBFUSCATED_JARS, Step.PATCH_LOCAL_VARIABLE_TABLES)),
				Step.APPLY_SIGNATURES, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.PROVIDE_SIGNATURES),
						StepDependencies.notRequired(Step.UNPACK_ARTIFACTS, Step.MERGE_OBFUSCATED_JARS, Step.PATCH_LOCAL_VARIABLE_TABLES, Step.APPLY_EXCEPTIONS))
			),
			Map.of(
				Step.REMAP_JARS, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.PROVIDE_MAPPINGS),
						StepDependencies.notRequired(Step.UNPACK_ARTIFACTS, Step.MERGE_OBFUSCATED_JARS, Step.PATCH_LOCAL_VARIABLE_TABLES, Step.APPLY_EXCEPTIONS, Step.APPLY_SIGNATURES)),
				Step.MERGE_REMAPPED_JARS, StepDependencies.required(Step.REMAP_JARS),
				Step.UNPICK_JARS, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_LIBRARIES, Step.PROVIDE_UNPICK, Step.PROVIDE_MAPPINGS, Step.REMAP_JARS),
						StepDependencies.notRequired(Step.MERGE_REMAPPED_JARS)),
				Step.PROVIDE_NESTS, StepDependencies.required(Step.PROVIDE_MAPPINGS),
				Step.APPLY_NESTS, StepDependencies.merge(
						StepDependencies.required(Step.REMAP_JARS, Step.PROVIDE_NESTS),
						StepDependencies.notRequired(Step.MERGE_REMAPPED_JARS, Step.UNPICK_JARS)),
				Step.PREEN_JARS, StepDependencies.merge(
						StepDependencies.required(Step.REMAP_JARS),
						StepDependencies.notRequired(Step.MERGE_REMAPPED_JARS, Step.UNPICK_JARS, Step.APPLY_NESTS)),
				Step.DECOMPILE_JARS, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.FETCH_LIBRARIES),
						StepDependencies.notRequired(Step.UNPACK_ARTIFACTS, Step.MERGE_OBFUSCATED_JARS, Step.PATCH_LOCAL_VARIABLE_TABLES, Step.APPLY_EXCEPTIONS, Step.APPLY_SIGNATURES, Step.REMAP_JARS, Step.MERGE_REMAPPED_JARS, Step.UNPICK_JARS, Step.APPLY_NESTS, Step.PREEN_JARS)),
				Step.COMMIT, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.DECOMPILE_JARS),
						StepDependencies.notRequired(Step.UNPACK_ARTIFACTS, Step.FETCH_ASSETS, Step.DATAGEN))
			)
		),
		(graph, versionCtx) -> versionCtx.repository() != null && versionCtx.repository().existsRevWithCommitMessageNoExcept(versionCtx.targetVersion().toCommitMessage()));

	// GC does not need to be in the default pipeline, as it is sufficient to only gc at the end once
	public static final PipelineDescription<OrderedVersion> GC_PIPELINE = new PipelineDescription<>(
		"GC",
		List.of(Step.REPO_GARBAGE_COLLECTOR),
		Map.of(Step.REPO_GARBAGE_COLLECTOR, EMPTY_INPUT_PROVIDER),
		Map.of(),
		(graph, versionCtx) -> !graph.getRootVersions().stream().findFirst().map(versionCtx.targetVersion()::equals).orElse(false) // only run once (for 'first' root)
	);

	public static final PipelineDescription<OrderedVersion> LAUNCH_PIPELINE = new PipelineDescription<>(
		"Launch Client",
		List.of(
			Step.FETCH_ARTIFACTS,
			Step.FETCH_LIBRARIES,
			Step.FETCH_ASSETS,
			Step.LAUNCH_PREPARE_HARDLINK_ASSETS,
			Step.PROVIDE_MAPPINGS,
			Step.PROVIDE_UNPICK,
			Step.PROVIDE_EXCEPTIONS,
			Step.PROVIDE_SIGNATURES,
			Step.PATCH_LOCAL_VARIABLE_TABLES,
			Step.APPLY_EXCEPTIONS,
			Step.APPLY_SIGNATURES,
			Step.REMAP_JARS,
			Step.UNPICK_JARS,
			Step.PROVIDE_NESTS,
			Step.APPLY_NESTS,
			Step.PREEN_JARS,
			Step.LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE,
			Step.LAUNCH_CLIENT
		),
		MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				Step.FETCH_ARTIFACTS, EMPTY_INPUT_PROVIDER,
				Step.FETCH_LIBRARIES, EMPTY_INPUT_PROVIDER,
				Step.FETCH_ASSETS, EMPTY_INPUT_PROVIDER,
				Step.LAUNCH_PREPARE_HARDLINK_ASSETS, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_MAPPINGS, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_UNPICK, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_EXCEPTIONS, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_SIGNATURES, EMPTY_INPUT_PROVIDER
			),
			Map.of(
				Step.PATCH_LOCAL_VARIABLE_TABLES, (storage, results) -> new LvtPatcher.Inputs(Optional.empty(), results.getKeyIfExists(ARTIFACTS_CLIENT_JAR), Optional.empty()),
				Step.APPLY_EXCEPTIONS, (storage, results) -> new JarsExceptor.Inputs(Optional.empty(), results.getKeyByPriority(LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), Optional.empty()),
				Step.APPLY_SIGNATURES, (storage, results) -> new JarsSignatureChanger.Inputs(Optional.empty(), results.getKeyByPriority(EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), Optional.empty()),
				Step.REMAP_JARS, (storage, results) -> new Remapper.Inputs(Optional.empty(), results.getKeyByPriority(SIGNATURES_PATCHED_CLIENT_JAR, EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), Optional.empty()),
				Step.UNPICK_JARS, (storage, results) -> new Unpicker.Inputs(Optional.empty(), results.getKeyIfExists(REMAPPED_CLIENT_JAR), Optional.empty()),
				Step.PROVIDE_NESTS, EMPTY_INPUT_PROVIDER,
				Step.APPLY_NESTS, (storage, results) -> new JarsNester.Inputs(Optional.empty(), results.getKeyByPriority(UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), Optional.empty()),
				Step.PREEN_JARS, (storage, results) -> new Preener.Inputs(Optional.empty(), results.getKeyByPriority(NESTED_CLIENT_JAR, UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), Optional.empty()),
				Step.LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE, (storage, results) -> new LaunchPrepareLaunchableFile.Inputs(results.getKeyByPriority(PREENED_CLIENT_JAR, NESTED_CLIENT_JAR, UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR, SIGNATURES_PATCHED_CLIENT_JAR, EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR)),
				Step.LAUNCH_CLIENT, (storage, results) -> new LaunchStepLaunch.Inputs(results.getKeyByPriority(LAUNCHABLE_CLIENT_JAR, ARTIFACTS_CLIENT_JAR))
			)
		),
		MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				Step.LAUNCH_PREPARE_HARDLINK_ASSETS, StepDependencies.required(Step.FETCH_ASSETS),
				Step.PATCH_LOCAL_VARIABLE_TABLES, StepDependencies.required(Step.FETCH_ARTIFACTS, Step.FETCH_LIBRARIES),
				Step.APPLY_EXCEPTIONS, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.PROVIDE_EXCEPTIONS),
						StepDependencies.notRequired(Step.PATCH_LOCAL_VARIABLE_TABLES)),
				Step.APPLY_SIGNATURES, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.PROVIDE_SIGNATURES),
						StepDependencies.notRequired(Step.PATCH_LOCAL_VARIABLE_TABLES, Step.APPLY_EXCEPTIONS)),
				Step.REMAP_JARS, StepDependencies.merge(
						StepDependencies.required(Step.FETCH_ARTIFACTS, Step.PROVIDE_MAPPINGS),
						StepDependencies.notRequired(Step.PATCH_LOCAL_VARIABLE_TABLES, Step.APPLY_EXCEPTIONS, Step.APPLY_SIGNATURES))
			),
			Map.of(
				Step.UNPICK_JARS, StepDependencies.required(Step.FETCH_LIBRARIES, Step.PROVIDE_UNPICK, Step.PROVIDE_MAPPINGS, Step.REMAP_JARS),
				Step.PROVIDE_NESTS, StepDependencies.required(Step.PROVIDE_MAPPINGS),
				Step.APPLY_NESTS, StepDependencies.merge(
						StepDependencies.required(Step.REMAP_JARS, Step.PROVIDE_NESTS),
						StepDependencies.notRequired(Step.UNPICK_JARS)),
				Step.PREEN_JARS, StepDependencies.merge(
						StepDependencies.required(Step.REMAP_JARS),
						StepDependencies.notRequired(Step.UNPICK_JARS, Step.APPLY_NESTS)),
				Step.LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE, StepDependencies.notRequired(Step.PREEN_JARS, Step.APPLY_NESTS, Step.UNPICK_JARS, Step.REMAP_JARS, Step.APPLY_SIGNATURES, Step.APPLY_EXCEPTIONS, Step.PATCH_LOCAL_VARIABLE_TABLES),
				Step.LAUNCH_CLIENT, StepDependencies.merge(
						StepDependencies.required(Step.LAUNCH_PREPARE_CONSTRUCT_LAUNCHABLE_FILE, Step.LAUNCH_PREPARE_HARDLINK_ASSETS, Step.FETCH_LIBRARIES, Step.FETCH_ARTIFACTS, Step.FETCH_ASSETS),
						StepDependencies.notRequired(Step.PREEN_JARS, Step.APPLY_NESTS, Step.UNPICK_JARS, Step.REMAP_JARS, Step.APPLY_SIGNATURES, Step.APPLY_EXCEPTIONS, Step.PATCH_LOCAL_VARIABLE_TABLES))
			)
		),
		(graph, versionCtx) -> !graph.getRootVersions().stream().findFirst().map(versionCtx.targetVersion()::equals).orElse(false) // only run once (for 'first' root)
	);
}
