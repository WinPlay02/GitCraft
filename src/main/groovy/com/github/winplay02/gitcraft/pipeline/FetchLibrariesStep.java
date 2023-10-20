package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FetchLibrariesStep extends Step {

	private final Path rootPath;

	public FetchLibrariesStep(Path rootPath) {
		this.rootPath = rootPath;
	}

	public FetchLibrariesStep() {
		this(GitCraft.LIBRARY_STORE);
	}

	@Override
	public String getName() {
		return STEP_FETCH_LIBRARIES;
	}

	@Override
	public boolean ignoresMappings() {
		return true;
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		List<StepResult> results = new ArrayList<>(mcVersion.libraries().size());
		for (Artifact library : mcVersion.libraries()) {
			results.add(library.fetchArtifact(this.rootPath, "library"));
		}
		return StepResult.merge(results);
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return rootPath;
	}
}
