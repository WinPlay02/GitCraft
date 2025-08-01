package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.pipeline.workers.ArtifactsUnpacker;
import com.github.winplay02.gitcraft.pipeline.workers.Committer;
import com.github.winplay02.gitcraft.pipeline.workers.DataGenerator;
import com.github.winplay02.gitcraft.pipeline.workers.Decompiler;
import com.github.winplay02.gitcraft.pipeline.workers.JarsMerger;
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

import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.ARTIFACTS_CLIENT_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.ARTIFACTS_SERVER_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.ARTIFACTS_SERVER_ZIP;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.ASSETS_INDEX_JSON;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.ASSETS_OBJECTS;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.DATAGEN_REPORTS_ARCHIVE;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.DATAGEN_SNBT_ARCHIVE;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.DECOMPILED_CLIENT_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.DECOMPILED_MERGED_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.DECOMPILED_SERVER_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.MERGED_JAR_OBFUSCATED;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.REMAPPED_CLIENT_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.REMAPPED_MERGED_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.REMAPPED_SERVER_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.UNPACKED_SERVER_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.UNPICKED_CLIENT_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.UNPICKED_MERGED_JAR;
import static com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage.UNPICKED_SERVER_JAR;

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
			MiscHelper.panic("PipelineDescription is invalid, %s steps exists, %s are duplicates", this.steps.size(), this.steps.size() - nonDuplicateSteps);
		}
		// Validate dependencies
		for (Step step : this.steps) {
			validateStepDependencies(step);
		}
	}

	private void validateStepDependencies(Step step) {
		int stepIndex = this.steps.indexOf(step);
		StepDependency dependencies = this.stepDependencies.getOrDefault(step, StepDependency.EMPTY);
		for (Map.Entry<Step, DependencyType> entry : dependencies.dependencyTypes().entrySet()) {
			// Validate non-cyclic on itself
			if (entry.getKey() == step) {
				MiscHelper.panic("PipelineDescription is invalid, step %s depends on itself. This should be declared as an inter-version dependency", entry.getKey());
			}
			int dependencyIndex = this.steps.indexOf(entry.getKey());
			// Validate dependencies exist, if required
			if (dependencyIndex == -1) {
				if (entry.getValue() == DependencyType.REQUIRED) {
					MiscHelper.panic("PipelineDescription is invalid, step %s depends on required step %s, which is not part of this pipeline description", step, entry.getKey());
				}
				if (entry.getValue() == DependencyType.NOT_REQUIRED) {
					MiscHelper.println("WARNING: Step %s depends on optional step %s, which is not part of this pipeline description", step, entry.getKey());
				}
			}
			// Validate dependencies are executed before subject step
			if (dependencyIndex > stepIndex) {
				MiscHelper.panic("PipelineDescription is invalid, step %s depends on future step %s. This should be declared as an inter-version dependency", step, entry.getKey());
			}
		}
	}

	public static final BiFunction<PipelineFilesystemStorage<OrderedVersion>, StepResults<OrderedVersion>, StepInput> EMPTY_INPUT_PROVIDER = (_storage, _results) -> StepInput.EMPTY;

	// Reset is not in the default pipeline, as parallelization would be even trickier, since every step (more or less) depends on it
	public static final PipelineDescription<OrderedVersion> RESET_PIPELINE = new PipelineDescription<>("Reset", List.of(Step.RESET), Map.of(), Map.of(Step.RESET, StepDependency.ofInterVersion(Step.RESET)));

	public static final PipelineDescription<OrderedVersion> DEFAULT_PIPELINE = new PipelineDescription<>("Default",
		List.of(
			Step.FETCH_ARTIFACTS,
			Step.FETCH_LIBRARIES,
			Step.FETCH_ASSETS,
			Step.UNPACK_ARTIFACTS,
			Step.MERGE_OBFUSCATED_JARS,
			Step.DATAGEN,
			Step.PROVIDE_MAPPINGS,
			Step.REMAP_JARS,
			Step.MERGE_REMAPPED_JARS,
			Step.UNPICK_JARS,
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
				Step.REMAP_JARS, (storage, results) -> new Remapper.Inputs(results.getKeyIfExists(MERGED_JAR_OBFUSCATED), results.getKeyIfExists(ARTIFACTS_CLIENT_JAR), results.getKeyByPriority(ARTIFACTS_SERVER_JAR, UNPACKED_SERVER_JAR)),
				Step.MERGE_REMAPPED_JARS, (storage, results) -> new JarsMerger.Inputs(results.getKeyIfExists(REMAPPED_CLIENT_JAR), results.getKeyIfExists(REMAPPED_SERVER_JAR)),
				Step.UNPICK_JARS, (storage, results) -> new Unpicker.Inputs(results.getKeyIfExists(REMAPPED_MERGED_JAR), results.getKeyIfExists(REMAPPED_CLIENT_JAR), results.getKeyIfExists(REMAPPED_SERVER_JAR))
			),
			Map.of(
				Step.DECOMPILE_JARS, (storage, results) -> new Decompiler.Inputs(results.getKeyByPriority(UNPICKED_MERGED_JAR, REMAPPED_MERGED_JAR), results.getKeyByPriority(UNPICKED_CLIENT_JAR, REMAPPED_CLIENT_JAR), results.getKeyByPriority(UNPICKED_SERVER_JAR, REMAPPED_SERVER_JAR)),
				Step.COMMIT, (storage, results) -> new Committer.Inputs(
					results.getKeyIfExists(DECOMPILED_MERGED_JAR), results.getKeyIfExists(DECOMPILED_CLIENT_JAR), results.getKeyIfExists(DECOMPILED_SERVER_JAR),
					results.getKeyIfExists(ARTIFACTS_SERVER_ZIP), results.getKeyByPriority(MERGED_JAR_OBFUSCATED, ARTIFACTS_CLIENT_JAR),
					results.getKeyIfExists(DATAGEN_REPORTS_ARCHIVE), results.getKeyIfExists(ARTIFACTS_VANILLA_WORLDGEN_DATAPACK_ZIP), results.getKeyIfExists(DATAGEN_SNBT_ARCHIVE),
					results.getKeyIfExists(ASSETS_INDEX_JSON), results.getKeyIfExists(ASSETS_OBJECTS)
				)
			)
		),
		Map.of(
			Step.UNPACK_ARTIFACTS, StepDependency.ofHardIntraVersionOnly(Step.FETCH_ARTIFACTS),
			Step.MERGE_OBFUSCATED_JARS, StepDependency.ofHardIntraVersionOnly(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS),
			Step.DATAGEN, StepDependency.ofHardIntraVersionOnly(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.MERGE_OBFUSCATED_JARS),
			Step.REMAP_JARS, StepDependency.ofIntraVersion(Set.of(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.PROVIDE_MAPPINGS), Set.of(Step.MERGE_OBFUSCATED_JARS)),
			Step.MERGE_REMAPPED_JARS, StepDependency.ofHardIntraVersionOnly(Step.REMAP_JARS),
			Step.UNPICK_JARS, StepDependency.ofHardIntraVersionOnly(Step.FETCH_LIBRARIES, Step.PROVIDE_MAPPINGS, Step.REMAP_JARS),
			Step.DECOMPILE_JARS, StepDependency.mergeDependencies(StepDependency.ofIntraVersion(Set.of(Step.FETCH_ARTIFACTS, Step.FETCH_LIBRARIES, Step.UNPACK_ARTIFACTS), Set.of(Step.MERGE_OBFUSCATED_JARS, Step.REMAP_JARS, Step.MERGE_REMAPPED_JARS, Step.UNPICK_JARS)), StepDependency.ofInterVersion(Step.DECOMPILE_JARS)), // only allow one decompile job concurrently
			Step.COMMIT, StepDependency.mergeDependencies(StepDependency.ofHardIntraVersionOnly(Step.FETCH_ARTIFACTS, Step.UNPACK_ARTIFACTS, Step.FETCH_ASSETS, Step.DECOMPILE_JARS, Step.DATAGEN), StepDependency.ofInterVersion(Step.COMMIT))
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
