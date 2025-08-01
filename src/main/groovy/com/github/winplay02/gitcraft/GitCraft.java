package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.config.ApplicationConfiguration;
import com.github.winplay02.gitcraft.config.Configuration;
import com.github.winplay02.gitcraft.config.DataConfiguration;
import com.github.winplay02.gitcraft.config.RepositoryConfiguration;
import com.github.winplay02.gitcraft.config.TransientApplicationConfiguration;
import com.github.winplay02.gitcraft.mappings.IdentityMappings;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.mappings.MojangMappings;
import com.github.winplay02.gitcraft.mappings.MojangPlusYarnMappings;
import com.github.winplay02.gitcraft.mappings.ParchmentMappings;
import com.github.winplay02.gitcraft.mappings.yarn.FabricIntermediaryMappings;
import com.github.winplay02.gitcraft.mappings.yarn.YarnMappings;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineDescription;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FabricHelper;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.LazyValue;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitCraft {
	public static final String VERSION = "0.2.0";

	/// Every Mapping
	public static final LazyValue<MojangMappings> MOJANG_MAPPINGS = LazyValue.of(MojangMappings::new);
	public static final LazyValue<FabricIntermediaryMappings> FABRIC_INTERMEDIARY_MAPPINGS = LazyValue.of(FabricIntermediaryMappings::new);
	public static final LazyValue<YarnMappings> YARN_MAPPINGS = LazyValue.of(() -> new YarnMappings(FABRIC_INTERMEDIARY_MAPPINGS.get()));
	public static final LazyValue<Mapping> MOJANG_PARCHMENT_MAPPINGS = LazyValue.of(() -> new ParchmentMappings(MOJANG_MAPPINGS.get()));
	public static final LazyValue<Mapping> IDENTITY_UNMAPPED = LazyValue.of(IdentityMappings::new);
	public static final LazyValue<Mapping> MOJANG_YARN_MAPPINGS = LazyValue.of(() -> new MojangPlusYarnMappings(MOJANG_MAPPINGS.get(), YARN_MAPPINGS.get()));

	public static MinecraftVersionGraph versionGraph = null;
	public static MinecraftVersionGraph resetVersionGraph = null;

	public static RepositoryConfiguration getRepositoryConfiguration() {
		return Configuration.getConfiguration(RepositoryConfiguration.class);
	}

	public static DataConfiguration getDataConfiguration() {
		return Configuration.getConfiguration(DataConfiguration.class);
	}

	public static ApplicationConfiguration getApplicationConfiguration() {
		return Configuration.getConfiguration(ApplicationConfiguration.class);
	}

	public static TransientApplicationConfiguration getTransientApplicationConfiguration() {
		return Configuration.getConfiguration(TransientApplicationConfiguration.class);
	}

	public static void main(String[] args) throws Exception {
		Library.initialize();
		Configuration.register("gitcraft_repository", RepositoryConfiguration.class, RepositoryConfiguration::deserialize);
		Configuration.register("gitcraft_dataimport", DataConfiguration.class, DataConfiguration::deserialize);
		Configuration.register("gitcraft_application", ApplicationConfiguration.class, ApplicationConfiguration::deserialize);
		Configuration.register("gitcraft_application_transient", TransientApplicationConfiguration.class, TransientApplicationConfiguration::deserialize);
		if (!GitCraftCli.handleCliArgs(args)) {
			return;
		}
		Library.applyConfiguration();
		GitCraftPaths.initializePaths();
		FabricHelper.checkFabricLoaderVersion();
		MiscHelper.println("If generated semver is incorrect, it will break the order of the generated repo.\nConsider updating Fabric Loader. (run ./gradlew run --refresh-dependencies)");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				RemoteHelper.saveMavenCache();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, "Shutdown-Hook-Maven-Cache-Saver"));
		// Create Graph
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Manifest-Metadata-Input").factory())) {
			GitCraft.versionGraph = MinecraftVersionGraph.createFromMetadata(executor, getApplicationConfiguration().manifestSource().getMetadataProvider());
		}

		RemoteHelper.saveMavenCache();
		MiscHelper.println("Decompiler log output is suppressed!");
		GitCraft.versionGraph = doVersionGraphOperations(GitCraft.versionGraph);
		GitCraft.resetVersionGraph = doVersionGraphOperationsForReset(GitCraft.versionGraph);
		try (RepoWrapper repo = GitCraft.getRepository()) {
			if (getTransientApplicationConfiguration().refreshDecompilation()) {
				Pipeline.run(PipelineDescription.RESET_PIPELINE, PipelineFilesystemStorage.DEFAULT.get(), repo, versionGraph);
			}
			Pipeline.run(PipelineDescription.DEFAULT_PIPELINE, PipelineFilesystemStorage.DEFAULT.get(), repo, versionGraph);
			if (getRepositoryConfiguration().gcAfterRun()) {
				Pipeline.run(PipelineDescription.GC_PIPELINE, PipelineFilesystemStorage.DEFAULT.get(), repo, versionGraph);
			}
			if (repo != null) {
				MiscHelper.println("Repo can be found at: %s", repo.getRootPath().toString());
			}
		}
	}

	private static MinecraftVersionGraph doVersionGraphOperations(MinecraftVersionGraph graph) {
		graph = graph.filterMapping(GitCraft.getApplicationConfiguration().usedMapping(), GitCraft.getApplicationConfiguration().fallbackMappings());
		if (getApplicationConfiguration().isOnlyVersion()) {
			if (getApplicationConfiguration().onlyVersion().length == 0) {
				MiscHelper.panic("No version provided");
			}
			OrderedVersion[] mc_versions = new OrderedVersion[getApplicationConfiguration().onlyVersion().length];
			for (int i = 0; i < mc_versions.length; ++i) {
				mc_versions[i] = graph.getMinecraftVersionByName(getApplicationConfiguration().onlyVersion()[i]);
				if (mc_versions[i] == null) {
					MiscHelper.panic("%s is invalid", getApplicationConfiguration().onlyVersion()[i]);
				}
			}
			graph = graph.filterOnlyVersion(mc_versions);
		} else {
			if (getApplicationConfiguration().isMinVersion()) {
				OrderedVersion mc_version = graph.getMinecraftVersionByName(getApplicationConfiguration().minVersion());
				if (mc_version == null) {
					MiscHelper.panic("%s is invalid", getApplicationConfiguration().minVersion());
				}
				graph = graph.filterMinVersion(mc_version);
			}
			if (getApplicationConfiguration().isMaxVersion()) {
				OrderedVersion mc_version = graph.getMinecraftVersionByName(getApplicationConfiguration().maxVersion());
				if (mc_version == null) {
					MiscHelper.panic("%s is invalid", getApplicationConfiguration().maxVersion());
				}
				graph = graph.filterMaxVersion(mc_version);
			}
		}
		if (getApplicationConfiguration().isAnyVersionExcluded()) {
			OrderedVersion[] mc_versions = new OrderedVersion[getApplicationConfiguration().excludedVersion().length];
			for (int i = 0; i < mc_versions.length; ++i) {
				mc_versions[i] = graph.getMinecraftVersionByName(getApplicationConfiguration().excludedVersion()[i]);
				if (mc_versions[i] == null) {
					MiscHelper.panic("%s is invalid", getApplicationConfiguration().onlyVersion()[i]);
				}
			}
			graph = graph.filterExcludeVersion(mc_versions);
		}
		if (getApplicationConfiguration().onlyStableReleases()) {
			graph = graph.filterStableRelease();
		}
		if (getApplicationConfiguration().onlySnapshots()) {
			graph = graph.filterSnapshots();
		}
		if (getApplicationConfiguration().skipNonLinear()) {
			graph = graph.filterMainlineVersions();
		}
		return graph;
	}

	private static MinecraftVersionGraph doVersionGraphOperationsForReset(MinecraftVersionGraph graph) {
		if (getTransientApplicationConfiguration().isRefreshOnlyVersion()) {
			if (getTransientApplicationConfiguration().refreshOnlyVersion().length == 0) {
				MiscHelper.panic("No version to refresh provided");
			}
			OrderedVersion[] mc_versions = new OrderedVersion[getTransientApplicationConfiguration().refreshOnlyVersion().length];
			for (int i = 0; i < mc_versions.length; ++i) {
				mc_versions[i] = graph.getMinecraftVersionByName(getTransientApplicationConfiguration().refreshOnlyVersion()[i]);
				if (mc_versions[i] == null) {
					MiscHelper.panic("%s is invalid", getTransientApplicationConfiguration().refreshOnlyVersion()[i]);
				}
			}
			graph = graph.filterOnlyVersion(mc_versions);
		} else {
			if (getTransientApplicationConfiguration().isRefreshMinVersion()) {
				OrderedVersion mc_version = graph.getMinecraftVersionByName(getTransientApplicationConfiguration().refreshMinVersion());
				if (mc_version == null) {
					MiscHelper.panic("%s is invalid", getTransientApplicationConfiguration().refreshMinVersion());
				}
				graph = graph.filterMinVersion(mc_version);
			}
			if (getTransientApplicationConfiguration().isRefreshMaxVersion()) {
				OrderedVersion mc_version = graph.getMinecraftVersionByName(getTransientApplicationConfiguration().refreshMaxVersion());
				if (mc_version == null) {
					MiscHelper.panic("%s is invalid", getTransientApplicationConfiguration().refreshMaxVersion());
				}
				graph = graph.filterMaxVersion(mc_version);
			}
		}
		return graph;
	}

	public static RepoWrapper getRepository() throws Exception {
		if (!getTransientApplicationConfiguration().noRepo()) {
			String identifier = GitCraft.versionGraph.repoTagsIdentifier(getApplicationConfiguration().usedMapping(), getApplicationConfiguration().fallbackMappings());
			return new RepoWrapper(getTransientApplicationConfiguration().overrideRepositoryPath() != null ? getTransientApplicationConfiguration().overrideRepositoryPath() : (identifier.isEmpty() ? null : LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("minecraft-repo-%s", identifier))));
		}
		return null;
	}
}
