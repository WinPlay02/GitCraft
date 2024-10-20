package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.RepoWrapper;

public interface StepWorker<S extends StepInput> {

	Config config();

	StepOutput run(Pipeline pipeline, Context context, S input, StepResults results) throws Exception;

	default StepOutput runGeneric(Pipeline pipeline, Context context, StepInput input, StepResults results) throws Exception {
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

	record Context(RepoWrapper repository, MinecraftVersionGraph versionGraph, OrderedVersion minecraftVersion) {

		public Context withDifferingVersion(OrderedVersion minecraftVersion) {
			return new Context(repository, versionGraph, minecraftVersion);
		}

		@Override
		public String toString() {
			return "version: %s".formatted(minecraftVersion.launcherFriendlyVersionName());
		}
	}
}
