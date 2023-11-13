package com.github.winplay02.gitcraft.meta;

import com.github.winplay02.gitcraft.GitCraftConfig;
import groovy.lang.Tuple2;

public record FabricYarnVersionMeta(String gameVersion, String separator, int build, String maven, String version,
									boolean stable) implements Comparable<FabricYarnVersionMeta> {
	@Override
	public int compareTo(FabricYarnVersionMeta o) {
		return Integer.compare(this.build, o.build);
	}

	public String makeMavenURLMergedV2() {
		int build = GitCraftConfig.yarnBrokenBuildOverride.getOrDefault(Tuple2.tuple(this.gameVersion(), this.build()), this.build());
		return String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s%s%s/yarn-%s%s%s-mergedv2.jar", gameVersion(), separator(), build, gameVersion(), separator(), build);
	}

	public String makeMavenURLUnmergedV2() {
		int build = GitCraftConfig.yarnBrokenBuildOverride.getOrDefault(Tuple2.tuple(this.gameVersion(), this.build()), this.build());
		return String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s%s%s/yarn-%s%s%s-v2.jar", gameVersion(), separator(), build, gameVersion(), separator(), build);
	}

	public String makeMavenURLUnmergedV1() {
		int build = GitCraftConfig.yarnBrokenBuildOverride.getOrDefault(Tuple2.tuple(this.gameVersion(), this.build()), this.build());
		return String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s%s%s/yarn-%s%s%s.jar", gameVersion(), separator(), build, gameVersion(), separator(), build);
	}

	public String makeMavenURLConstants() {
		int build = GitCraftConfig.yarnBrokenBuildOverride.getOrDefault(Tuple2.tuple(this.gameVersion(), this.build()), this.build());
		return String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s%s%s/yarn-%s%s%s-constants.jar", gameVersion(), separator(), build, gameVersion(), separator(), build);
	}
}
