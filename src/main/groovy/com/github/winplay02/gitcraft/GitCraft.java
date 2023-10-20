package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.mappings.FabricIntermediaryMappings;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.mappings.MojangMappings;
import com.github.winplay02.gitcraft.mappings.ParchmentMappings;
import com.github.winplay02.gitcraft.mappings.YarnMappings;
import com.github.winplay02.gitcraft.pipeline.CommitStep;
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
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;

public class GitCraft {

	public static final Path CURRENT_WORKING_DIRECTORY;

	static {
		try {
			CURRENT_WORKING_DIRECTORY = Paths.get(new File(".").getCanonicalPath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static final Path MAIN_ARTIFACT_STORE = CURRENT_WORKING_DIRECTORY.resolve("artifact-store");
	public static final Path DECOMPILED_WORKINGS = MAIN_ARTIFACT_STORE.resolve("decompiled");
	public static final Path MAPPINGS = MAIN_ARTIFACT_STORE.resolve("mappings");
	public static final Path REPO = MAIN_ARTIFACT_STORE.getParent().resolve("minecraft-repo");
	public static final Path MC_VERSION_STORE = MAIN_ARTIFACT_STORE.resolve("mc-versions");
	public static final Path MC_VERSION_META_STORE = MAIN_ARTIFACT_STORE.resolve("mc-meta");
	public static final Path MC_VERSION_META_DOWNLOADS = MAIN_ARTIFACT_STORE.resolve("mc-meta-download");
	public static final Path LIBRARY_STORE = MAIN_ARTIFACT_STORE.resolve("libraries");
	public static final Path REMAPPED = MAIN_ARTIFACT_STORE.resolve("remapped-mc");
	public static final Path ASSETS_INDEX = MAIN_ARTIFACT_STORE.resolve("assets-index");
	public static final Path ASSETS_OBJECTS = MAIN_ARTIFACT_STORE.resolve("assets-objects");

	public static final Path SOURCE_EXTRA_VERSIONS = CURRENT_WORKING_DIRECTORY.resolve("extra-versions");
	/// Every Mapping
	public static final MojangMappings MOJANG_MAPPINGS = new MojangMappings();
	public static final FabricIntermediaryMappings FABRIC_INTERMEDIARY_MAPPINGS = new FabricIntermediaryMappings();
	public static final Mapping YARN_MAPPINGS = new YarnMappings(FABRIC_INTERMEDIARY_MAPPINGS);
	public static final Mapping MOJANG_PARCHMENT_MAPPINGS = new ParchmentMappings(MOJANG_MAPPINGS);

	/// Every Step
	public static final Step STEP_RESET = new ResetStep();
	public static final Step STEP_FETCH_ARTIFACTS = new FetchArtifactsStep();
	public static final Step STEP_FETCH_LIBRARIES = new FetchLibrariesStep();
	public static final Step STEP_FETCH_ASSETS = new FetchAssetsStep();
	public static final Step STEP_MERGE = new MergeStep();
	public static final Step STEP_PREPARE_MAPPINGS = new PrepareMappingsStep();
	public static final Step STEP_REMAP = new RemapStep();
	public static final Step STEP_DECOMPILE = new DecompileStep();
	public static final CommitStep STEP_COMMIT = new CommitStep();

	/// Default Pipeline
	public static final List<Step> DEFAULT_PIPELINE = List.of(STEP_RESET, STEP_FETCH_ARTIFACTS, STEP_FETCH_LIBRARIES, STEP_FETCH_ASSETS, STEP_MERGE, STEP_PREPARE_MAPPINGS, STEP_REMAP, STEP_DECOMPILE, STEP_COMMIT);

	public static GitCraftConfig config = null;
	public static MinecraftVersionGraph versionGraph = null;
	public static LinkedHashMap<String, OrderedVersion> mcMetadata;

	static {
		MAIN_ARTIFACT_STORE.toFile().mkdirs();
		DECOMPILED_WORKINGS.toFile().mkdirs();
		MAPPINGS.toFile().mkdirs();
		MC_VERSION_STORE.toFile().mkdirs();
		LIBRARY_STORE.toFile().mkdirs();
		REMAPPED.toFile().mkdirs();
		ASSETS_INDEX.toFile().mkdirs();
		ASSETS_OBJECTS.toFile().mkdirs();
		SOURCE_EXTRA_VERSIONS.toFile().mkdirs();
	}

	public static void main(String[] args) throws Exception {
		GitCraft.config = GitCraftCli.handleCliArgs(args);
		if (GitCraft.config == null) {
			return;
		}
		MiscHelper.checkFabricLoaderVersion();
		GitCraft.mcMetadata = MetadataBootstrap.initialize();
		MiscHelper.println("If generated semver is incorrect, it will break the order of the generated repo.\nConsider updating Fabric Loader. (run ./gradlew run --refresh-dependencies)");
		GitCraft.versionGraph = MinecraftVersionGraph.createFromMetadata(mcMetadata);
		MiscHelper.println("Decompiler log output is suppressed!");
		GitCraft.versionGraph = doVersionGraphOperations(GitCraft.versionGraph);
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
				mc_versions[i] = graph.getMinecraftMainlineVersionByName(config.onlyVersion[i]);
				if (mc_versions[i] == null) {
					MiscHelper.panic("%s is invalid", config.onlyVersion[i]);
				}
			}
			graph = graph.filterOnlyVersion(mc_versions);
		} else if (config.isMinVersion()) {
			OrderedVersion mc_version = graph.getMinecraftMainlineVersionByName(config.minVersion);
			if (mc_version == null) {
				MiscHelper.panic("%s is invalid", config.minVersion);
			}
			graph = graph.filterMinVersion(mc_version);
		}
		if (config.isAnyVersionExcluded()) {
			OrderedVersion[] mc_versions = new OrderedVersion[config.excludedVersion.length];
			for (int i = 0; i < mc_versions.length; ++i) {
				mc_versions[i] = graph.getMinecraftMainlineVersionByName(config.excludedVersion[i]);
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

	private static RepoWrapper getRepository() throws Exception {
		if (!GitCraft.config.noRepo) {
			String identifier = GitCraft.versionGraph.repoTagsIdentifier(GitCraft.config.usedMapping, GitCraft.config.fallbackMappings);
			return new RepoWrapper(GitCraft.config.overrideRepositoryPath != null ? GitCraft.config.overrideRepositoryPath : (identifier.isEmpty() ? null : MAIN_ARTIFACT_STORE.getParent().resolve(String.format("minecraft-repo-%s", identifier))));
		}
		return null;
	}
}
