package com.github.winplay02.gitcraft.integrity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class GitBlobSHA1Algorithm extends IntegrityAlgorithm {
	private static final byte[] BLOB_BYTES = new byte[]{(byte) 'b', (byte) 'l', (byte) 'o', (byte) 'b', (byte) ' '};

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
