package com.github.winplay02.gitcraft.util;

public interface VersionMeta {

	default String makeMavenJarUrl(String mavenUrl) {
		return makeMavenUrl(mavenUrl) + ".jar";
	}

	default String makeMavenJarV2Url(String mavenUrl) {
		return makeMavenUrl(mavenUrl) + "-v2.jar";
	}

	default String makeMavenJarMergedV2Url(String mavenUrl) {
		return makeMavenUrl(mavenUrl) + "-mergedv2.jar";
	}

	String makeMavenUrl(String baseUrl);

}
