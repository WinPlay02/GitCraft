package com.github.winplay02.gitcraft.pipeline;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record StepDependencies(Set<StepDependency> dependencies) implements Iterable<StepDependency> {

	public static final StepDependencies EMPTY = new StepDependencies(Collections.emptySet());

	public static StepDependencies required(Step... steps) {
		return of(steps, DependencyRelation.REQUIRED);
	}

	public static StepDependencies notRequired(Step... steps) {
		return of(steps, DependencyRelation.NOT_REQUIRED);
	}

	public static StepDependencies of(Step[] steps, DependencyRelation relation) {
		return of(List.of(steps), relation);
	}

	public static StepDependencies of(List<Step> steps, DependencyRelation relation) {
		return new StepDependencies(steps.stream().map(step -> new StepDependency(step, relation)).collect(Collectors.toSet()));
	}

	public static StepDependencies merge(StepDependencies... dependencies) {
		Set<StepDependency> dst = new HashSet<>();
		for (StepDependencies deps : dependencies) {
			dst.addAll(deps.dependencies);
		}
		return new StepDependencies(dst);
	}

	public void validate() {
		// TODO
	}

	public List<Step> steps() {
		return dependencies.stream().map(StepDependency::step).toList();
	}

	public StepDependencies filterRelation(DependencyRelation relation) {
		return new StepDependencies(dependencies.stream().filter(dep -> dep.relation() == relation).collect(Collectors.toSet()));
	}

	public DependencyRelation getRelation(Step step) {
		return dependencies.stream().filter(dep -> dep.step() == step).map(StepDependency::relation).findAny().orElse(DependencyRelation.NONE);
	}

	@Override
	public Iterator<StepDependency> iterator() {
		return dependencies.iterator();
	}
}
