package com.github.winplay02.meta;

import com.github.winplay02.RemoteHelper;

public record FabricYarnVersionMeta(String gameVersion, String separator, int build, String maven, String version,
									boolean stable) implements Comparable<FabricYarnVersionMeta> {
	@Override
	public int compareTo(FabricYarnVersionMeta o) {
		return Integer.compare(this.build, o.build);
	}

	@Override
	public int build() {
		if ((build == 9 && gameVersion().equals("19w04b"))) { // FIXUP Version 19w04b Build 9 is broken
			/* 19w04b.9 -> Mapping source name conflicts detected: (3 instances) */
			// MiscHelper.println("Yarn build %s for %s is known to be broken, using build %s", build, gameVersion, build - 1);
			return build - 1;
		}
		if ((build == 9 && gameVersion().equals("19w08a"))) { // FIXUP Version 19w08a Build 6-9 is broken
			/* 19w08a -> Mapping target name conflicts detected: (1 instance) */
			return 5;
		}
		if ((build == 12 && gameVersion().equals("19w12b"))) { // FIXUP Version 19w12b Build 7-12 is broken
			/* 19w12b -> Mapping target name conflicts detected: (1 instance); Mapping source name conflicts detected: (7 instances) */
			return 6;
		}
		if ((build == 10 && gameVersion().equals("19w13a"))) { // FIXUP Version 19w13a Build 1-10 is broken
			/* 19w13a -> Mapping target name conflicts detected: (1 instance) */
			return -1; // No working build found
		}
		if ((build == 12 && gameVersion().equals("19w13b"))) { // FIXUP Version 19w13b Build 1-12 is broken
			/* 19w13b -> Mapping target name conflicts detected: (1 instance) */
			return -1; // No working build found
		}
		if ((build == 9 && gameVersion().equals("19w14a"))) { // FIXUP Version 19w14a Build 1-9 is broken
			/* 19w14a -> Mapping target name conflicts detected: (1 instance) */
			return -1; // No working build found
		}
		if ((build == 9 && gameVersion().equals("19w14b"))) { // FIXUP Version 19w14b Build 1-9 is broken
			/* 19w14b -> Mapping target name conflicts detected: (2 instances) */
			return -1; // No working build found
		}
		return build;
	}

	//public static boolean shouldRemappingIgnoreUnfixableConflicts(McVersion mcVersion) { // FIXUP Version 19w08a is broken
	//	return (mcVersion.version.equals("19w08a")); /* 19w08a -> Mapping target name conflicts detected: (1 instance) */
	//}

	public String makeMavenURLMergedV2() {
		return RemoteHelper.urlencodedURL(String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s%s%s/yarn-%s%s%s-mergedv2.jar", gameVersion(), separator(), build(), gameVersion(), separator(), build()));
	}

	public String makeMavenURLUnmergedV2() {
		return RemoteHelper.urlencodedURL(String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s%s%s/yarn-%s%s%s-v2.jar", gameVersion(), separator(), build(), gameVersion(), separator(), build()));
	}

	public String makeMavenURLUnmergedV1() {
		return RemoteHelper.urlencodedURL(String.format("https://maven.fabricmc.net/net/fabricmc/yarn/%s%s%s/yarn-%s%s%s.jar", gameVersion(), separator(), build(), gameVersion(), separator(), build()));
	}
}
