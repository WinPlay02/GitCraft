package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.manifest.ManifestProvider;
import com.github.winplay02.gitcraft.manifest.MinecraftLauncherManifest;
import com.github.winplay02.gitcraft.mappings.FabricIntermediaryMappings;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.mappings.MojangMappings;
import com.github.winplay02.gitcraft.mappings.ParchmentMappings;
import com.github.winplay02.gitcraft.mappings.YarnMappings;
import com.github.winplay02.gitcraft.pipeline.CommitStep;
import com.github.winplay02.gitcraft.pipeline.DatagenStep;
import com.github.winplay02.gitcraft.pipeline.DecompileStep;
import com.github.winplay02.gitcraft.pipeline.FetchArtifactsStep;
import com.github.winplay02.gitcraft.pipeline.FetchAssetsStep;
import com.github.winplay02.gitcraft.pipeline.FetchLibrariesStep;
import com.github.winplay02.gitcraft.pipeline.MergeStep;
import com.github.winplay02.gitcraft.pipeline.PrepareMappingsStep;
import com.github.winplay02.gitcraft.pipeline.RemapStep;
import com.github.winplay02.gitcraft.pipeline.ResetStep;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.util.ArrayList;
import java.util.List;

public class GitCraft {
	/// Every Mapping
	public static final MojangMappings MOJANG_MAPPINGS = new MojangMappings();
	public static final FabricIntermediaryMappings FABRIC_INTERMEDIARY_MAPPINGS = new FabricIntermediaryMappings();
	public static final Mapping YARN_MAPPINGS = new YarnMappings(FABRIC_INTERMEDIARY_MAPPINGS);
	public static final Mapping MOJANG_PARCHMENT_MAPPINGS = new ParchmentMappings(MOJANG_MAPPINGS);

	/// Every Step
	public static Step STEP_RESET = null;
	public static Step STEP_FETCH_ARTIFACTS = null;
	public static Step STEP_FETCH_LIBRARIES = null;
	public static Step STEP_FETCH_ASSETS = null;
	public static Step STEP_MERGE = null;
	public static DatagenStep STEP_DATAGEN = null;
	public static Step STEP_PREPARE_MAPPINGS = null;
	public static Step STEP_REMAP = null;
	public static Step STEP_DECOMPILE = null;
	public static CommitStep STEP_COMMIT = null;

	/// Default Pipeline
	public static List<Step> DEFAULT_PIPELINE = null;

	/// Other Information
	public static GitCraftConfig config = null;
	public static MinecraftVersionGraph versionGraph = null;
	public static ManifestProvider manifestProvider = null;

	public static void main(String[] args) throws Exception {
		GitCraft.config = GitCraftCli.handleCliArgs(args);
		GitCraftPaths.initializePaths(GitCraftPaths.lookupCurrentWorkingDirectory());
		if (GitCraft.config == null) {
			return;
		}
		MiscHelper.checkFabricLoaderVersion();
		GitCraft.manifestProvider = new MinecraftLauncherManifest();
		MiscHelper.println("If generated semver is incorrect, it will break the order of the generated repo.\nConsider updating Fabric Loader. (run ./gradlew run --refresh-dependencies)");
		GitCraft.versionGraph = MinecraftVersionGraph.createFromMetadata(GitCraft.manifestProvider);
		MiscHelper.println("Decompiler log output is suppressed!");
		GitCraft.versionGraph = doVersionGraphOperations(GitCraft.versionGraph);
		{
			DEFAULT_PIPELINE = new ArrayList<>();
			DEFAULT_PIPELINE.add(STEP_RESET = new ResetStep());
			DEFAULT_PIPELINE.add(STEP_FETCH_ARTIFACTS = new FetchArtifactsStep());
			DEFAULT_PIPELINE.add(STEP_FETCH_LIBRARIES = new FetchLibrariesStep());
			DEFAULT_PIPELINE.add(STEP_FETCH_ASSETS = new FetchAssetsStep());
			DEFAULT_PIPELINE.add(STEP_MERGE = new MergeStep());
			DEFAULT_PIPELINE.add(STEP_DATAGEN = new DatagenStep());
			DEFAULT_PIPELINE.add(STEP_PREPARE_MAPPINGS = new PrepareMappingsStep());
			DEFAULT_PIPELINE.add(STEP_REMAP = new RemapStep());
			DEFAULT_PIPELINE.add(STEP_DECOMPILE = new DecompileStep());
			DEFAULT_PIPELINE.add(STEP_COMMIT = new CommitStep());
		}
		try (RepoWrapper repo = GitCraft.getRepository()) {
			runMainPipeline(GitCraft.DEFAULT_PIPELINE, repo);
			if (repo != null) {
				MiscHelper.println("Repo can be found at: %s", repo.getRootPath().toString());
			}
		}
	}

	private static void runMainPipeline(List<Step> pipeline, RepoWrapper repo) {
		for (OrderedVersion mcv : versionGraph) {
			Step.executePipeline(pipeline, mcv, GitCraft.config.getMappingsForMinecraftVersion(mcv).orElseThrow(), versionGraph, repo);
		}
	}

	private static MinecraftVersionGraph doVersionGraphOperations(MinecraftVersionGraph graph) {
		graph = graph.filterMapping(GitCraft.config.usedMapping, GitCraft.config.fallbackMappings);
		if (config.isOnlyVersion()) {
			if (config.onlyVersion.length == 0) {
				MiscHelper.panic("No version provided");
			}
			OrderedVersion[] mc_versions = new OrderedVersion[config.onlyVersion.length];
			for (int i = 0; i < mc_versions.length; ++i) {
				mc_versions[i] = graph.getMinecraftVersionByName(config.onlyVersion[i]);
				if (mc_versions[i] == null) {
					MiscHelper.panic("%s is invalid", config.onlyVersion[i]);
				}
			}
			graph = graph.filterOnlyVersion(mc_versions);
		} else {
			if (config.isMinVersion()) {
				OrderedVersion mc_version = graph.getMinecraftVersionByName(config.minVersion);
				if (mc_version == null) {
					MiscHelper.panic("%s is invalid", config.minVersion);
				}
				graph = graph.filterMinVersion(mc_version);
			}
			if (config.isMaxVersion()) {
				OrderedVersion mc_version = graph.getMinecraftVersionByName(config.maxVersion);
				if (mc_version == null) {
					MiscHelper.panic("%s is invalid", config.maxVersion);
				}
				graph = graph.filterMaxVersion(mc_version);
			}
		}
		if (config.isAnyVersionExcluded()) {
			OrderedVersion[] mc_versions = new OrderedVersion[config.excludedVersion.length];
			for (int i = 0; i < mc_versions.length; ++i) {
				mc_versions[i] = graph.getMinecraftVersionByName(config.excludedVersion[i]);
				if (mc_versions[i] == null) {
					MiscHelper.panic("%s is invalid", config.onlyVersion[i]);
				}
			}
			graph = graph.filterExcludeVersion(mc_versions);
		}
		if (config.onlyStableReleases) {
			graph = graph.filterStableRelease();
		}
		if (config.onlySnapshots) {
			graph = graph.filterSnapshots();
		}
		if (config.skipNonLinear) {
			graph = graph.filterMainlineVersions();
		}
		return graph;
	}

	public static RepoWrapper getRepository() throws Exception {
		if (!GitCraft.config.noRepo) {
			String identifier = GitCraft.versionGraph.repoTagsIdentifier(GitCraft.config.usedMapping, GitCraft.config.fallbackMappings);
			return new RepoWrapper(GitCraft.config.overrideRepositoryPath != null ? GitCraft.config.overrideRepositoryPath : (identifier.isEmpty() ? null : GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("minecraft-repo-%s", identifier))));
		}
		return null;
	}
}
