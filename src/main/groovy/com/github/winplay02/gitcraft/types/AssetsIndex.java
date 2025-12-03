package com.github.winplay02.gitcraft.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.winplay02.gitcraft.manifest.metadata.AssetsIndexMetadata;

public record AssetsIndex(AssetsIndexMetadata assetsIndex, List<Artifact> assets) {
	public static AssetsIndex from(AssetsIndexMetadata assetsIndex) {
		List<Artifact> assets = new ArrayList<>(assetsIndex.objects().size());
		for (AssetsIndexMetadata.Asset info : assetsIndex.objects().values()) {
			assets.add(new Artifact(info.url(), info.hash(), info.hash()));
		}
		return new AssetsIndex(assetsIndex, Collections.unmodifiableList(assets));
	}

	public static String makeMinecraftAssetUrl(String hash) {
		return String.format("https://resources.download.minecraft.net/%s/%s", hash.substring(0, 2), hash);
	}
}
