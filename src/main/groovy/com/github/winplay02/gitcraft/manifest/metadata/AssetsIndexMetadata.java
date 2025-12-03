package com.github.winplay02.gitcraft.manifest.metadata;

import com.github.winplay02.gitcraft.types.AssetsIndex;

import java.util.Map;

public record AssetsIndexMetadata(Map<String, Asset> objects, boolean map_to_resources) {
	public record Asset(String hash, int size, String url) {
		public Asset(String hash, int size) {
			this(hash, size, null);
		}

		public Asset(String hash, int size, String url) {
			this.hash = hash;
			this.size = size;
			this.url = (url != null) ? url : AssetsIndex.makeMinecraftAssetUrl(hash);
		}
	}
}
