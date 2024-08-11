package com.github.winplay02.gitcraft.nests.ornithe;

public record OrnitheNestsVersionMeta(String gameVersion, String separator, int build, String maven, String version,
									  boolean stable) implements Comparable<OrnitheNestsVersionMeta> {
	@Override
	public int compareTo(OrnitheNestsVersionMeta o) {
		return Integer.compare(this.build, o.build);
	}

	public String makeMavenURL() {
		return String.format("https://maven.ornithemc.net/releases/net/ornithemc/nests/%s%s%s/nests-%s%s%s.jar", gameVersion(), separator(), build(), gameVersion(), separator(), build());
	}
}
