package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.LibraryPaths;
import com.github.winplay02.gitcraft.migration.MetadataStoreUpgrade;
import com.github.winplay02.gitcraft.migration.Transition0_1_0To0_2_0;
import com.github.winplay02.gitcraft.migration.Transition0_2_0_To0_3_0;
import com.github.winplay02.gitcraft.pipeline.IPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GitCraftPaths {
	public static IPipelineFilesystemRoot FILESYSTEM_ROOT = null;
	public static Path LEGACY_METADATA_STORE = null;
	protected static Path GITCRAFT_VERSION_INFO = null;
	public static Path LOST_AND_FOUND = null;

	protected static MetadataStoreUpgrade initialLegacyUpgrade = new Transition0_1_0To0_2_0();

	protected static Map<String, MetadataStoreUpgrade> upgrades = Map.of(
		"0.1.0", initialLegacyUpgrade,
		"0.2.0", new Transition0_2_0_To0_3_0()
	);

	protected static boolean isRecognizedVersion(String version) {
		return upgrades.containsKey(version) || GitCraft.VERSION.equals(version);
	}

	public static void initializePaths() throws IOException {
		if (FILESYSTEM_ROOT != null) {
			return;
		}
		FILESYSTEM_ROOT = new IPipelineFilesystemRoot.SimpleSuppliedPipelineFilesystemRoot(() -> LibraryPaths.MAIN_ARTIFACT_STORE);
		LEGACY_METADATA_STORE = LibraryPaths.MAIN_ARTIFACT_STORE.resolve("metadata.json");
		GITCRAFT_VERSION_INFO = LibraryPaths.MAIN_ARTIFACT_STORE.resolve("gitcraft-version.txt");
		LOST_AND_FOUND = LibraryPaths.MAIN_ARTIFACT_STORE.resolve("lost-and-found"); // only create, if really needed
		// Warning for breaking changes (the only breaking changes for now)
		upgradeExisting();
		GitCraftPipelineFilesystemRoot.initialize(FILESYSTEM_ROOT);
		MiscHelper.tryJavaExecution();
	}

	protected static void upgrade(final String existingVersion) throws IOException {
		String lastGitCraftVersion = existingVersion;
		List<String> updateMessages = new ArrayList<>();
		boolean hasUpgradedOnce = false;
		boolean isUpToDate;
		while (!(isUpToDate = lastGitCraftVersion.equalsIgnoreCase(GitCraft.VERSION))) {
			MetadataStoreUpgrade upgrade = upgrades.get(lastGitCraftVersion);
			if (upgrade == null) {
				// Skip to latest, there is not much we can do
				MiscHelper.println("WARNING: Existing version is '%s'. This version is unknown. Upgrading anyway...", lastGitCraftVersion);
				writeVersion();
				break;
			}
			if (!upgrade.sourceVersion().equalsIgnoreCase(lastGitCraftVersion)) {
				MiscHelper.panic("MetadataStoreUpgrade for version '%s' accepts version '%s' (integrity warning)", lastGitCraftVersion, upgrade.sourceVersion());
			}
			if (!isRecognizedVersion(upgrade.targetVersion())) {
				MiscHelper.panic("MetadataStoreUpgrade for version '%s' upgrades to unrecognized version '%s' (integrity warning)", lastGitCraftVersion, upgrade.targetVersion());
			}
			updateMessages.add(String.format("--- Upgrading local metadata store from version '%s' to '%s' ---", upgrade.sourceVersion(), upgrade.targetVersion()));
			upgrade.upgrade();
			updateMessages.addAll(upgrade.upgradeInfo());
			// Checkpoint
			writeVersion(upgrade.targetVersion());
			lastGitCraftVersion = upgrade.targetVersion();
			hasUpgradedOnce = true;
		}
		if (hasUpgradedOnce) {
			MiscHelper.println(String.join("\n", updateMessages));
			MiscHelper.println("This warning will not show again");
			System.exit(0);
		}
	}

	protected static void upgradeExisting() throws IOException {
		// Before gitcraft-version.txt exists; The latest commit this would work on, is cb1e7917b67d91a4319355a79d1eb9f9f75d9f78
		if (Files.exists(LEGACY_METADATA_STORE)) {
			upgrade("0.1.0");
			return;
		}
		// Initial store, nothing to do
		if (!Files.exists(GITCRAFT_VERSION_INFO)) {
			writeVersion();
			return;
		}
		String lastGitCraftVersion = readVersion();
		if (!lastGitCraftVersion.equalsIgnoreCase(GitCraft.VERSION)) {
			upgrade(lastGitCraftVersion);
		}
	}

	protected static String readVersion() throws IOException {
		return Files.readString(GITCRAFT_VERSION_INFO, StandardCharsets.UTF_8).trim();
	}

	protected static void writeVersion() throws IOException {
		writeVersion(GitCraft.VERSION);
	}

	protected static void writeVersion(String targetVersion) throws IOException {
		Files.writeString(GITCRAFT_VERSION_INFO, targetVersion, StandardCharsets.UTF_8,
			StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DSYNC);
	}
}
