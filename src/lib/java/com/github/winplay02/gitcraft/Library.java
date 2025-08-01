package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.config.Configuration;
import com.github.winplay02.gitcraft.config.GlobalConfiguration;
import com.github.winplay02.gitcraft.config.IntegrityConfiguration;
import com.github.winplay02.gitcraft.integrity.GitBlobSHA1Algorithm;
import com.github.winplay02.gitcraft.integrity.IntegrityAlgorithm;
import com.github.winplay02.gitcraft.integrity.SHA1Algorithm;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Library {
	public static GlobalConfiguration CONF_GLOBAL = null;
	public static IntegrityConfiguration CONF_INTEGRITY = null;

	public static IntegrityAlgorithm IA_SHA1 = null;
	public static IntegrityAlgorithm IA_GIT_BLOB_SHA1 = null;

	public static Logger LIBRARY_LOGGER = null;

	public static void initialize() {
		LIBRARY_LOGGER = Logger.getLogger("GitCraft/Library");
		LIBRARY_LOGGER.setLevel(Level.ALL);
		try {
			LibraryPaths.init(LibraryPaths.lookupCurrentWorkingDirectory());
			// Maven startup
			RemoteHelper.loadMavenCache();
		} catch (IOException e) {
			MiscHelper.panicBecause(e, "Could not initialize base library");
		}
		Configuration.register("global", GlobalConfiguration.class, GlobalConfiguration::deserialize);
		Configuration.register("integrity", IntegrityConfiguration.class, IntegrityConfiguration::deserialize);
	}

	public static void applyConfiguration() throws IOException {
		Configuration.loadConfiguration();
		CONF_GLOBAL = Configuration.getConfiguration(GlobalConfiguration.class);
		CONF_INTEGRITY = Configuration.getConfiguration(IntegrityConfiguration.class);
		IA_SHA1 = new SHA1Algorithm(CONF_INTEGRITY);
		IA_GIT_BLOB_SHA1 = new GitBlobSHA1Algorithm(CONF_INTEGRITY);
		System.setProperty("jdk.httpclient.connectionPoolSize", String.valueOf(CONF_GLOBAL.maxConcurrentHttpConnections()));
		System.setProperty("jdk.httpclient.maxstreams", String.valueOf(CONF_GLOBAL.maxConcurrentHttpStreams()));
		System.setProperty("jdk.httpclient.bufsize", String.valueOf(Short.MAX_VALUE * 2));
	}

	public static Logger getSubLogger(String name) {
		Logger logger = Logger.getLogger(name);
		logger.setParent(LIBRARY_LOGGER);
		return logger;
	}

	public static Logger getSubLogger(String name, Level logLevel) {
		Logger logger = Logger.getLogger(name);
		logger.setParent(LIBRARY_LOGGER);
		logger.setLevel(logLevel);
		return logger;
	}
}
