package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.*;

public record PipelineDescription<T extends AbstractVersion<T>>(String descriptionName,
																List<Step> steps,
																Map<Step, BiFunction<PipelineFilesystemStorage<T>, StepResults<T>, StepInput>> stepInputMap,
																Map<Step, StepDependency> stepDependencies,
																BiFunction<AbstractVersionGraph<T>, StepWorker.Context<T>, Boolean> skipVersion) {

	public PipelineDescription(String descriptionName,
							   List<Step> steps,
							   Map<Step, BiFunction<PipelineFilesystemStorage<T>, StepResults<T>, StepInput>> stepInputMap,
							   Map<Step, StepDependency> stepDependencies) {
		this(descriptionName, steps, stepInputMap, stepDependencies, ($, $$) -> false);
	}

	public Set<Step> getIntraVersionDependencies(Step step) {
		return this.stepDependencies.getOrDefault(step, StepDependency.EMPTY).dependencyTypes().keySet();
	}

	public Set<Step> getInterVersionDependencies(Step step) {
		return this.stepDependencies.getOrDefault(step, StepDependency.EMPTY).interVersionDependency();
	}

	public Set<Step> getDependenciesOfRequirement(Step step, DependencyType type) {
		return this.stepDependencies.getOrDefault(step, StepDependency.EMPTY).dependencyTypes().entrySet().stream().filter(entry -> entry.getValue() == type).map(Map.Entry::getKey).collect(Collectors.toSet());
	}

	public DependencyType getDependencyType(Step step, Step dependentStep) {
		return this.stepDependencies.getOrDefault(step, StepDependency.EMPTY).dependencyTypes().getOrDefault(dependentStep, DependencyType.NONE);
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
				MiscHelper.panic("PipelineDescription %s is invalid, step %s has no declared inputs. If there are no inputs for this step, an EMPTY_INPUT_PROVIDER should be used instead.", this.descriptionName(), step);
			}
		}
	}

	private void validateStepDependencies(Step step) {
		int stepIndex = this.steps.indexOf(step);
		StepDependency dependencies = this.stepDependencies.getOrDefault(step, StepDependency.EMPTY);
		for (Map.Entry<Step, DependencyType> entry : dependencies.dependencyTypes().entrySet()) {
			// Validate non-cyclic on itself
			if (entry.getKey() == step) {
				MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on itself. This should be declared as an inter-version dependency", this.descriptionName(), entry.getKey());
			}
			int dependencyIndex = this.steps.indexOf(entry.getKey());
			// Validate dependencies exist, if required
			if (dependencyIndex == -1) {
				if (entry.getValue() == DependencyType.REQUIRED) {
					MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on required step %s, which is not part of this pipeline description", this.descriptionName(), step, entry.getKey());
				}
				if (entry.getValue() == DependencyType.NOT_REQUIRED) {
					MiscHelper.println("WARNING: (In PipelineDescription %s) Step %s depends on optional step %s, which is not part of this pipeline description", this.descriptionName(), step, entry.getKey());
				}
			}
			// Validate dependencies are executed before subject step
			if (dependencyIndex > stepIndex) {
				MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on future step %s. This should be declared as an inter-version dependency", this.descriptionName(), step, entry.getKey());
			}
		}
	}

	public static final BiFunction<PipelineFilesystemStorage<OrderedVersion>, StepResults<OrderedVersion>, StepInput> EMPTY_INPUT_PROVIDER = (_storage, _results) -> StepInput.EMPTY;

	// Reset is not in the default pipeline, as parallelization would be even trickier, since every step (more or less) depends on it
	public static final PipelineDescription<OrderedVersion> RESET_PIPELINE = new PipelineDescription<>("Reset", List.of(Step.RESET), Map.of(Step.RESET, EMPTY_INPUT_PROVIDER), Map.of(Step.RESET, StepDependency.ofInterVersion(Step.RESET)));

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
				Step.DATAGEN, (storage, results) -> new DataGenerator.Inputs(results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR).orElseThrow(), results.getKeyByPriority(MERGED_JAR_OBFUSCATED, ARTIFACTS_CLIENT_JAR).orElseThrow()),
				Step.PROVIDE_MAPPINGS, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_UNPICK, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_EXCEPTIONS, EMPTY_INPUT_PROVIDER,
				Step.PROVIDE_SIGNATURES, EMPTY_INPUT_PROVIDER
			),
			Map.of(
				Step.PATCH_LOCAL_VARIABLE_TABLES, (storage, results) -> new LvtPatcher.Inputs(results.getKeyIfExists(MERGED_JAR_OBFUSCATED), results.getKeyIfExists(ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.APPLY_EXCEPTIONS, (storage, results) -> new JarsExceptor.Inputs(results.getKeyByPriority(LVT_PATCHED_MERGED_JAR, MERGED_JAR_OBFUSCATED), results.getKeyByPriority(LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.APPLY_SIGNATURES, (storage, results) -> new JarsSignatureChanger.Inputs(results.getKeyByPriority(EXCEPTIONS_PATCHED_MERGED_JAR, LVT_PATCHED_MERGED_JAR, MERGED_JAR_OBFUSCATED), results.getKeyByPriority(EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(EXCEPTIONS_PATCHED_SERVER_JAR, LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.REMAP_JARS, (storage, results) -> new Remapper.Inputs(results.getKeyByPriority(SIGNATURES_PATCHED_MERGED_JAR, EXCEPTIONS_PATCHED_MERGED_JAR, LVT_PATCHED_MERGED_JAR, MERGED_JAR_OBFUSCATED), results.getKeyByPriority(SIGNATURES_PATCHED_CLIENT_JAR, EXCEPTIONS_PATCHED_CLIENT_JAR, LVT_PATCHED_CLIENT_JAR, ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(SIGNATURES_PATCHED_SERVER_JAR, EXCEPTIONS_PATCHED_SERVER_JAR, LVT_PATCHED_SERVER_JAR, ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
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
					results.getKeyIfExists(ARTIFACTS_SERVER_ZIP), results.getKeyByPriority(MERGED_JAR_OBFUSCATED, ARTIFACTS_CLIENT_JAR),
					results.getKeyIfExists(DATAGEN_REPORTS_ARCHIVE), results.getKeyIfExists(ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP), results.getKeyIfExists(DATAGEN_SNBT_ARCHIVE),
					results.getKeyIfExists(ASSETS_INDEX_JSON), results.getKeyIfExists(ASSETS_OBJECTS)
				)
			)
		),
		MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				Step.UNPACK_ARTIFACTS, StepDependency.ofHardIntraVersionOnly(Step.FETCH_ARTIFACTS),
				Step.MERGE_OBFUSCATED_JARS, StepDependency.ofHardIntraVersionOnly(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS),
				Step.DATAGEN, StepDependency.ofHardIntraVersionOnly(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.MERGE_OBFUSCATED_JARS),
				Step.PATCH_LOCAL_VARIABLE_TABLES, StepDependency.ofIntraVersion(Set.of(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.FETCH_LIBRARIES), Set.of(Step.MERGE_OBFUSCATED_JARS)),
				Step.APPLY_EXCEPTIONS, StepDependency.ofIntraVersion(Set.of(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.PROVIDE_EXCEPTIONS), Set.of(Step.MERGE_OBFUSCATED_JARS, Step.PATCH_LOCAL_VARIABLE_TABLES)),
				Step.APPLY_SIGNATURES, StepDependency.ofIntraVersion(Set.of(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.PROVIDE_SIGNATURES), Set.of(Step.MERGE_OBFUSCATED_JARS, Step.PATCH_LOCAL_VARIABLE_TABLES, Step.APPLY_EXCEPTIONS))
			),
			Map.of(
				Step.REMAP_JARS, StepDependency.ofIntraVersion(Set.of(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.PROVIDE_MAPPINGS), Set.of(Step.MERGE_OBFUSCATED_JARS, Step.PATCH_LOCAL_VARIABLE_TABLES, Step.APPLY_EXCEPTIONS, Step.APPLY_SIGNATURES)),
				Step.MERGE_REMAPPED_JARS, StepDependency.ofHardIntraVersionOnly(Step.REMAP_JARS),
				Step.UNPICK_JARS, StepDependency.ofHardIntraVersionOnly(Step.FETCH_LIBRARIES, Step.PROVIDE_UNPICK, Step.PROVIDE_MAPPINGS, Step.REMAP_JARS),
				Step.PROVIDE_NESTS, StepDependency.ofHardIntraVersionOnly(Step.PROVIDE_MAPPINGS),
				Step.APPLY_NESTS, StepDependency.ofIntraVersion(Set.of(Step.REMAP_JARS, Step.PROVIDE_NESTS), Set.of(Step.UNPICK_JARS)),
				Step.PREEN_JARS, StepDependency.ofIntraVersion(Set.of(Step.REMAP_JARS), Set.of(Step.APPLY_NESTS, Step.UNPICK_JARS)),
				Step.DECOMPILE_JARS, StepDependency.mergeDependencies(StepDependency.ofIntraVersion(Set.of(Step.FETCH_ARTIFACTS, Step.FETCH_LIBRARIES, Step.UNPACK_ARTIFACTS), Set.of(Step.MERGE_OBFUSCATED_JARS, Step.PATCH_LOCAL_VARIABLE_TABLES, Step.APPLY_EXCEPTIONS, Step.APPLY_SIGNATURES, Step.REMAP_JARS, Step.MERGE_REMAPPED_JARS, Step.UNPICK_JARS, Step.APPLY_NESTS, Step.PREEN_JARS)), StepDependency.ofInterVersion(Step.DECOMPILE_JARS)), // only allow one decompile job concurrently
				Step.COMMIT, StepDependency.mergeDependencies(StepDependency.ofHardIntraVersionOnly(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.FETCH_ASSETS, Step.DECOMPILE_JARS, Step.DATAGEN), StepDependency.ofInterVersion(Step.COMMIT))
			)
		),
		(graph, versionCtx) -> versionCtx.repository() != null && versionCtx.repository().existsRevWithCommitMessageNoExcept(versionCtx.targetVersion().toCommitMessage()));

	// GC does not need to be in the default pipeline, as it is sufficient to only gc at the end once
	public static final PipelineDescription<OrderedVersion> GC_PIPELINE = new PipelineDescription<>(
		"GC",
		List.of(Step.REPO_GARBAGE_COLLECTOR),
		Map.of(Step.REPO_GARBAGE_COLLECTOR, EMPTY_INPUT_PROVIDER),
		Map.of(Step.REPO_GARBAGE_COLLECTOR, StepDependency.ofInterVersion(Step.REPO_GARBAGE_COLLECTOR)),
		(graph, versionCtx) -> !graph.getRootVersions().stream().findFirst().map(versionCtx.targetVersion()::equals).orElse(false) // only run once (for 'first' root)
	);
}
