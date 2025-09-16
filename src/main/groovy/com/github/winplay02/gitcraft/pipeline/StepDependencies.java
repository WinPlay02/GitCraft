package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record StepDependencies(Map<Step, DependencyRelation> dependencyTypes, Set<Step> interVersionDependency) {
	// interVersionDependency are always NOT_REQUIRED; otherwise the pipeline would never execute, as the first step doesn't have any predecessor
	public static final StepDependencies EMPTY = new StepDependencies(Map.of(), Set.of());

	public static StepDependencies ofHardIntraVersionOnly(Step... dependencies) {
		return new StepDependencies(Arrays.stream(dependencies).collect(Collectors.toMap(Function.identity(), __ -> DependencyRelation.REQUIRED)), Set.of());
	}

	public static StepDependencies ofOptionalIntraVersionOnly(Step... dependencies) {
		return new StepDependencies(Arrays.stream(dependencies).collect(Collectors.toMap(Function.identity(), __ -> DependencyRelation.NOT_REQUIRED)), Set.of());
	}

	public static StepDependencies ofIntraVersion(Set<Step> requiredDependencies, Set<Step> optionalDependencies) {
		if (!MiscHelper.calculateSetIntersection(requiredDependencies, optionalDependencies).isEmpty()) {
			MiscHelper.panic("A dependency of a step cannot be both required an optional.");
		}
		Map<Step, DependencyRelation> dependencyTypes = requiredDependencies.stream().collect(Collectors.toMap(Function.identity(), __ -> DependencyRelation.REQUIRED));
		dependencyTypes.putAll(optionalDependencies.stream().collect(Collectors.toMap(Function.identity(), __ -> DependencyRelation.NOT_REQUIRED)));
		return new StepDependencies(dependencyTypes, Set.of());
	}

	public static StepDependencies ofInterVersion(Step... dependencies) {
		return new StepDependencies(Map.of(), Arrays.stream(dependencies).collect(Collectors.toSet()));
	}

	public static StepDependencies merge(StepDependencies d1, StepDependencies d2) {
		Set<Step> intersection = MiscHelper.calculateSetIntersection(d1.dependencyTypes().keySet(), d2.dependencyTypes().keySet());
		for (Step step : intersection) {
			if (d1.dependencyTypes().get(step) != d2.dependencyTypes().get(step)) {
				MiscHelper.panic("Cannot merge step dependency declarations, as they are contradictory (Step: %s, type1: %s, type2: %s).", step, d1.dependencyTypes().get(step), d2.dependencyTypes().get(step));
			}
		}
		Map<Step, DependencyRelation> dependencyTypes = new HashMap<>(d1.dependencyTypes());
		dependencyTypes.putAll(d2.dependencyTypes());
		Set<Step> interVersionDependencies = new HashSet<>(d1.interVersionDependency());
		interVersionDependencies.addAll(d2.interVersionDependency());
		return new StepDependencies(dependencyTypes, interVersionDependencies);
	}

	public void validate() {
		// TODO
	}

	public List<Step> steps() {
		return this.dependencyTypes.keySet().stream().toList();
	}

	public StepDependencies filterRelation(DependencyRelation relation) {
		return new StepDependencies(this.dependencyTypes.entrySet().stream().filter(dep -> dep.getValue() == relation).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), this.interVersionDependency);
	}

	public DependencyRelation getRelation(Step step) {
		return this.dependencyTypes.getOrDefault(step, DependencyRelation.NONE);
	}

	public StepDependencies filterInterOnly() {
		return new StepDependencies(Map.of(), this.interVersionDependency);
	}

	public StepDependencies filterIntraOnly() {
		return new StepDependencies(this.dependencyTypes, Set.of());
	}
}
