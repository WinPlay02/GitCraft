package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.config.ApplicationConfiguration;
import com.github.winplay02.gitcraft.config.Configuration;
import com.github.winplay02.gitcraft.config.DataConfiguration;
import com.github.winplay02.gitcraft.config.RepositoryConfiguration;
import com.github.winplay02.gitcraft.config.TransientApplicationConfiguration;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.github.winplay02.gitcraft.util.SerializationTypes;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GitCraftTestingFs implements BeforeAllCallback, AutoCloseable {

	protected static Path temporaryTestingFsPath = null;
	//protected static FileSystemUtil.Delegate temporaryTestingFs;

	@Override
	public void beforeAll(ExtensionContext extensionContext) throws Exception {
		if (temporaryTestingFsPath == null) {
			temporaryTestingFsPath = Files.createTempDirectory("gitcraft");
			LibraryPaths.init(temporaryTestingFsPath);
			Library.initialize();
			Configuration.register("gitcraft_repository", RepositoryConfiguration.class, RepositoryConfiguration::deserialize);
			Configuration.register("gitcraft_dataimport", DataConfiguration.class, DataConfiguration::deserialize);
			Configuration.register("gitcraft_application", ApplicationConfiguration.class, ApplicationConfiguration::deserialize);
			Configuration.register("gitcraft_application_transient", TransientApplicationConfiguration.class, TransientApplicationConfiguration::deserialize);
			SerializationHelper.registerTypeAdapter(VersionInfo.VersionArgumentWithRules.class, SerializationTypes.VersionArgumentWithRulesAdapter::new);
			Library.applyConfiguration();

			//temporaryTestingFs = FileSystemUtil.getJarFileSystem(temporaryTestingFsPath.resolve("testingfs.jar"), true);
			//GitCraftPaths.initializePaths(temporaryTestingFs.getPath("."));
			//GitCraftPaths.initializePaths(temporaryTestingFsPath);
			GitCraftPaths.initializePaths();
			extensionContext.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).put("GitCraftTestingFs", this);
		}
	}

	@Override
	public void close() throws IOException {
		if (temporaryTestingFsPath != null) {
			// temporaryTestingFs.close();
			// Files.delete(temporaryTestingFsPath.resolve("testingfs.jar"));
			// MiscHelper.deleteDirectory(temporaryTestingFsPath);
			temporaryTestingFsPath = null;
		}
	}
}
