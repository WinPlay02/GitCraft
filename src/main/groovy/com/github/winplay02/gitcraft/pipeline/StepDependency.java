package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record StepDependency(Map<Step, DependencyType> dependencyTypes, Set<Step> interVersionDependency) {
	public static final StepDependency EMPTY = new StepDependency(Map.of(), Set.of());

	public static StepDependency ofHardIntraVersionOnly(Step... dependencies) {
		return new StepDependency(Arrays.stream(dependencies).collect(Collectors.toMap(Function.identity(), __ -> DependencyType.REQUIRED)), Set.of());
	}

	public static StepDependency ofIntraVersion(Set<Step> requiredDependencies, Set<Step> optionalDependencies) {
		if (!MiscHelper.calculateSetIntersection(requiredDependencies, optionalDependencies).isEmpty()) {
			MiscHelper.panic("A dependency of a step cannot be both required an optional.");
		}
		Map<Step, DependencyType> dependencyTypes = requiredDependencies.stream().collect(Collectors.toMap(Function.identity(), __ -> DependencyType.REQUIRED));
		dependencyTypes.putAll(optionalDependencies.stream().collect(Collectors.toMap(Function.identity(), __ -> DependencyType.NOT_REQUIRED)));
		return new StepDependency(dependencyTypes, Set.of());
	}

	public static StepDependency ofInterVersion(Step... dependencies) {
		return new StepDependency(Map.of(), Arrays.stream(dependencies).collect(Collectors.toSet()));
	}

	public static StepDependency mergeDependencies(StepDependency d1, StepDependency d2) {
		Set<Step> intersection = MiscHelper.calculateSetIntersection(d1.dependencyTypes().keySet(), d2.dependencyTypes().keySet());
		for (Step step : intersection) {
			if (d1.dependencyTypes().get(step) != d2.dependencyTypes().get(step)) {
				MiscHelper.panic("Cannot merge step dependency declarations, as they are contradictory (Step: %s, type1: %s, type2: %s).", step, d1.dependencyTypes().get(step), d2.dependencyTypes().get(step));
			}
		}
		Map<Step, DependencyType> dependencyTypes = new HashMap<>(d1.dependencyTypes());
		dependencyTypes.putAll(d2.dependencyTypes());
		Set<Step> interVersionDependencies = new HashSet<>(d1.interVersionDependency());
		interVersionDependencies.addAll(d2.interVersionDependency());
		return new StepDependency(dependencyTypes, interVersionDependencies);
	}
}
