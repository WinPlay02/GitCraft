package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record PipelineExecutionGraph<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig>
	(Set<IPipeline.TupleVersionStep<T, C, D>> stepVersionSubsetVertices, Map<IPipeline.TupleVersionStep<T, C, D>, Set<IPipeline.TupleVersionStep<T, C, D>>> stepVersionSubsetEdges) {

	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> PipelineExecutionGraph<T, C, D> populate(PipelineDescription<T, C, D> description, AbstractVersionGraph<T> versionGraph) {
		Set<IPipeline.TupleVersionStep<T, C, D>> stepVersionSubsetVertices = new HashSet<>();
		// directed: (target, source)
		Map<IPipeline.TupleVersionStep<T, C, D>, Set<IPipeline.TupleVersionStep<T, C, D>>> stepVersionSubsetEdges = new HashMap<>();
		for (T version : versionGraph) {
			for (IStep<T, ?, C, D> step : description.steps()) {
				IPipeline.TupleVersionStep<T, C, D> node = new IPipeline.TupleVersionStep<>(step, version);
				stepVersionSubsetVertices.add(node);
				stepVersionSubsetEdges.computeIfAbsent(node, __ -> new HashSet<>());
				// Inter-Version dependency: depend on previous version only; logically should depend on all previous versions
				// but this is not necessary as this dependency applies transitively in a valid pipeline description (step depending on itself)
				for (IStep<T, ?, C, D> interVersionDependencyStep : description.getInterVersionDependencies(step)) {
					for (T previousVersion : versionGraph.getPreviousVertices(version)) {
						stepVersionSubsetEdges.get(node).add(new IPipeline.TupleVersionStep<>(interVersionDependencyStep, previousVersion));
					}
				}
				// Intra-Version dependency
				for (IStep<T, ?, C, D> intraVersionDependencyStep : description.getIntraVersionDependencies(step)) {
					DependencyRelation depType = description.getDependencyType(step, intraVersionDependencyStep);
					if (depType != null && depType.isDependency()) {
						stepVersionSubsetEdges.get(node).add(new IPipeline.TupleVersionStep<>(intraVersionDependencyStep, version));
					}
				}
			}
		}
		// TODO validate execution graph
		return new PipelineExecutionGraph<>(Collections.unmodifiableSet(stepVersionSubsetVertices), Collections.unmodifiableMap(stepVersionSubsetEdges));
	}

	protected Set<IPipeline.TupleVersionStep<T, C, D>> nextTuples(Set<IPipeline.TupleVersionStep<T, C, D>> completedSubset) {
		return stepVersionSubsetEdges.entrySet().stream().filter(entry -> !completedSubset.contains(entry.getKey())).filter(entry -> MiscHelper.calculateAsymmetricSetDifference(entry.getValue(), completedSubset).isEmpty()).map(java.util.Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
	}
}
