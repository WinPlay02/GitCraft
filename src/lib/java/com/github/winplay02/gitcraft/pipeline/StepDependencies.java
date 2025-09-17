package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record StepDependencies<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig>(Map<IStep<T, ?, C, D>, DependencyRelation> dependencyTypes, Set<IStep<T, ?, C, D>> interVersionDependency) {
	// interVersionDependency are always NOT_REQUIRED; otherwise the pipeline would never execute, as the first step doesn't have any predecessor
	public static final StepDependencies<?, ?, ?> EMPTY = new StepDependencies<>(Map.of(), Set.of());

	@SuppressWarnings("unchecked")
	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> StepDependencies<T, C, D> empty() {
		return (StepDependencies<T, C, D>) EMPTY;
	}

	@SafeVarargs
	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> StepDependencies<T, C, D> ofHardIntraVersionOnly(IStep<T, ?, C, D>... dependencies) {
		return new StepDependencies<>(Arrays.stream(dependencies).collect(Collectors.toMap(Function.identity(), __ -> DependencyRelation.REQUIRED)), Set.of());
	}

	@SafeVarargs
	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> StepDependencies<T, C, D> ofOptionalIntraVersionOnly(IStep<T, ?, C, D>... dependencies) {
		return new StepDependencies<>(Arrays.stream(dependencies).collect(Collectors.toMap(Function.identity(), __ -> DependencyRelation.NOT_REQUIRED)), Set.of());
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> StepDependencies<T, C, D> ofIntraVersion(Set<IStep<T, ?, C, D>> requiredDependencies, Set<IStep<T, ?, C, D>> optionalDependencies) {
		if (!MiscHelper.calculateSetIntersection(requiredDependencies, optionalDependencies).isEmpty()) {
			MiscHelper.panic("A dependency of a step cannot be both required an optional.");
		}
		Map<IStep<T, ?, C, D>, DependencyRelation> dependencyTypes = requiredDependencies.stream().collect(Collectors.toMap(Function.identity(), __ -> DependencyRelation.REQUIRED));
		dependencyTypes.putAll(optionalDependencies.stream().collect(Collectors.toMap(Function.identity(), __ -> DependencyRelation.NOT_REQUIRED)));
		return new StepDependencies<>(dependencyTypes, Set.of());
	}

	@SafeVarargs
	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> StepDependencies<T, C, D> ofInterVersion(IStep<T, ?, C, D>... dependencies) {
		return new StepDependencies<>(Map.of(), Arrays.stream(dependencies).collect(Collectors.toSet()));
	}

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> StepDependencies<T, C, D> merge(StepDependencies<T, C, D> d1, StepDependencies<T, C, D> d2) {
		Set<IStep<T, ?, C, D>> intersection = MiscHelper.calculateSetIntersection(d1.dependencyTypes().keySet(), d2.dependencyTypes().keySet());
		for (IStep<T, ?, C, D> step : intersection) {
			if (d1.dependencyTypes().get(step) != d2.dependencyTypes().get(step)) {
				MiscHelper.panic("Cannot merge step dependency declarations, as they are contradictory (Step: %s, type1: %s, type2: %s).", step, d1.dependencyTypes().get(step), d2.dependencyTypes().get(step));
			}
		}
		Map<IStep<T, ?, C, D>, DependencyRelation> dependencyTypes = new HashMap<>(d1.dependencyTypes());
		dependencyTypes.putAll(d2.dependencyTypes());
		Set<IStep<T, ?, C, D>> interVersionDependencies = new HashSet<>(d1.interVersionDependency());
		interVersionDependencies.addAll(d2.interVersionDependency());
		return new StepDependencies<>(dependencyTypes, interVersionDependencies);
	}

	public void validate() {
		// TODO
	}

	public List<IStep<T, ?, C, D>> steps() {
		return this.dependencyTypes.keySet().stream().toList();
	}

	public StepDependencies<T, C, D> filterRelation(DependencyRelation relation) {
		return new StepDependencies<>(this.dependencyTypes.entrySet().stream().filter(dep -> dep.getValue() == relation).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), this.interVersionDependency);
	}

	public DependencyRelation getRelation(IStep<T, ?, C, D> step) {
		return this.dependencyTypes.getOrDefault(step, DependencyRelation.NONE);
	}

	public StepDependencies<T, C, D> filterInterOnly() {
		return new StepDependencies<>(Map.of(), this.interVersionDependency);
	}

	public StepDependencies<T, C, D> filterIntraOnly() {
		return new StepDependencies<>(this.dependencyTypes, Set.of());
	}
}
