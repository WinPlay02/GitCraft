package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.meta.AssetsIndexMeta;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import com.github.winplay02.gitcraft.util.SerializationHelper;

import java.nio.file.Path;
import java.util.List;

public class FetchAssetsStep extends Step {

	private final Path rootPathAssetsIndex, rootPathAssetObjects;

	public FetchAssetsStep(Path rootPathAssetsIndex, Path rootPathAssetObjects) {
		this.rootPathAssetsIndex = rootPathAssetsIndex;
		this.rootPathAssetObjects = rootPathAssetObjects;
	}

	public FetchAssetsStep() {
		this(GitCraftPaths.ASSETS_INDEX, GitCraftPaths.ASSETS_OBJECTS);
	}

	@Override
	public String getName() {
		return STEP_FETCH_ASSETS;
	}

	@Override
	public boolean ignoresMappings() {
		return true;
	}

	@Override
	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		return GitCraft.config.loadAssetsExtern && GitCraft.config.loadAssets && super.preconditionsShouldRun(pipelineCache, mcVersion, mappingFlavour, versionGraph, repo);
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		if (mcVersion.assetsIndex() == null) {
			return StepResult.NOT_RUN;
		}
		Path assetsIndexPath = mcVersion.assetsIndex().resolve(this.rootPathAssetsIndex);
		StepResult assetsIndexResult = mcVersion.assetsIndex().fetchArtifact(this.rootPathAssetsIndex, "assets index");
		AssetsIndex assetsIndex = AssetsIndex.from(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(assetsIndexPath), AssetsIndexMeta.class));
		pipelineCache.putAssetsIndex(assetsIndex);
		List<StepResult> results = new java.util.ArrayList<>(List.of(assetsIndexResult));
		for (Artifact assetObject : assetsIndex.assets()) {
			results.add(assetObject.fetchArtifact(this.rootPathAssetObjects, "asset"));
		}
		return StepResult.merge(results);
	}

	@Override
	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return rootPathAssetObjects;
	}
}
