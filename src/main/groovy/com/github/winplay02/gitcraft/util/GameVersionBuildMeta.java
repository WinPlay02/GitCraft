package com.github.winplay02.gitcraft.util;

public record GameVersionBuildMeta(String gameVersion, String separator, int build, String maven, String version, boolean stable) implements VersionMeta, Comparable<GameVersionBuildMeta> {
	@Override
	public int compareTo(GameVersionBuildMeta o) {
		return Integer.compare(this.build, o.build);
	}

	@Override
	public String makeMavenUrl(String baseUrl) {
		return baseUrl + maven.substring(0, maven.indexOf(':')).replace('.', '/') + "/" + maven.substring(maven.indexOf(':') + 1, maven.lastIndexOf(':')) + "/" + maven.substring(maven.lastIndexOf(':') + 1) + "/" + maven.substring(maven.indexOf(':') + 1).replace(':', '-');
	}
}
