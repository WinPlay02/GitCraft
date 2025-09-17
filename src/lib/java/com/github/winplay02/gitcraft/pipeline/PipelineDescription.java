package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PipelineDescription<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig>(
	String descriptionName,
	List<IStep<T, ?, C, D>> steps,
	Map<IStep<T, ?, C, D>, BiFunction<PipelineFilesystemStorage<T, C, D>, StepResults<T, C, D>, StepInput>> stepInputMap,
	Map<IStep<T, ?, C, D>, StepDependencies<T, C, D>> stepDependencies,
	BiFunction<AbstractVersionGraph<T>, C, Boolean> skipVersion,
	ContextCreator<T, C> contextCreator,
	Function<T, D> configCreator
) {

	@FunctionalInterface
	public interface ContextCreator<T extends AbstractVersion<T>, C extends IStepContext<C, T>> {
		C getContext(T version, RepoWrapper repository, AbstractVersionGraph<T> versionGraph, ExecutorService executorService);
	}

	public static final BiFunction<PipelineFilesystemStorage<?, ?, ?>, StepResults<?, ?, ?>, StepInput> EMPTY_INPUT_PROVIDER = (_storage, _results) -> StepInput.EMPTY;

	@SuppressWarnings("unchecked")
	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> BiFunction<PipelineFilesystemStorage<T, C, D>, StepResults<T, C, D>, StepInput> emptyInputProvider() {
		return (BiFunction<PipelineFilesystemStorage<T, C, D>, StepResults<T, C, D>, StepInput>) (Object) EMPTY_INPUT_PROVIDER;
	}

	public PipelineDescription(String descriptionName,
							   List<IStep<T, ?, C, D>> steps,
							   Map<IStep<T, ?, C, D>, BiFunction<PipelineFilesystemStorage<T, C, D>, StepResults<T, C, D>, StepInput>> stepInputMap,
							   Map<IStep<T, ?, C, D>, StepDependencies<T, C, D>> stepDependencies,
							   ContextCreator<T, C> contextCreator,
							   Function<T, D> configCreator) {
		this(descriptionName, steps, stepInputMap, stepDependencies, ($, $$) -> false, contextCreator, configCreator);
	}

	public Set<IStep<T, ?, C, D>> getIntraVersionDependencies(IStep<T, ?, C, D> step) {
		return this.stepDependencies.getOrDefault(step, StepDependencies.empty()).dependencyTypes().keySet();
	}

	public Set<IStep<T, ?, C, D>> getInterVersionDependencies(IStep<T, ?, C, D> step) {
		return this.stepDependencies.getOrDefault(step, StepDependencies.empty()).interVersionDependency();
	}

	public Set<IStep<T, ?, C, D>> getDependenciesOfRequirement(IStep<T, ?, C, D> step, DependencyRelation type) {
		return this.stepDependencies.getOrDefault(step, StepDependencies.empty()).dependencyTypes().entrySet().stream().filter(entry -> entry.getValue() == type).map(Map.Entry::getKey).collect(Collectors.toSet());
	}

	public DependencyRelation getDependencyType(IStep<T, ?, C, D> step, IStep<T, ?, C, D> dependentStep) {
		return this.stepDependencies.getOrDefault(step, StepDependencies.empty()).dependencyTypes().getOrDefault(dependentStep, DependencyRelation.NONE);
	}

	public void validate() {
		// Validate that each step only appears once
		int nonDuplicateSteps = new HashSet<>(this.steps).size();
		if (nonDuplicateSteps != this.steps.size()) {
			MiscHelper.panic("PipelineDescription %s is invalid, %s steps exists, %s are duplicates", this.descriptionName(), this.steps.size(), this.steps.size() - nonDuplicateSteps);
		}
		// Validate dependencies
		for (IStep<T, ?, C, D> step : this.steps) {
			validateStepDependencies(step);
		}
		// Validate inputs exist
		for (IStep<T, ?, C, D> step : this.steps) {
			if (!this.stepInputMap().containsKey(step)) {
				MiscHelper.panic("PipelineDescription %s is invalid, step %s has no declared inputs. If there are no inputs for this step, an EMPTY_INPUT_PROVIDER should be used instead.", this.descriptionName(), step);
			}
		}
	}

	private void validateStepDependencies(IStep<T, ?, C, D> step) {
		int stepIndex = this.steps.indexOf(step);
		StepDependencies<T, C, D> dependencies = this.stepDependencies.getOrDefault(step, StepDependencies.empty());
		for (Map.Entry<IStep<T, ?, C, D>, DependencyRelation> entry : dependencies.dependencyTypes().entrySet()) {
			// Validate non-cyclic on itself
			if (entry.getKey() == step) {
				MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on itself. This should be declared as an inter-version dependency", this.descriptionName(), entry.getKey());
			}
			int dependencyIndex = this.steps.indexOf(entry.getKey());
			// Validate dependencies exist, if required
			if (dependencyIndex == -1) {
				if (entry.getValue() == DependencyRelation.REQUIRED) {
					MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on required step %s, which is not part of this pipeline description", this.descriptionName(), step, entry.getKey());
				}
				if (entry.getValue() == DependencyRelation.NOT_REQUIRED) {
					MiscHelper.println("WARNING: (In PipelineDescription %s) Step %s depends on optional step %s, which is not part of this pipeline description", this.descriptionName(), step, entry.getKey());
				}
			}
			// Validate dependencies are executed before subject step
			if (dependencyIndex > stepIndex) {
				MiscHelper.panic("PipelineDescription %s is invalid, step %s depends on future step %s. This should be declared as an inter-version dependency", this.descriptionName(), step, entry.getKey());
			}
		}
	}
}
