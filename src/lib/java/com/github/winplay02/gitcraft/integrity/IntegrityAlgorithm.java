package com.github.winplay02.gitcraft.integrity;

import com.github.winplay02.gitcraft.config.IntegrityConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * An algorithm checking the integrity of any provided data, by comparing a calculated hash sum to an expected value.
 * <p>
 * How this value is calculated is up to the implementing algorithm.
 * <p>
 * Calculated checksums may be cached to improve performance, if allowed by the {@link IntegrityConfiguration}.
 * Cached checksums are always checked to be up to date by comparing with the last-modified timestamp of the file.
 */
public abstract class IntegrityAlgorithm {

	/**
	 * Used configuration
	 */
	private final IntegrityConfiguration configuration;

	/**
	 * Whether checksums should be cached
	 */
	private boolean cacheHashes;

	/**
	 * An entry for the checksum cache.
	 *
	 * @param hashValue    Checksum
	 * @param lastModified Last-modified timestamp
	 */
	private record HashValue(String hashValue, FileTime lastModified) {
	}

	/**
	 * Checksum Cache
	 */
	private final ConcurrentHashMap<Path, HashValue> cachedHashes = new ConcurrentHashMap<>();

	/**
	 * Construct a new abstract integrity algorithms with a configuration.
	 *
	 * @param configuration Configuration
	 */
	protected IntegrityAlgorithm(IntegrityConfiguration configuration) {
		this.configuration = configuration;
		this.cacheHashes = configuration.cacheChecksums();
	}

	/**
	 * @return Name of the implementing algorithm
	 */
	public abstract String getAlgorithmName();

	/**
	 * Calculate a checksum in a generic way.
	 * This method is not intended to be called directly, instead it should be implemented by the algorithm.
	 *
	 * @param object                Any object identifying the resource to hash.
	 * @param objectBytesExtractor  Functional interface to extract bytes from the resource to feed into the hash algorithm. The inner consumer provides a block of data and an int specifying the amount of valid data and should forward the data into the hashing algorithm.
	 * @param objectLengthExtractor Functional interface to get the exact length of the resource, as this information may be needed by some algorithms. Maps a resource to a resource-size as long.
	 * @param <T>                   Type of resource
	 * @return Calculated checksum as a byte-array
	 */
	protected abstract <T> byte[] calculateChecksum(T object, BiConsumer<T, BiConsumer<byte[], Integer>> objectBytesExtractor, Function<T, Long> objectLengthExtractor);

	/**
	 * Calculate the checksum of a file, which is identified by the provided path.
	 *
	 * @param path Path of the file to hash
	 * @return Calculated checksum as a hexadecimal string
	 */
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

	/**
	 * Check whether the cached checksum is up to date, by comparing it with the provided last-modified timestamp.
	 *
	 * @param path         Path of the file
	 * @param lastModified Last-modified timestamp
	 * @return True if the cached entry contains the same last-modified timestamp, otherwise false
	 */
	protected boolean isCachedHashUpToDate(Path path, FileTime lastModified) {
		HashValue value = cachedHashes.get(path);
		if (value != null) {
			return value.lastModified().equals(lastModified);
		}
		return false;
	}

	/**
	 * Get the checksum of a file by either calculating the file or looking up the file in the cache, if caching is enabled by the configuration.
	 *
	 * @param path Path of the file to hash
	 * @return Checksum as a hexadecimal string
	 */
	public String getChecksumFile(Path path) {
		if (!configuration.verifyChecksums()) {
			return null;
		}
		FileTime lastModified;
		try {
			lastModified = Files.getLastModifiedTime(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (!cachedHashes.containsKey(path) || !cacheHashes || !isCachedHashUpToDate(path, lastModified)) {
			String hash = calculateChecksumFile(path);
			if (cacheHashes) {
				cachedHashes.put(path, new HashValue(hash, lastModified));
			}
		}
		return cachedHashes.get(path).hashValue();
	}

	/**
	 * Compare the checksum of a provided file by comparing it to an expected hash.
	 *
	 * @param path             Path of the file to check
	 * @param expectedChecksum Expected checksum
	 * @return True if the calculated checksum matches the expected checksum, otherwise false
	 */
	public final boolean fileMatchesChecksum(Path path, String expectedChecksum) {
		return getChecksumFile(path).equalsIgnoreCase(expectedChecksum);
	}

	/**
	 * Enable or disable the checksum cache.
	 *
	 * @param enabled True to enable the cache, false to disable the cache
	 */
	public void setCacheState(boolean enabled) {
		if (this.cacheHashes != enabled) {
			this.cacheHashes = enabled;
		}
		if (!this.cacheHashes) {
			this.flushCache();
		}
	}

	/**
	 * Invalidate a single entry in the checksum cache.
	 *
	 * @param path Path of entry to invalidate
	 */
	public void invalidateFile(Path path) {
		cachedHashes.remove(path);
	}

	/**
	 * Flush the entire checksum cache.
	 */
	public void flushCache() {
		cachedHashes.clear();
	}

	/// Integrity Checking Utility Functions

	/**
	 * Convert 4 bits into 1 hexadecimal character.
	 *
	 * @param b Number, only the 4 least significant bits are used.
	 * @return Hexadecimal character
	 */
	protected static String hexChr(int b) {
		return Integer.toHexString(b & 0xF);
	}

	/**
	 * Convert 1 byte into 2 hexadecimal characters.
	 *
	 * @param b Number, only the 8 least significant bits are used.
	 * @return Hexadecimal String
	 */
	protected static String toHex(int b) {
		return hexChr((b & 0xF0) >> 4) + hexChr(b & 0x0F);
	}

	/**
	 * Convert a byte-array into hexadecimal string.
	 *
	 * @param bytes Byte Array
	 * @return Hexadecimal String
	 */
	protected static String formatBytesHex(byte[] bytes) {
		StringBuilder hexsum = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			hexsum.append(toHex(b));
		}
		return hexsum.toString();
	}
}
