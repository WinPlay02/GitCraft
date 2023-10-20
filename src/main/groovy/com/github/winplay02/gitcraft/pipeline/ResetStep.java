package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.nio.file.Files;
import java.nio.file.Path;

public class ResetStep extends Step {
	@Override
	public String getName() {
		return STEP_RESET;
	}

	@Override
	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		return GitCraft.config.refreshDecompilation;
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		Path remappedPath = GitCraft.STEP_REMAP.getInternalArtifactPath(mcVersion, mappingFlavour);
		Path decompiledPath = GitCraft.STEP_DECOMPILE.getInternalArtifactPath(mcVersion, mappingFlavour);
		if (!Files.exists(remappedPath) && !Files.exists(decompiledPath)) {
			return StepResult.UP_TO_DATE;
		}
		if (Files.exists(remappedPath)) {
			Files.delete(remappedPath);
			MiscHelper.println("%s (%s, %s, remapped) has been deleted", remappedPath, mcVersion.launcherFriendlyVersionName(), mappingFlavour);
		}
		if (Files.exists(decompiledPath)) {
			Files.delete(decompiledPath);
			MiscHelper.println("%s (%s, %s, decompiled) has been deleted", decompiledPath, mcVersion.launcherFriendlyVersionName(), mappingFlavour);
		}
		return StepResult.SUCCESS;
	}
}
