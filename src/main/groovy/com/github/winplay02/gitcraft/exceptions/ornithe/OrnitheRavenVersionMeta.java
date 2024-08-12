package com.github.winplay02.gitcraft.exceptions.ornithe;

public record OrnitheRavenVersionMeta(String gameVersion, String separator, int build, String maven, String version,
									  boolean stable) implements Comparable<OrnitheRavenVersionMeta> {
	@Override
	public int compareTo(OrnitheRavenVersionMeta o) {
		return Integer.compare(this.build, o.build);
	}

	public String makeMavenURL() {
		return String.format("https://maven.ornithemc.net/releases/net/ornithemc/raven/%s%s%s/raven-%s%s%s.jar", gameVersion(), separator(), build(), gameVersion(), separator(), build());
	}
}
