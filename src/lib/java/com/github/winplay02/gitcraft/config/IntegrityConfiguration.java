package com.github.winplay02.gitcraft.config;

import com.github.winplay02.gitcraft.util.MiscHelper;
import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

import static com.github.winplay02.gitcraft.config.Configuration.Utils.prim;

/**
 * Configuration for integrity verifying algorithms.
 *
 * @param verifyChecksums Whether checksums should be verified. If this is false, no checksum is actually calculated.
 * @param cacheChecksums  Whether checksums should be cached. If this is false, no checksum is cached and every calculation will start from scratch. When dealing in a malicious environment, this should be disabled as last-modified timestamps can be forged.
 */
public record IntegrityConfiguration(boolean verifyChecksums,
									 boolean cacheChecksums)
	implements Configuration {

	@Override
	public Map<String, JsonElement> serialize() {
		return Map.of(
			"verifyChecksums", prim(this.verifyChecksums()),
			"cacheChecksums", prim(this.cacheChecksums())
		);
	}

	@Override
	public List<String> generateInfo() {
		return List.of(
			String.format("Checksum verification is: %s", verifyChecksums ? "enabled" : "disabled")
		);
	}

	public static IntegrityConfiguration deserialize(Map<String, JsonElement> map) {
		return new IntegrityConfiguration(
			Utils.getBoolean(map, "verifyChecksums", true),
			Utils.getBoolean(map, "cacheChecksums", true)
		);
	}
}
