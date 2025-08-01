package com.github.winplay02.gitcraft.meta;

public interface VersionMeta<M extends VersionMeta<M>> extends Comparable<M> {

	default String makeJarMavenUrl(String mavenUrl) {
		return makeMavenUrl(mavenUrl, ".jar");
	}

	default String makeV2JarMavenUrl(String mavenUrl) {
		return makeMavenUrl(mavenUrl, "-v2.jar");
	}

	default String makeMergedV2JarMavenUrl(String mavenUrl) {
		return makeMavenUrl(mavenUrl, "-mergedv2.jar");
	}

	default String makeConstantsJarMavenUrl(String mavenUrl) {
		return makeMavenUrl(mavenUrl, "-constants.jar");
	}

	String makeMavenUrl(String baseUrl, String ext);

}
