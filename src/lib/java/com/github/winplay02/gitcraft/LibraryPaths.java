package com.github.winplay02.gitcraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LibraryPaths {
	public static Path lookupCurrentWorkingDirectory() throws IOException {
		return Paths.get(new File(".").getCanonicalPath());
	}

	public static Path CURRENT_WORKING_DIRECTORY = null;
	public static Path MAIN_ARTIFACT_STORE = null;
	public static Path MAVEN_CACHE = null;
	public static Path TMP_DIR = null;

	public static void init(Path currentWorkingDirectory) throws IOException {
		if (CURRENT_WORKING_DIRECTORY != null) {
			return;
		}
		CURRENT_WORKING_DIRECTORY = currentWorkingDirectory;
		MAIN_ARTIFACT_STORE = CURRENT_WORKING_DIRECTORY.resolve("artifact-store");
		MAVEN_CACHE = MAIN_ARTIFACT_STORE.resolve("maven-cache.json");
		TMP_DIR = CURRENT_WORKING_DIRECTORY.resolve("tmp");
		Files.createDirectories(MAIN_ARTIFACT_STORE);
		Files.createDirectories(TMP_DIR);
	}

	public record TmpFileGuard(Path filePath) implements AutoCloseable {
		@Override
		public void close() throws IOException {
			Files.deleteIfExists(filePath);
		}
	}

	public static TmpFileGuard getTmpFile(String prefix, String suffix) throws IOException {
		return new TmpFileGuard(TMP_DIR.resolve(prefix + "-" + System.nanoTime() + "-" + suffix));
	}
}
