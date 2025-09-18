package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.GitCraftApplication;
import com.github.winplay02.gitcraft.config.ApplicationConfiguration;
import com.github.winplay02.gitcraft.config.Configuration;
import com.github.winplay02.gitcraft.config.DataConfiguration;
import com.github.winplay02.gitcraft.config.RepositoryConfiguration;
import com.github.winplay02.gitcraft.config.TransientApplicationConfiguration;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineDescription;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
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
		if (!GitCraftLauncherCli.handleCliArgs(args)) {
			return false;
		}
		return true;
	}

	@Override
	public void run() throws Exception {
		versionGraph = doVersionGraphOperations(versionGraph);
		IPipeline.run(GitCraftPipelineDescription.LAUNCH_PIPELINE, GitCraftPipelineFilesystemStorage.DEFAULT.get(), null, versionGraph);
	}
}
