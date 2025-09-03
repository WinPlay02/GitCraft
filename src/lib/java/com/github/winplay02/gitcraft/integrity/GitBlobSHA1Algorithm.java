package com.github.winplay02.gitcraft.integrity;

import com.github.winplay02.gitcraft.config.IntegrityConfiguration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Integrity algorithm using SHA1 in the same way blobs in git are hashed.
 * This includes a {@code "blob "} prefix followed by the amount of bytes and terminated with {@code "\0"}.
 */
public class GitBlobSHA1Algorithm extends IntegrityAlgorithm {
	private static final byte[] BLOB_BYTES = new byte[]{(byte) 'b', (byte) 'l', (byte) 'o', (byte) 'b', (byte) ' '};

	/**
	 * Construct this algorithm with a configuration.
	 *
	 * @param configuration Configuration
	 */
	public GitBlobSHA1Algorithm(IntegrityConfiguration configuration) {
		super(configuration);
	}

	@Override
	public String getAlgorithmName() {
		return "Git-Blob-SHA1";
	}

	@Override
	protected <T> byte[] calculateChecksum(T object, BiConsumer<T, BiConsumer<byte[], Integer>> objectBytesExtractor, Function<T, Long> objectLengthExtractor) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA1");
			digest.update(BLOB_BYTES);
			digest.update(String.valueOf(objectLengthExtractor.apply(object)).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			objectBytesExtractor.accept(object, (bytes, length) -> digest.update(bytes, 0, length));
			return digest.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
