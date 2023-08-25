package com.github.winplay02;

import java.util.Optional;

public class GitCraftConfig {
	public boolean loadIntegratedDatapack = true;
	public boolean loadAssets = true;
	public boolean loadAssetsExtern = true;
	public boolean verifyChecksums = true;
	public boolean checksumRemoveInvalidFiles = true;
	public boolean skipNonLinear = false;
	public boolean noRepo = false;
	public boolean printExistingFileChecksumMatching = false;
	public boolean printExistingFileChecksumMatchingSkipped = false;
	public boolean refreshDecompilation = false;
	public int failedFetchRetryInterval = 500;

	/// Optional settings
	public String[] onlyVersion = null;
	public String minVersion = null;

	/// Other Settings
	public String gitUser = "Mojang";

	public String gitMail = "gitcraft@decompiled.mc";

	public static GitCraftConfig defaultConfig() {
		return new GitCraftConfig();
	}

	public void printConfigInformation() {
		MiscHelper.println("Integrated datapack versioning is %s", loadIntegratedDatapack ? "enabled" : "disabled");
		MiscHelper.println("Asset versioning is %s", loadAssets ? "enabled" : "disabled");
		MiscHelper.println("External asset versioning is %s", loadAssetsExtern ? (loadAssets ? "enabled" : "implicitely disabled") : "disabled");
		MiscHelper.println("Checksum verification is %s", verifyChecksums ? "enabled" : "disabled");
		MiscHelper.println("Non-Linear version are %s", skipNonLinear || onlyVersion != null || minVersion != null ? "skipped" : "included");
		MiscHelper.println("Repository creation and versioning is %s", noRepo ? "skipped" : "enabled");
		MiscHelper.println("All / specified version(s) will be %s", refreshDecompilation ? "deleted and decompiled again" : "reused if existing");
		if (isOnlyVersion()) {
			MiscHelper.println("Versions to decompile: %s", String.join(", ", onlyVersion));
		} else if (isMinVersion()) {
			MiscHelper.println("Versions to decompile: Starting with %s", minVersion);
		} else {
			MiscHelper.println("Versions to decompile: all");
		}
	}

	public boolean isOnlyVersion() {
		return onlyVersion != null;
	}

	public boolean isMinVersion() {
		return minVersion != null;
	}
}
