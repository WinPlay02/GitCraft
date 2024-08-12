package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.GitCraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class GitCraftPaths {
	public static Path CURRENT_WORKING_DIRECTORY = null;
	public static Path MAIN_ARTIFACT_STORE = null;
	public static Path DECOMPILED_WORKINGS = null;
	public static Path EXCEPTIONS = null;
	public static Path MAPPINGS = null;
	public static Path NESTS = null;
	public static Path REPO = null;
	public static Path MC_VERSION_STORE = null;
	public static Path MC_VERSION_META_STORE = null;
	public static Path MC_VERSION_META_DOWNLOADS = null;
	public static Path LIBRARY_STORE = null;
	public static Path EXCEPTIONS_APPLIED = null;
	public static Path REMAPPED = null;
	public static Path UNPICKED = null;
	public static Path NESTS_APPLIED = null;
	public static Path ASSETS_INDEX = null;
	public static Path ASSETS_OBJECTS = null;
	public static Path SOURCE_EXTRA_VERSIONS = null;
	protected static Path LEGACY_METADATA_STORE = null;
	protected static Path GITCRAFT_VERSION_INFO = null;
	protected static Path MAVEN_CACHE = null;

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
		EXCEPTIONS = MAIN_ARTIFACT_STORE.resolve("exceptions");
		MAPPINGS = MAIN_ARTIFACT_STORE.resolve("mappings");
		NESTS = MAIN_ARTIFACT_STORE.resolve("nests");
		REPO = CURRENT_WORKING_DIRECTORY.resolve("minecraft-repo");
		MC_VERSION_STORE = MAIN_ARTIFACT_STORE.resolve("mc-versions");
		MC_VERSION_META_STORE = MAIN_ARTIFACT_STORE.resolve("mc-meta");
		MC_VERSION_META_DOWNLOADS = MAIN_ARTIFACT_STORE.resolve("mc-meta-download");
		LIBRARY_STORE = MAIN_ARTIFACT_STORE.resolve("libraries");
		EXCEPTIONS_APPLIED = MAIN_ARTIFACT_STORE.resolve("exceptions-applied-mc");
		REMAPPED = MAIN_ARTIFACT_STORE.resolve("remapped-mc");
		UNPICKED = MAIN_ARTIFACT_STORE.resolve("unpicked-mc");
		NESTS_APPLIED = MAIN_ARTIFACT_STORE.resolve("nests-applied-mc");
		ASSETS_INDEX = MAIN_ARTIFACT_STORE.resolve("assets-index");
		ASSETS_OBJECTS = MAIN_ARTIFACT_STORE.resolve("assets-objects");
		SOURCE_EXTRA_VERSIONS = CURRENT_WORKING_DIRECTORY.resolve("extra-versions");
		LEGACY_METADATA_STORE = MAIN_ARTIFACT_STORE.resolve("metadata.json");
		GITCRAFT_VERSION_INFO = MAIN_ARTIFACT_STORE.resolve("gitcraft-version.txt");
		MAVEN_CACHE = MAIN_ARTIFACT_STORE.resolve("maven-cache.json");
		// Warning for breaking changes (the only breaking changes for now)
		Files.createDirectories(MAIN_ARTIFACT_STORE);
		upgradeExisting();
		Files.createDirectories(DECOMPILED_WORKINGS);
		Files.createDirectories(EXCEPTIONS);
		Files.createDirectories(MAPPINGS);
		Files.createDirectories(NESTS);
		Files.createDirectories(MC_VERSION_STORE);
		Files.createDirectories(MC_VERSION_META_STORE);
		Files.createDirectories(MC_VERSION_META_DOWNLOADS);
		Files.createDirectories(LIBRARY_STORE);
		Files.createDirectories(EXCEPTIONS_APPLIED);
		Files.createDirectories(REMAPPED);
		Files.createDirectories(UNPICKED);
		Files.createDirectories(NESTS_APPLIED);
		Files.createDirectories(ASSETS_INDEX);
		Files.createDirectories(ASSETS_OBJECTS);
		Files.createDirectories(SOURCE_EXTRA_VERSIONS);
		MiscHelper.tryJavaExecution();
	}

	protected static void upgradeExisting() throws IOException {
		// Before gitcraft-version.txt exists; The latest commit this would work on, is cb1e7917b67d91a4319355a79d1eb9f9f75d9f78
		if (Files.exists(LEGACY_METADATA_STORE)) {
			Files.delete(LEGACY_METADATA_STORE);
			writeVersion();
			MiscHelper.println("WARNING: There were breaking changes to existing repos");
			MiscHelper.println("- More known versions are automatically downloaded (like experimental snapshots)");
			MiscHelper.println("- Commits may now be merges of multiple previous versions");
			MiscHelper.println("- Datagen is executed by default (registry reports, NBT -> SNBT)");
			MiscHelper.println("- Vanilla worldgen datapack is now downloaded for versions, where there are no other ways of obtaining these files");
			MiscHelper.println("- Comments are enabled for yarn generation");
			MiscHelper.println("- Constant unpicking is now done for yarn generation");
			MiscHelper.println("More information in --help");
			MiscHelper.println("This warning will not show again");
			System.exit(0);
		}
		if (!Files.exists(GITCRAFT_VERSION_INFO)) {
			writeVersion();
			return;
		}
		String lastGitCraftVersion = readVersion();
		if (!lastGitCraftVersion.equalsIgnoreCase(GitCraft.VERSION)) {
			MiscHelper.println("WARNING: Existing version is '%s'. This version is unknown. Upgrading anyway...", lastGitCraftVersion);
			writeVersion();
			System.exit(0);
		}
	}

	protected static String readVersion() throws IOException {
		return Files.readString(GITCRAFT_VERSION_INFO, StandardCharsets.UTF_8).trim();
	}

	protected static void writeVersion() throws IOException {
		Files.writeString(GITCRAFT_VERSION_INFO, GitCraft.VERSION, StandardCharsets.UTF_8,
			StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DSYNC);
	}
}
