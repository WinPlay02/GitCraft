package com.github.winplay02.gitcraft.meta;

import java.util.Map;

public record AssetsIndexMetadata(Map<String, Asset> objects) {
	public record Asset(String hash, int size) {
	}
}
