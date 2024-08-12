package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.exceptions.ExceptionsPatch;
import com.github.winplay02.gitcraft.exceptions.NoneExceptions;
import com.github.winplay02.gitcraft.exceptions.ornithe.RavenExceptions;
import com.github.winplay02.gitcraft.mappings.IdentityMappings;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.mappings.MojangMappings;
import com.github.winplay02.gitcraft.mappings.ParchmentMappings;
import com.github.winplay02.gitcraft.mappings.yarn.FabricIntermediaryMappings;
import com.github.winplay02.gitcraft.mappings.yarn.YarnMappings;
import com.github.winplay02.gitcraft.nests.Nest;
import com.github.winplay02.gitcraft.nests.NoneNests;
import com.github.winplay02.gitcraft.nests.ornithe.OrnitheNests;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.signatures.NoneSignatures;
import com.github.winplay02.gitcraft.signatures.SignaturesPatch;
import com.github.winplay02.gitcraft.signatures.ornithe.SparrowSignatures;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.LazyValue;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.io.IOException;

public class GitCraft {
	public static final String VERSION = "0.2.0";

	/// Every Mapping
	public static final LazyValue<MojangMappings> MOJANG_MAPPINGS = LazyValue.of(MojangMappings::new);
	public static final LazyValue<FabricIntermediaryMappings> FABRIC_INTERMEDIARY_MAPPINGS = LazyValue.of(FabricIntermediaryMappings::new);
	public static final LazyValue<Mapping> YARN_MAPPINGS = LazyValue.of(() -> new YarnMappings(FABRIC_INTERMEDIARY_MAPPINGS.get()));
	public static final LazyValue<Mapping> MOJANG_PARCHMENT_MAPPINGS = LazyValue.of(() -> new ParchmentMappings(MOJANG_MAPPINGS.get()));
	public static final LazyValue<Mapping> IDENTITY_UNMAPPED = LazyValue.of(IdentityMappings::new);

	// Every Exception Patch
	public static final LazyValue<ExceptionsPatch> RAVEN_EXCEPTIONS = LazyValue.of(RavenExceptions::new);
	public static final LazyValue<ExceptionsPatch> NONE_EXCEPTIONS = LazyValue.of(NoneExceptions::new);

	// Every Signature Patch
	public static final LazyValue<SignaturesPatch> SPARROW_SIGNATURES = LazyValue.of(SparrowSignatures::new);
	public static final LazyValue<SignaturesPatch> NONE_SIGNATURES = LazyValue.of(NoneSignatures::new);

	// Every Nest
	public static final LazyValue<Nest> ORNITHE_NESTS = LazyValue.of(OrnitheNests::new);
	public static final LazyValue<Nest> NONE_NESTS = LazyValue.of(NoneNests::new);

	/// Other Information
	public static GitCraftConfig config = null;

	public static MinecraftVersionGraph versionGraph = null;
	public static MinecraftVersionGraph resetVersionGraph = null;

	public static void main(String[] args) throws Exception {
		GitCraft.config = GitCraftCli.handleCliArgs(args);
		GitCraftPaths.initializePaths(GitCraftPaths.lookupCurrentWorkingDirectory());
		if (GitCraft.config == null) {
			return;
		}
		MiscHelper.checkFabricLoaderVersion();
		MiscHelper.println("If generated semver is incorrect, it will break the order of the generated repo.\nConsider updating Fabric Loader. (run ./gradlew run --refresh-dependencies)");
		// Maven startup
		RemoteHelper.loadMavenCache();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				RemoteHelper.saveMavenCache();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, "Shutdown-Hook-Maven-Cache-Saver"));
		// Create Graph
		GitCraft.versionGraph = MinecraftVersionGraph.createFromMetadata(GitCraft.config.manifestSource.getMetadataProvider());
		RemoteHelper.saveMavenCache();
		MiscHelper.println("Decompiler log output is suppressed!");
		GitCraft.versionGraph = doVersionGraphOperations(GitCraft.versionGraph);
		GitCraft.resetVersionGraph = doVersionGraphOperationsForReset(GitCraft.versionGraph);
		try (RepoWrapper repo = GitCraft.getRepository()) {
			Pipeline.run(repo, versionGraph);
			if (repo != null) {
				MiscHelper.println("Repo can be found at: %s", repo.getRootPath().toString());
			}
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

	private static MinecraftVersionGraph doVersionGraphOperationsForReset(MinecraftVersionGraph graph) {
		if (config.isRefreshOnlyVersion()) {
			if (config.refreshOnlyVersion.length == 0) {
				MiscHelper.panic("No version to refresh provided");
			}
			OrderedVersion[] mc_versions = new OrderedVersion[config.refreshOnlyVersion.length];
			for (int i = 0; i < mc_versions.length; ++i) {
				mc_versions[i] = graph.getMinecraftVersionByName(config.refreshOnlyVersion[i]);
				if (mc_versions[i] == null) {
					MiscHelper.panic("%s is invalid", config.refreshOnlyVersion[i]);
				}
			}
			graph = graph.filterOnlyVersion(mc_versions);
		} else {
			if (config.isRefreshMinVersion()) {
				OrderedVersion mc_version = graph.getMinecraftVersionByName(config.refreshMinVersion);
				if (mc_version == null) {
					MiscHelper.panic("%s is invalid", config.refreshMinVersion);
				}
				graph = graph.filterMinVersion(mc_version);
			}
			if (config.isRefreshMaxVersion()) {
				OrderedVersion mc_version = graph.getMinecraftVersionByName(config.refreshMaxVersion);
				if (mc_version == null) {
					MiscHelper.panic("%s is invalid", config.refreshMaxVersion);
				}
				graph = graph.filterMaxVersion(mc_version);
			}
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
