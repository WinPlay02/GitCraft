package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import groovy.lang.Tuple2;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GitCraftConfig {

	/// Manifest Source option
	public ManifestSource manifestSource = ManifestSource.MOJANG;

	/// Additional Data import settings
	public boolean loadIntegratedDatapack = true;
	public boolean loadAssets = true;
	public boolean loadAssetsExtern = true;
	public boolean readableNbt = true;
	public boolean loadDatagenRegistry = true;
	public boolean sortJsonObjects = false;

	/// Internal options
	public boolean verifyChecksums = true;
	public boolean checksumRemoveInvalidFiles = true;
	public boolean printExistingFileChecksumMatching = false;
	public boolean printExistingFileChecksumMatchingSkipped = false;
	public boolean printNotRunSteps = false;
	public int failedFetchRetryInterval = 500;
	public int remappingThreads = Runtime.getRuntime().availableProcessors() - 3;
	public int decompilingThreads = Runtime.getRuntime().availableProcessors() - 3;
	public boolean useHardlinks = true;

	/// Repository settings
	public boolean noRepo = false;
	public Path overrideRepositoryPath = null;
	public String gitUser = "Mojang";
	public String gitMail = "gitcraft@decompiled.mc";
	public String gitMainlineLinearBranch = "master";
	public String gitOldServerLinearBranch = "classic-alpha-server";
	public boolean createVersionBranches = false;
    public boolean createStableVersionBranches = false;

	/// Refresh settings
	public boolean refreshDecompilation = false;
	public String[] refreshOnlyVersion = null;
	public String refreshMinVersion = null;
	public String refreshMaxVersion = null;

	/// Mapping settings
	public MappingFlavour usedMapping = MappingFlavour.MOJMAP;
	public MappingFlavour[] fallbackMappings = null;

	/// Version settings
	public boolean onlyStableReleases = false;
	public boolean onlySnapshots = false;
	public boolean skipNonLinear = false;
	public String[] onlyVersion = null;
	public String minVersion = null;
	public String maxVersion = null;
	public String[] excludedVersion = null;

	/// Mapping quirks
	public static final String MIN_SUPPORTED_FABRIC_LOADER = "0.15.11";

	public static final String FABRIC_INTERMEDIARY_MAPPINGS_START_VERSION_ID = "18w43b"; // 1.14 snapshot
	public static final String YARN_MAPPINGS_START_VERSION_ID = "18w49a"; // 1.14 snapshot
	public static final String YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION_ID = "1.14.3";
	public static final String PARCHMENT_START_VERSION_ID = "1.16.5";

	// use release time to get around 1.3 having different ids in different manifest sources
	public static final ZonedDateTime FIRST_MERGEABLE_VERSION_RELEASE_TIME = ZonedDateTime.parse("2012-07-25T22:00:00+00:00");

	public static List<String> intermediaryMissingVersions = List.of("1.16_combat-1", "1.16_combat-2", "1.16_combat-4", "1.16_combat-5", "1.16_combat-6");

	public static List<String> yarnBrokenVersions = List.of("19w13a", "19w13b", "19w14a", "19w14b");

	public static List<String> yarnMissingVersions = List.of("1.16_combat-1", "1.16_combat-2", "1.16_combat-4", "1.16_combat-5", "1.16_combat-6");

	public static List<String> yarnMissingReuploadedVersions = List.of("23w13a_or_b_original", "24w14potato_original");

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
	public static List<String> parchmentMissingVersions = List.of("1.18", "1.19", "1.19.1", "1.20", "1.20.5");

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
		if (refreshDecompilation && !isRefreshOnlyVersion() && !isRefreshMinVersion() && !isRefreshMaxVersion()) {
			MiscHelper.println("All / specified version(s) will be %s", refreshDecompilation ? "deleted and decompiled again" : "reused if existing");
		} else {
			if (isRefreshOnlyVersion()) {
				MiscHelper.println("Versions to refresh artifacts: %s", String.join(", ", refreshOnlyVersion));
			} else if (isRefreshMinVersion() && isRefreshMaxVersion()) {
				MiscHelper.println("Versions to refresh artifacts: from %s to %s", refreshMinVersion, refreshMaxVersion);
			} else if (isRefreshMinVersion()) {
				MiscHelper.println("Versions to refresh artifacts: all from %s", refreshMinVersion);
			} else if (isRefreshMaxVersion()) {
				MiscHelper.println("Versions to refresh artifacts: all up to %s", refreshMaxVersion);
			}
		}
		String excludedBranches = onlyStableReleases ? " (only stable releases)" : (onlySnapshots ? " (only snapshots)" : "");
		String excludedVersions = isAnyVersionExcluded() && this.excludedVersion.length > 0 ? String.format(" (excluding: %s)", String.join(", ", this.excludedVersion)) : "";
		if (isOnlyVersion()) {
			MiscHelper.println("Versions to decompile: %s%s%s", String.join(", ", onlyVersion), excludedBranches, excludedVersions);
		} else if (isMinVersion() && isMaxVersion()) {
			MiscHelper.println("Versions to decompile: Starting with %s up to %s%s%s", minVersion, maxVersion, excludedBranches, excludedVersions);
		} else if (isMinVersion()) {
			MiscHelper.println("Versions to decompile: Starting with %s%s%s", minVersion, excludedBranches, excludedVersions);
		} else if (isMaxVersion()) {
			MiscHelper.println("Versions to decompile: Up to %s%s%s", maxVersion, excludedBranches, excludedVersions);
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
		if (createVersionBranches) {
			MiscHelper.println("A seperate branch will be created for each version.");
		}
        else if (createStableVersionBranches) {
            MiscHelper.println("A seperate branch will be created for each stable version.");
        }
		if (sortJsonObjects) {
			MiscHelper.println("JSON files (JSON objects) will be sorted in natural order.");
		}
	}

	public boolean isOnlyVersion() {
		return onlyVersion != null;
	}

	public boolean isMinVersion() {
		return minVersion != null;
	}

	public boolean isMaxVersion() {
		return maxVersion != null;
	}

	public boolean isAnyVersionExcluded() {
		return excludedVersion != null;
	}

	public boolean isRefreshOnlyVersion() {
		return refreshOnlyVersion != null;
	}

	public boolean isRefreshMinVersion() {
		return refreshMinVersion != null;
	}

	public boolean isRefreshMaxVersion() {
		return refreshMaxVersion != null;
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
