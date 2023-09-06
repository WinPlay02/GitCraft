package com.github.winplay02;

import groovy.lang.Tuple2;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

import java.util.List;
import java.util.Map;

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
	public int remappingThreads = Runtime.getRuntime().availableProcessors() - 3;
	public int decompilingThreads = Runtime.getRuntime().availableProcessors() - 3;
	public MappingHelper.MappingFlavour usedMapping = MappingHelper.MappingFlavour.MOJMAP;

	/// Optional settings
	public String[] onlyVersion = null;
	public String minVersion = null;

	/// Other Settings
	public String gitUser = "Mojang";
	public String gitMail = "gitcraft@decompiled.mc";
	public String gitMainlineLinearBranch = "master";
	public boolean loomFixRecords = true;

	/// Experimental Settings
	public boolean useHardlinks = true;

	/// Mapping Settings

	public static final SemanticVersion INTERMEDIARY_MAPPINGS_START_VERSION, YARN_MAPPINGS_START_VERSION, YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION;

	static {
		try {
			INTERMEDIARY_MAPPINGS_START_VERSION = SemanticVersion.parse("1.14-alpha.18.43.b");
			YARN_MAPPINGS_START_VERSION = SemanticVersion.parse("1.14-alpha.18.49.a");
			YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION = SemanticVersion.parse("1.14.3");
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}

	public static List<String> intermediaryMissingVersions = List.of("1.16_combat-1", "1.16_combat-2", "1.16_combat-4", "1.16_combat-5", "1.16_combat-6");

	public static List<String> yarnBrokenVersions = List.of("19w13a", "19w13b", "19w14a", "19w14b");

	public static List<String> yarnMissingVersions = List.of("1.16_combat-1", "1.16_combat-2", "1.16_combat-4", "1.16_combat-5", "1.16_combat-6");

	public static List<String> yarnMissingReuploadedVersions = List.of("23w13a_or_b_original");

	// Maps (version, [broken] build) -> [working] build or -1
	public static Map<Tuple2<String, Integer>, Integer> yarnBrokenBuildOverride = Map.of(
			// FIXUP Version 19w04b Build 9 is broken
			Tuple2.tuple("19w04b", 9), 8, /* 19w04b.9 -> Mapping source name conflicts detected: (3 instances) */
			// FIXUP Version 19w08a Build 6-9 is broken
			Tuple2.tuple("19w08a", 9), 5, /* 19w08a -> Mapping target name conflicts detected: (1 instance) */
			// FIXUP Version 19w12b Build 7-12 is broken
			Tuple2.tuple("19w12b", 12), 6, /* 19w12b -> Mapping target name conflicts detected: (1 instance); Mapping source name conflicts detected: (7 instances) */
			// FIXUP Version 19w13a Build 1-10 is broken
			Tuple2.tuple("19w13a", 10), -1, /* 19w13a -> Mapping target name conflicts detected: (1 instance) */ // No working build found
			// FIXUP Version 19w13b Build 1-12 is broken
			Tuple2.tuple("19w13b", 12), -1, /* 19w13b -> Mapping target name conflicts detected: (1 instance) */ // No working build found
			// FIXUP Version 19w14a Build 1-9 is broken
			Tuple2.tuple("19w14a", 9), -1, /* 19w14a -> Mapping target name conflicts detected: (1 instance) */ // No working build found
			// FIXUP Version 19w14b Build 1-9 is broken
			Tuple2.tuple("19w14b", 9), -1 /* 19w14b -> Mapping target name conflicts detected: (2 instances) */ // No working build found
	);

	public static Map<String, String> yarnInconsistentVersionNaming = Map.of(
			"1.15_combat-6", "1_15_combat-6", // 1.15_combat-6
			"1.16_combat-0", "1_16_combat-0" // 1.16_combat-0
	);

	public static GitCraftConfig defaultConfig() {
		return new GitCraftConfig();
	}

	public void printConfigInformation() {
		MiscHelper.println("Integrated datapack versioning is %s", loadIntegratedDatapack ? "enabled" : "disabled");
		MiscHelper.println("Asset versioning is %s", loadAssets ? "enabled" : "disabled");
		MiscHelper.println("External asset versioning is %s", loadAssetsExtern ? (loadAssets ? "enabled" : "implicitely disabled") : "disabled");
		MiscHelper.println("Checksum verification is %s", verifyChecksums ? "enabled" : "disabled");
		MiscHelper.println("Non-Linear version are %s", skipNonLinear ? "skipped" : "included");
		MiscHelper.println("Repository creation and versioning is %s", noRepo ? "skipped" : "enabled");
		MiscHelper.println("All / specified version(s) will be %s", refreshDecompilation ? "deleted and decompiled again" : "reused if existing");
		if (isOnlyVersion()) {
			MiscHelper.println("Versions to decompile: %s", String.join(", ", onlyVersion));
		} else if (isMinVersion()) {
			MiscHelper.println("Versions to decompile: Starting with %s", minVersion);
		} else {
			MiscHelper.println("Versions to decompile: all");
		}
		MiscHelper.println("Mappings used: %s", usedMapping);
	}

	public boolean isOnlyVersion() {
		return onlyVersion != null;
	}

	public boolean isMinVersion() {
		return minVersion != null;
	}
}
