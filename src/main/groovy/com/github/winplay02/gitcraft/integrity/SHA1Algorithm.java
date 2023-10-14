package com.github.winplay02.gitcraft.integrity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SHA1Algorithm extends IntegrityAlgorithm {
	@Override
	public String getAlgorithmName() {
		return "SHA1";
	}

	@Override
	protected <T> byte[] calculateChecksum(T object, BiConsumer<T, BiConsumer<byte[], Integer>> objectBytesExtractor, Function<T, Long> objectLengthExtractor) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA1");
			objectBytesExtractor.accept(object, (bytes, length) -> digest.update(bytes, 0, length));
			return digest.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
