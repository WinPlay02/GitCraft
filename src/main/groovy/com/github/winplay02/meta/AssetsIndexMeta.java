package com.github.winplay02.meta;

import java.util.HashMap;

public record AssetsIndexMeta(HashMap<String, AssetsIndexEntry> objects) {
	public record AssetsIndexEntry(String hash, int size) {

	}
}
