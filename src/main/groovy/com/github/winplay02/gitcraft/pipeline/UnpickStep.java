package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class UnpickStep extends Step {

	private final Path rootPath;

	public UnpickStep(Path rootPath) {
		this.rootPath = rootPath;
	}

	public UnpickStep() {
		this(GitCraftPaths.REMAPPED);
	}

	@Override
	public String getName() {
		return STEP_UNPICK;
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return this.rootPath.resolve(String.format("%s-%s-unpicked.jar", mcVersion.launcherFriendlyVersionName(), mappingFlavour.toString()));
	}

	@Override
	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		Map<String, Path> additionalMappingPaths = mappingFlavour.getMappingImpl().getAdditionalMappingInformation(mcVersion);
		return mappingFlavour.getMappingImpl().supportsConstantUnpicking() && additionalMappingPaths.containsKey(Mapping.KEY_UNPICK_CONSTANTS) && additionalMappingPaths.containsKey(Mapping.KEY_UNPICK_DEFINITIONS) && super.preconditionsShouldRun(pipelineCache, mcVersion, mappingFlavour, versionGraph, repo);
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		Path unpickedPath = getInternalArtifactPath(mcVersion, mappingFlavour);
		if (Files.exists(unpickedPath) && Files.size(unpickedPath) > 22 /* not empty jar */) {
			return StepResult.UP_TO_DATE;
		}
		if (Files.exists(unpickedPath)) {
			Files.delete(unpickedPath);
		}
		Path libraryRoot = pipelineCache.getForKey(Step.STEP_FETCH_LIBRARIES);
		Path remappedPath = pipelineCache.getForKey(Step.STEP_REMAP);
		// if remapping did not happen, nothing can be done here
		if (remappedPath == null) {
			MiscHelper.panic("A remapped JAR for version %s does not exist", mcVersion.launcherFriendlyVersionName());
		}
		Map<String, Path> additionalPaths = mappingFlavour.getMappingImpl().getAdditionalMappingInformation(mcVersion);
		ArrayList<String> params = new ArrayList<>(mcVersion.libraries().size() + 4);
		params.add(remappedPath.toAbsolutePath().toString());
		params.add(unpickedPath.toAbsolutePath().toString());
		params.add(additionalPaths.get(Mapping.KEY_UNPICK_DEFINITIONS).toAbsolutePath().toString());
		params.add(additionalPaths.get(Mapping.KEY_UNPICK_CONSTANTS).toAbsolutePath().toString());
		params.addAll(mcVersion.libraries().stream().map(artifact -> artifact.resolve(libraryRoot)).map(p -> p.toAbsolutePath().toString()).toList());
		daomephsta.unpick.cli.Main.main(params.toArray(new String[0]));
		return StepResult.SUCCESS;
	}
}
