package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.util.concurrent.ExecutorService;

public interface StepWorker<T extends AbstractVersion<T>, S extends StepInput> {

	Config config();

	StepOutput<T> run(Pipeline<T> pipeline, Context<T> context, S input, StepResults<T> results) throws Exception;

	default StepOutput<T> runGeneric(Pipeline<T> pipeline, Context<T> context, StepInput input, StepResults<T> results) throws Exception {
		@SuppressWarnings("unchecked")
		S castInput = (S) input;
		return this.run(pipeline, context, castInput, results);
	}

	record Config(MappingFlavour mappingFlavour) {

		@Override
		public String toString() {
			return "mappings: %s".formatted(mappingFlavour);
		}
	}

	record Context<T extends AbstractVersion<T>>(RepoWrapper repository, AbstractVersionGraph<T> versionGraph, T targetVersion, ExecutorService executorService) {

		public Context<T> withDifferingVersion(T targetVersion) {
			return new Context<>(repository, versionGraph, targetVersion, executorService);
		}

		@Override
		public String toString() {
			return "version: %s".formatted(targetVersion.friendlyVersion());
		}
	}
}
