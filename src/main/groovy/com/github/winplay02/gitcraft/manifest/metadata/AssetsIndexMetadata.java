package com.github.winplay02.gitcraft.manifest.metadata;

import java.util.Map;

public record AssetsIndexMetadata(Map<String, Asset> objects, boolean map_to_resources) {
	public record Asset(String hash, int size) {
	}
}
