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

	public static void init(Path currentWorkingDirectory) throws IOException {
		if (CURRENT_WORKING_DIRECTORY != null) {
			return;
		}
		CURRENT_WORKING_DIRECTORY = currentWorkingDirectory;
		MAIN_ARTIFACT_STORE = CURRENT_WORKING_DIRECTORY.resolve("artifact-store");
		MAVEN_CACHE = MAIN_ARTIFACT_STORE.resolve("maven-cache.json");
		Files.createDirectories(MAIN_ARTIFACT_STORE);
	}
}
