package com.github.winplay02.gitcraft.types;

import com.github.winplay02.gitcraft.meta.AssetsIndexMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AssetsIndex(AssetsIndexMetadata assetsIndex, List<Artifact> assets) {
	public static AssetsIndex from(AssetsIndexMetadata assetsIndex) {
		List<Artifact> assets = new ArrayList<>(assetsIndex.objects().size());
		for (AssetsIndexMetadata.Object info : assetsIndex.objects().values()) {
			assets.add(new Artifact(makeMinecraftAssetUrl(info.hash()), info.hash(), info.hash()));
		}
		return new AssetsIndex(assetsIndex, Collections.unmodifiableList(assets));
	}

	private static String makeMinecraftAssetUrl(String hash) {
		return String.format("https://resources.download.minecraft.net/%s/%s", hash.substring(0, 2), hash);
	}
}
