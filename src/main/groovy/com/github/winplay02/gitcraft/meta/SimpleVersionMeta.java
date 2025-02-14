package com.github.winplay02.gitcraft.meta;

public record SimpleVersionMeta(String maven, String version, boolean stable) implements VersionMeta<SimpleVersionMeta> {
	@Override
	public int compareTo(SimpleVersionMeta o) {
		return 0;
	}

	@Override
	public String makeMavenUrl(String baseUrl, String ext) {
		return baseUrl + maven.substring(0, maven.indexOf(':')).replace('.', '/') + "/" + maven.substring(maven.indexOf(':') + 1, maven.lastIndexOf(':')) + "/" + maven.substring(maven.lastIndexOf(':') + 1) + "/" + maven.substring(maven.indexOf(':') + 1).replace(':', '-') + ext;
	}
}
