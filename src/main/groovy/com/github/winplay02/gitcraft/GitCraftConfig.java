package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import groovy.lang.Tuple2;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
	public MappingFlavour usedMapping = MappingFlavour.MOJMAP;
	public MappingFlavour[] fallbackMappings = null;
	public boolean onlyStableReleases = false;
	public boolean onlySnapshots = false;
	public Path overrideRepositoryPath = null;

	/// Optional settings
	public String[] onlyVersion = null;
	public String minVersion = null;
	public String[] excludedVersion = null;

	/// Other Settings
	public String gitUser = "Mojang";
	public String gitMail = "gitcraft@decompiled.mc";
	public String gitMainlineLinearBranch = "master";

	/// Experimental Settings
	public boolean useHardlinks = true;

	/// Remote Settings
	public static final Map<String, String> URL_META = Map.of(
			// Launcher Meta
			"Mojang Minecraft Launcher Main Meta", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
			// Fabric Meta
			// This would be possible "Fabric Maven Meta (Experimental Versions)", "https://maven.fabricmc.net/net/minecraft/experimental_versions.json"
	);

	public static final List<Artifact> URL_EXTRA_META = List.of(
			// 1.14.3 - Combat Test
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/610f5c9874ba8926d5ae1bcce647e5f0e6e7c889/1_14_combat-212796.zip", "610f5c9874ba8926d5ae1bcce647e5f0e6e7c889"),
			// Combat Test 2
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/d164bb6ecc5fca9ac02878c85f11befae61ac1ca/1_14_combat-0.zip", "d164bb6ecc5fca9ac02878c85f11befae61ac1ca"),
			// Combat Test 3
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/0f209c9c84b81c7d4c88b4632155b9ae550beb89/1_14_combat-3.zip", "0f209c9c84b81c7d4c88b4632155b9ae550beb89"),
			// Combat Test 4
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60/1_15_combat-1.zip", "ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60"),
			// Combat Test 5
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/52263d42a626b40c947e523128f7a195ec5af76a/1_15_combat-6.zip", "52263d42a626b40c947e523128f7a195ec5af76a"),
			// Combat Test 6
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/5a8ceec8681ed96ab6ecb9607fb5d19c8a755559/1_16_combat-0.zip", "5a8ceec8681ed96ab6ecb9607fb5d19c8a755559"),
			// Combat Test 7 - From Minecraft Wiki
			Artifact.fromURL("https://archive.org/download/Combat_Test_7ab/1_16_combat-1.zip", "47bb5be6cb3ba215539ee97dfae66724c73c3dd5"),
			// Combat Test 7b - From Minecraft Wiki
			Artifact.fromURL("https://archive.org/download/Combat_Test_7ab/1_16_combat-2.zip", "43266ea8f2c20601d9fb264d5aa85df8052abc9e"),
			// Combat Test 7c
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/2557b99d95588505e988886220779087d7d6b1e9/1_16_combat-3.zip", "2557b99d95588505e988886220779087d7d6b1e9"),
			// Combat Test 8 - From Minecraft Wiki
			Artifact.fromURL("https://cdn.discordapp.com/attachments/369990015096455168/947864881028272198/1_16_combat-4.zip", "b4306b421183bd084b2831bd8d33a5db05ae9f9c"),
			// Combat Test 8b
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/9b2b984d635d373564b50803807225c75d7fd447/1_16_combat-5.zip", "9b2b984d635d373564b50803807225c75d7fd447"),
			// Combat Test 8c
			Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/ea08f7eb1f96cdc82464e27c0f95d23965083cfb/1_16_combat-6.zip", "ea08f7eb1f96cdc82464e27c0f95d23965083cfb"),
			// 1.18 Experimental Snapshot 1
			Artifact.fromURL("https://piston-data.mojang.com/v1/objects/231bba2a21e18b8c60976e1f6110c053b7b93226/1_18_experimental-snapshot-1.zip", "231bba2a21e18b8c60976e1f6110c053b7b93226"),
			// 1.18 Experimental Snapshot 2
			Artifact.fromURL("https://piston-data.mojang.com/v1/objects/0adfe4f321aa45248fc88ac888bed5556633e7fb/1_18_experimental-snapshot-2.zip", "0adfe4f321aa45248fc88ac888bed5556633e7fb"),
			// 1.18 Experimental Snapshot 3
			Artifact.fromURL("https://piston-data.mojang.com/v1/objects/846648ff9fe60310d584061261de43010e5c722b/1_18_experimental-snapshot-3.zip", "846648ff9fe60310d584061261de43010e5c722b"),
			// 1.18 Experimental Snapshot 4
			Artifact.fromURL("https://piston-data.mojang.com/v1/objects/b92a360cbae2eb896a62964ad8c06c3493b6c390/1_18_experimental-snapshot-4.zip", "b92a360cbae2eb896a62964ad8c06c3493b6c390"),
			// 1.18 Experimental Snapshot 5
			Artifact.fromURL("https://piston-data.mojang.com/v1/objects/d9cb7f6fb4e440862adfb40a385d83e3f8d154db/1_18_experimental-snapshot-5.zip", "d9cb7f6fb4e440862adfb40a385d83e3f8d154db"),
			// 1.18 Experimental Snapshot 6
			Artifact.fromURL("https://piston-data.mojang.com/v1/objects/4697c84c6a347d0b8766759d5b00bc5a00b1b858/1_18_experimental-snapshot-6.zip", "4697c84c6a347d0b8766759d5b00bc5a00b1b858"),
			// 1.18 Experimental Snapshot 7
			Artifact.fromURL("https://piston-data.mojang.com/v1/objects/ab4ecebb133f56dd4c4c4c3257f030a947ddea84/1_18_experimental-snapshot-7.zip", "ab4ecebb133f56dd4c4c4c3257f030a947ddea84"),
			// 1.19 Deep Dark Experimental Snapshot 1
			Artifact.fromURL("https://piston-data.mojang.com/v1/objects/b1e589c1d6ed73519797214bc796e53f5429ac46/1_19_deep_dark_experimental_snapshot-1.zip", "b1e589c1d6ed73519797214bc796e53f5429ac46"),
			// Before Reupload of 23w13a_or_b: 23w13a_or_b_original
			Artifact.fromURL("https://maven.fabricmc.net/net/minecraft/23w13a_or_b_original.json", "469f0d1416f2b25a8829d7991c11be3411813bf1")
	);

	public static final String URL_FABRIC_YARN_META = "https://meta.fabricmc.net/v2/versions/yarn";

	/// Mapping Settings
	public static final String MIN_SUPPORTED_FABRIC_LOADER = "0.14.23";

	public static final SemanticVersion INTERMEDIARY_MAPPINGS_START_VERSION, YARN_MAPPINGS_START_VERSION, YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION, PARCHMENT_START_VERSION;

	static {
		try {
			INTERMEDIARY_MAPPINGS_START_VERSION = SemanticVersion.parse("1.14-alpha.18.43.b");
			YARN_MAPPINGS_START_VERSION = SemanticVersion.parse("1.14-alpha.18.49.a");
			YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION = SemanticVersion.parse("1.14.3");
			PARCHMENT_START_VERSION = SemanticVersion.parse("1.16.5");
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

	// There are no releases for these parchment versions (yet)
	public static List<String> parchmentMissingVersions = List.of("1.18", "1.19", "1.19.1", "1.20", "1.20.2", "1.20.3");

	// Version Override
	public static Map<String, String> minecraftVersionSemVerOverride = Map.of(
			// support extra original for 23w13a_or_b
			"23w13a_or_b_original", "1.20-alpha.23.13.ab.original"
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
		String excludedBranches = onlyStableReleases ? " (only stable releases)" : (onlySnapshots ? " (only snapshots)" : "");
		String excludedVersions = isAnyVersionExcluded() && this.excludedVersion.length > 0 ? String.format(" (excluding: %s)", String.join(", ", this.excludedVersion)) : "";
		if (isOnlyVersion()) {
			MiscHelper.println("Versions to decompile: %s%s%s", String.join(", ", onlyVersion), excludedBranches, excludedVersions);
		} else if (isMinVersion()) {
			MiscHelper.println("Versions to decompile: Starting with %s%s%s", minVersion, excludedBranches, excludedVersions);
		} else {
			MiscHelper.println("Versions to decompile: all%s%s", excludedBranches, excludedVersions);
		}
		MiscHelper.println("Mappings used: %s", usedMapping);
		if (fallbackMappings != null && fallbackMappings.length > 0) {
			MiscHelper.println("Mappings used as fallback: %s", Arrays.stream(fallbackMappings).map(Object::toString).collect(Collectors.joining(", ")));
		}
		if (overrideRepositoryPath != null) {
			MiscHelper.println("Repository path is overridden. This may lead to various errors (see help). Proceed with caution. Target: %s", overrideRepositoryPath);
		}
	}

	public boolean isOnlyVersion() {
		return onlyVersion != null;
	}

	public boolean isMinVersion() {
		return minVersion != null;
	}

	public boolean isAnyVersionExcluded() {
		return excludedVersion != null;
	}

	public Optional<MappingFlavour> getMappingsForMinecraftVersion(OrderedVersion mcVersion) {
		if (this.usedMapping.getMappingImpl().doMappingsExist(mcVersion)) {
			return Optional.of(this.usedMapping);
		}
		if (this.fallbackMappings != null && this.fallbackMappings.length != 0) {
			for (MappingFlavour nextBestFallbackMapping : this.fallbackMappings) {
				if (nextBestFallbackMapping.getMappingImpl().doMappingsExist(mcVersion)) {
					MiscHelper.println("WARNING: %s mappings do not exist for %s. Falling back to %s", this.usedMapping, mcVersion.launcherFriendlyVersionName(), nextBestFallbackMapping);
					return Optional.of(nextBestFallbackMapping);
				}
			}
			MiscHelper.panic("ERROR: %s mappings do not exist for %s. All fallback options (%s) have been exhausted", this.usedMapping, mcVersion.launcherFriendlyVersionName(), Arrays.stream(this.fallbackMappings).map(Object::toString).collect(Collectors.joining(", ")));
		} else {
			MiscHelper.panic("ERROR: %s mappings do not exist for %s. No fallback options were specified", this.usedMapping, mcVersion.launcherFriendlyVersionName());
		}
		return Optional.empty();
	}
}
