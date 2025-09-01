package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.config.ApplicationConfiguration;
import com.github.winplay02.gitcraft.config.Configuration;
import com.github.winplay02.gitcraft.config.DataConfiguration;
import com.github.winplay02.gitcraft.config.RepositoryConfiguration;
import com.github.winplay02.gitcraft.config.TransientApplicationConfiguration;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineDescription;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.github.winplay02.gitcraft.util.SerializationTypes;

public class GitCraft extends GitCraftApplication {
	public static final String NAME = "GitCraft";
	public static final String VERSION = "0.3.0";

	public static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";
	public static final String ORNITHE_MAVEN = "https://maven.ornithemc.net/releases/";

	public static void main(String... args) throws Exception {
		new GitCraft().mainEntrypoint(args);
	}

	@Override
	public boolean initialize(String... args) {
		Configuration.register("gitcraft_repository", RepositoryConfiguration.class, RepositoryConfiguration::deserialize);
		Configuration.register("gitcraft_dataimport", DataConfiguration.class, DataConfiguration::deserialize);
		Configuration.register("gitcraft_application", ApplicationConfiguration.class, ApplicationConfiguration::deserialize);
		Configuration.register("gitcraft_application_transient", TransientApplicationConfiguration.class, TransientApplicationConfiguration::deserialize);
		SerializationHelper.registerTypeAdapter(VersionInfo.VersionArgumentWithRules.class, SerializationTypes.VersionArgumentWithRulesAdapter::new);
		if (!GitCraftCli.handleCliArgs(args)) {
			return false;
		}
		return true;
	}

	@Override
	public void run() throws Exception {
		MiscHelper.println("Decompiler log output is suppressed!");
		versionGraph = doVersionGraphOperations(versionGraph);
		resetVersionGraph = doVersionGraphOperationsForReset(versionGraph);
		try (RepoWrapper repo = getRepository()) {
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
}
