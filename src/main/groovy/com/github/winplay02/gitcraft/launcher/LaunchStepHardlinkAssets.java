package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.nio.file.Files;
import java.nio.file.Path;

public class LaunchStepHardlinkAssets extends Step {
	@Override
	public String getName() {
		return STEP_HARDLINK_ASSETS;
	}

	@Override
	public boolean ignoresMappings() {
		return true;
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		Path assetsPathObjects = GitCraftLauncher.assetsPath.resolve("objects");
		Path assetsPathIndexes = GitCraftLauncher.assetsPath.resolve("indexes");
		Files.createDirectories(assetsPathObjects);
		Files.createDirectories(assetsPathIndexes);
		// Create Link to assets index
		Path targetAssetsIndexNoExt = mcVersion.assetsIndex().resolve(assetsPathIndexes);
		Path targetAssetsIndex = targetAssetsIndexNoExt.getParent().resolve(targetAssetsIndexNoExt.getFileName() + ".json");
		if (!Files.exists(targetAssetsIndex)) {
			Files.createLink(targetAssetsIndex, mcVersion.assetsIndex().resolve(GitCraftPaths.ASSETS_INDEX));
		}
		// Create Link to assets
		AssetsIndex assetsIndex = pipelineCache.getAssetsIndex();
		for (Artifact assetObject : assetsIndex.assets()) {
			Path outerDirectory = assetsPathObjects.resolve(assetObject.name().substring(0, 2));
			Files.createDirectories(outerDirectory);
			if (!Files.exists(assetObject.resolve(outerDirectory))) {
				Files.createLink(assetObject.resolve(outerDirectory), assetObject.resolve(GitCraftPaths.ASSETS_OBJECTS));
			}
		}
		return StepResult.SUCCESS;
	}
}
