package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.RepoWrapper;

public interface StepWorker {

	Step step();

	Config config();

	StepStatus run(Pipeline pipeline, Context context) throws Exception;

	public record Config(MappingFlavour mappingFlavour) {

		@Override
		public String toString() {
			return "mappings: %s".formatted(mappingFlavour);
		}
	}

	public record Context(RepoWrapper repository, MinecraftVersionGraph versionGraph, OrderedVersion minecraftVersion) {

		@Override
		public String toString() {
			return "version: %s".formatted(minecraftVersion.launcherFriendlyVersionName());
		}
	}
}
