package com.github.winplay02.gitcraft.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GitCraftPaths {
	public static Path CURRENT_WORKING_DIRECTORY = null;
	public static Path MAIN_ARTIFACT_STORE = null;
	public static Path DECOMPILED_WORKINGS = null;
	public static Path MAPPINGS = null;
	public static Path REPO = null;
	public static Path MC_VERSION_STORE = null;
	public static Path MC_VERSION_META_STORE = null;
	public static Path MC_VERSION_META_DOWNLOADS = null;
	public static Path LIBRARY_STORE = null;
	public static Path REMAPPED = null;
	public static Path ASSETS_INDEX = null;
	public static Path ASSETS_OBJECTS = null;
	public static Path SOURCE_EXTRA_VERSIONS = null;
	protected static Path LEGACY_METADATA_STORE = null;

	public static Path lookupCurrentWorkingDirectory() throws IOException {
		return Paths.get(new File(".").getCanonicalPath());
	}

	public static void initializePaths(Path currentWorkingDirectory) throws IOException {
		if (CURRENT_WORKING_DIRECTORY != null) {
			return;
		}
		CURRENT_WORKING_DIRECTORY = currentWorkingDirectory;
		MAIN_ARTIFACT_STORE = CURRENT_WORKING_DIRECTORY.resolve("artifact-store");
		DECOMPILED_WORKINGS = MAIN_ARTIFACT_STORE.resolve("decompiled");
		MAPPINGS = MAIN_ARTIFACT_STORE.resolve("mappings");
		REPO = CURRENT_WORKING_DIRECTORY.resolve("minecraft-repo");
		MC_VERSION_STORE = MAIN_ARTIFACT_STORE.resolve("mc-versions");
		MC_VERSION_META_STORE = MAIN_ARTIFACT_STORE.resolve("mc-meta");
		MC_VERSION_META_DOWNLOADS = MAIN_ARTIFACT_STORE.resolve("mc-meta-download");
		LIBRARY_STORE = MAIN_ARTIFACT_STORE.resolve("libraries");
		REMAPPED = MAIN_ARTIFACT_STORE.resolve("remapped-mc");
		ASSETS_INDEX = MAIN_ARTIFACT_STORE.resolve("assets-index");
		ASSETS_OBJECTS = MAIN_ARTIFACT_STORE.resolve("assets-objects");
		SOURCE_EXTRA_VERSIONS = CURRENT_WORKING_DIRECTORY.resolve("extra-versions");
		LEGACY_METADATA_STORE = MAIN_ARTIFACT_STORE.resolve("metadata.json");
		// Warning for breaking changes (the only breaking changes for now)
		if (Files.exists(LEGACY_METADATA_STORE)) {
			Files.delete(LEGACY_METADATA_STORE);
			MiscHelper.println("WARNING: There were breaking changes to existing repos");
			MiscHelper.println("- More known versions are automatically downloaded (like experimental snapshots)");
			MiscHelper.println("- Commits may now be merges of multiple previous versions");
			MiscHelper.println("- Datagen is executed by default (registry reports, NBT -> SNBT)");
			MiscHelper.println("- Comments are enabled for yarn generation");
			MiscHelper.println("More information in --help");
			MiscHelper.println("This warning will not show again");
			System.exit(0);
		}
		Files.createDirectories(MAIN_ARTIFACT_STORE);
		Files.createDirectories(DECOMPILED_WORKINGS);
		Files.createDirectories(MAPPINGS);
		Files.createDirectories(MC_VERSION_STORE);
		Files.createDirectories(MC_VERSION_META_STORE);
		Files.createDirectories(MC_VERSION_META_DOWNLOADS);
		Files.createDirectories(LIBRARY_STORE);
		Files.createDirectories(REMAPPED);
		Files.createDirectories(ASSETS_INDEX);
		Files.createDirectories(ASSETS_OBJECTS);
		Files.createDirectories(SOURCE_EXTRA_VERSIONS);
		MiscHelper.tryJavaExecution();
	}
}
