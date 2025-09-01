package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.GitCraftApplication;
import com.github.winplay02.gitcraft.config.ApplicationConfiguration;
import com.github.winplay02.gitcraft.config.Configuration;
import com.github.winplay02.gitcraft.config.DataConfiguration;
import com.github.winplay02.gitcraft.config.RepositoryConfiguration;
import com.github.winplay02.gitcraft.config.TransientApplicationConfiguration;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineDescription;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.github.winplay02.gitcraft.util.SerializationTypes;

public class GitCraftLauncher extends GitCraftApplication {

	public static void main(String... args) throws Exception {
		new GitCraftLauncher().mainEntrypoint(args);
	}

	public static LauncherConfig getLauncherConfig() {
		return Configuration.getConfiguration(LauncherConfig.class);
	}

	@Override
	public boolean initialize(String... args) {
		Configuration.register("gitcraft_repository", RepositoryConfiguration.class, RepositoryConfiguration::deserialize);
		Configuration.register("gitcraft_dataimport", DataConfiguration.class, DataConfiguration::deserialize);
		Configuration.register("gitcraft_application", ApplicationConfiguration.class, ApplicationConfiguration::deserialize);
		Configuration.register("gitcraft_application_transient", TransientApplicationConfiguration.class, TransientApplicationConfiguration::deserialize);
		Configuration.register("gitcraft_launcher", LauncherConfig.class, LauncherConfig::deserialize);
		SerializationHelper.registerTypeAdapter(VersionInfo.VersionArgumentWithRules.class, SerializationTypes.VersionArgumentWithRulesAdapter::new);
		return true;
	}

	@Override
	public void run() throws Exception {
		versionGraph = doVersionGraphOperations(versionGraph);
		OrderedVersion mc_version = versionGraph.getMinecraftVersionByName("1.1");
		versionGraph = versionGraph.filterOnlyVersion(mc_version);
		Pipeline.run(PipelineDescription.LAUNCH_PIPELINE, PipelineFilesystemStorage.DEFAULT.get(), null, versionGraph);
	}
}
