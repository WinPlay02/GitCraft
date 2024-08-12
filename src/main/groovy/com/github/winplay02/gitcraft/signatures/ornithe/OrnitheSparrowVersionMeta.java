package com.github.winplay02.gitcraft.signatures.ornithe;

public record OrnitheSparrowVersionMeta(String gameVersion, String separator, int build, String maven, String version,
										boolean stable) implements Comparable<OrnitheSparrowVersionMeta> {
	@Override
	public int compareTo(OrnitheSparrowVersionMeta o) {
		return Integer.compare(this.build, o.build);
	}

	public String makeMavenURL() {
		return String.format("https://maven.ornithemc.net/releases/net/ornithemc/sparrow/%s%s%s/sparrow-%s%s%s.jar", gameVersion(), separator(), build(), gameVersion(), separator(), build());
	}
}
