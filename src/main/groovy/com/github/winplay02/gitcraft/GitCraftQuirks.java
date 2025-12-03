package com.github.winplay02.gitcraft;

import groovy.lang.Tuple2;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class GitCraftQuirks {
	/// Mapping quirks
	public static final String MIN_SUPPORTED_FABRIC_LOADER = "0.17.0";

	public static final String FABRIC_INTERMEDIARY_MAPPINGS_START_VERSION_ID = "18w43b"; // 1.14 snapshot
	public static final String YARN_MAPPINGS_START_VERSION_ID = "18w49a"; // 1.14 snapshot
	public static final String YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION_ID = "1.14.3";
	public static final String PARCHMENT_START_VERSION_ID = "1.16.5";
	public static final String YARN_UNPICK_START_VERSION_ID = "21w11a"; // build 22; 1.17 snapshot
	public static final String YARN_UNPICK_NO_CONSTANTS_JAR_VERSION_ID = "25w32a"; // build 7; 1.21.9? snapshot

	public static final ZonedDateTime RELEASE_TIME_1_3 = ZonedDateTime.parse("2012-07-25T22:00:00+00:00");
	public static final ZonedDateTime RELEASE_TIME_B1_0 = ZonedDateTime.parse("2010-12-20T17:28:00+00:00");
	public static final ZonedDateTime RELEASE_TIME_A1_0_15 = ZonedDateTime.parse("2010-08-04T00:00:00+00:00");

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
	public static List<String> parchmentMissingVersions = List.of("1.18", "1.19", "1.19.1", "1.20", "1.20.5", "1.21.2");
}
