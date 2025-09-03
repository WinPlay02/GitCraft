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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Library {
	public static GlobalConfiguration CONF_GLOBAL = null;
	public static IntegrityConfiguration CONF_INTEGRITY = null;

	public static IntegrityAlgorithm IA_SHA1 = null;
	public static IntegrityAlgorithm IA_GIT_BLOB_SHA1 = null;

	public static Logger LIBRARY_LOGGER = null;

	public static void initialize() {
		LIBRARY_LOGGER = Logger.getLogger("GitCraft/Library");
		LIBRARY_LOGGER.setLevel(Level.ALL);
		LIBRARY_LOGGER.setUseParentHandlers(false);
		{
			ConsoleHandler consoleHandler = new ConsoleHandler();
			consoleHandler.setFormatter(new SimpleFormatter() {
				protected String format = "[%1$tF %1$tT - %3$s / %4$s] %5$s %n";

				@Override
				public String format(LogRecord record) {
					ZonedDateTime zdt = ZonedDateTime.ofInstant(
						record.getInstant(), ZoneId.systemDefault());
					String source;
					if (record.getSourceClassName() != null) {
						source = record.getSourceClassName();
						if (record.getSourceMethodName() != null) {
							source += " " + record.getSourceMethodName();
						}
					} else {
						source = record.getLoggerName();
					}
					String message = formatMessage(record);
					String throwable = "";
					if (record.getThrown() != null) {
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						pw.println();
						record.getThrown().printStackTrace(pw);
						pw.close();
						throwable = sw.toString();
					}
					return String.format(format,
						zdt,
						source,
						record.getLoggerName(),
						record.getLevel().getLocalizedName(),
						message,
						throwable);
				}
			});
			LIBRARY_LOGGER.addHandler(consoleHandler);
		}
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
