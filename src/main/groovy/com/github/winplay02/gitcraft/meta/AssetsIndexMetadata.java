package com.github.winplay02.gitcraft.meta;

import java.util.Map;

public record AssetsIndexMetadata(Map<String, Object> objects) {
	public record Object(String hash, int size) {
	}
}
