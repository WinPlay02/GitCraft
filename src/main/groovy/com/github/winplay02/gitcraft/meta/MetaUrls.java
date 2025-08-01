package com.github.winplay02.gitcraft.meta;

public class MetaUrls {

	private static final String FABRIC_META_URL = "https://meta.fabricmc.net/";
	private static final String ORNITHE_META_URL = "https://meta.ornithemc.net/";

	public static final String FABRIC_YARN = fabricV2("yarn");

	public static final String ORNITHE_RAVEN = ornitheV3("raven");
	public static final String ORNITHE_SPARROW = ornitheV3("sparrow");
	public static final String ORNITHE_NESTS = ornitheV3("nests");

	public static String ornitheCalamusIntermediary(int generation) {
		return ornitheV3(generation, "intermediary");
	}

	public static String ornitheFeather(int generation) {
		return ornitheV3(generation, "feather");
	}

	public static String fabricV2(String endpoint) {
		return FABRIC_META_URL + "v2/versions/" + endpoint;
	}

	public static String ornitheV3(String endpoint) {
		return ORNITHE_META_URL + "v3/versions/" + endpoint;
	}

	public static String ornitheV3(int generation, String endpoint) {
		return ORNITHE_META_URL + "v3/versions/gen" + generation + "/" + endpoint;
	}
}
