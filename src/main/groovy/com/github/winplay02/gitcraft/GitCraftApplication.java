package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.config.ApplicationConfiguration;
import com.github.winplay02.gitcraft.config.Configuration;
import com.github.winplay02.gitcraft.config.DataConfiguration;
import com.github.winplay02.gitcraft.config.RepositoryConfiguration;
import com.github.winplay02.gitcraft.config.TransientApplicationConfiguration;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FabricHelper;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public abstract class GitCraftApplication {
	public Logger applicationLogger = null;

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

	public abstract boolean initialize(String... args);

	public abstract void run() throws Exception;

	public final void mainEntrypoint(String[] args) throws Exception {
		Library.initialize();
		if (!this.initialize(args)) {
			return;
		}
		applicationLogger = Library.getSubLogger("GitCraft/Application");
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
			versionGraph = MinecraftVersionGraph.createFromMetadata(executor, getApplicationConfiguration().manifestSource().getMetadataProvider());
		}

		RemoteHelper.saveMavenCache();
		this.run();
	}

	protected static MinecraftVersionGraph doVersionGraphOperations(MinecraftVersionGraph graph) {
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
		graph = graph.filterMapping(getApplicationConfiguration().usedMapping(), getApplicationConfiguration().fallbackMappings());
		graph = graph.filterUnpick(getApplicationConfiguration().usedUnpickFlavour(), getApplicationConfiguration().fallbackUnpickFlavours());
		return graph;
	}

	protected static MinecraftVersionGraph doVersionGraphOperationsForReset(MinecraftVersionGraph graph) {
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
			String identifier = versionGraph.repoTagsIdentifier(
				getApplicationConfiguration().usedMapping(),
				getApplicationConfiguration().fallbackMappings(),
				getApplicationConfiguration().patchLvt(),
				getApplicationConfiguration().usedSignatures(),
				getApplicationConfiguration().usedNests(),
				getApplicationConfiguration().usedExceptions(),
				getApplicationConfiguration().enablePreening()
			);
			return new RepoWrapper(
				Objects.requireNonNullElse(
					getTransientApplicationConfiguration().overrideRepositoryPath() != null ?
						getTransientApplicationConfiguration().overrideRepositoryPath() :
						(identifier.isEmpty() ? null : LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("minecraft-repo-%s", identifier))),
					GitCraftPipelineFilesystemRoot.getDefaultRepository().apply(GitCraftPaths.FILESYSTEM_ROOT)),
				GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()
			);
		}
		return null;
	}
}
