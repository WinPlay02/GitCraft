package com.github.winplay02.gitcraft.integrity;

import com.github.winplay02.gitcraft.GitCraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public abstract class IntegrityAlgorithm {

	private final LinkedHashMap<Path, String> cachedHashes = new LinkedHashMap<>();

	public abstract String getAlgorithmName();

	protected abstract <T> byte[] calculateChecksum(T object, BiConsumer<T, BiConsumer<byte[], Integer>> objectBytesExtractor, Function<T, Long> objectLengthExtractor);

	protected String calculateChecksumFile(Path path) {
		byte[] buffer = new byte[16384];
		try (InputStream fileInput = Files.newInputStream(path)) {
			long fileSize = Files.size(path);
			return formatBytesHex(calculateChecksum(path, (_object, byte_sink) -> {
				try {
					int len;
					while ((len = fileInput.read(buffer)) > 0) {
						byte_sink.accept(buffer, len);
					}
				} catch (IOException e) {
					throw new RuntimeException();
				}
			}, (_object) -> fileSize));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getChecksumFile(Path path) {
		if (!GitCraft.config.verifyChecksums) {
			return null;
		}
		if (!cachedHashes.containsKey(path)) {
			String hash = calculateChecksumFile(path);
			cachedHashes.put(path, hash);
		}
		return cachedHashes.get(path);
	}

	public final boolean fileMatchesChecksum(Path path, String expectedChecksum) {
		return getChecksumFile(path).equalsIgnoreCase(expectedChecksum);
	}

	public void invalidateFile(Path path) {
		cachedHashes.remove(path);
	}

	/// Integrity Checking Utility Functions
	protected static String hexChr(int b) {
		return Integer.toHexString(b & 0xF);
	}

	protected static String toHex(int b) {
		return hexChr((b & 0xF0) >> 4) + hexChr(b & 0x0F);
	}

	protected static String formatBytesHex(byte[] bytes) {
		StringBuilder hexsum = new StringBuilder();
		for (byte b : bytes) {
			hexsum.append(toHex(b));
		}
		return hexsum.toString();
	}
}
