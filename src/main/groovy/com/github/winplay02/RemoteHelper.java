package com.github.winplay02;

import dex.mcgitmaker.GitCraft;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;

public class RemoteHelper {

	public static final String MINECRAFT_MAIN_META_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

	public static String makeAssetUrl(String hash) {
		return String.format("https://resources.download.minecraft.net/%s/%s", hash.substring(0, 2), hash);
	}

	public static String calculateSHA1Checksum(File file) {
		if (GitCraft.config.verifyChecksums) {
			try {
				return calculateSHA1ChecksumInternal(file);
			} catch (NoSuchAlgorithmException | IOException e) {
				// throw new RuntimeException(e);
			}
		}
		return null;
	}

	static LinkedHashMap<File, String> cached_hashes = new LinkedHashMap<>();

	public static String calculateSHA1ChecksumInternal(File file) throws NoSuchAlgorithmException, IOException {
		if (cached_hashes.containsKey(file)) {
			return cached_hashes.get(file);
		}
		MessageDigest digest = MessageDigest.getInstance("SHA1");
		try (InputStream inputstream = new FileInputStream(file)) {
			byte[] buffer = new byte[16384];
			int len;

			while ((len = inputstream.read(buffer)) > 0) {
				digest.update(buffer, 0, len);
			}
		}

		byte[] sha1sum = digest.digest();

		StringBuilder hexsum = new StringBuilder();
		for (byte b : sha1sum) {
			hexsum.append(toHex(b));
		}
		String hex = hexsum.toString();
		cached_hashes.put(file, hex);
		return hex;
	}

	private static String hexChr(int b) {
		return Integer.toHexString(b & 0xF);
	}

	private static String toHex(int b) {
		return hexChr((b & 0xF0) >> 4) + hexChr(b & 0x0F);
	}

	public static void downloadToFile(String url, Path output) {
		while (true) {
			try {
				try (InputStream open_stream = new URL(url).openConnection(java.net.Proxy.NO_PROXY).getInputStream()) {
					Files.copy(open_stream, output, StandardCopyOption.REPLACE_EXISTING);
				}
				return;
			} catch (Exception e1) {
				MiscHelper.println("Failed to fetch URL: %s", url);
				MiscHelper.sleep(500);
				MiscHelper.println("Retrying... ");
			}
		}
	}

	public static boolean checksumCheckFileIsValidAndExists(File targetFile, String sha1sum, String outputFileKind, String outputFileId, boolean useRemote) {
		String fileVerbParticiple = useRemote ? "downloaded" : "read";
		String fileVerbParticipleCap = useRemote ? "Downloaded" : "Read";
		if (targetFile.exists()) {
			if (sha1sum != null && GitCraft.config.verifyChecksums) {
				String actualSha1 = calculateSHA1Checksum(targetFile);
				if (actualSha1 == null) {
					MiscHelper.panic("Could not calculate checksum of %s", targetFile);
				}
				if (!actualSha1.equalsIgnoreCase(sha1sum)) {
					if (GitCraft.config.checksumRemoveInvalidFiles) {
						MiscHelper.println("Checksum of %s %s %s is %s, expected %s. The mismatching file will now be removed (checksums mismatch)", fileVerbParticiple, outputFileKind, outputFileId, actualSha1, sha1sum);
						targetFile.delete();
						cached_hashes.remove(targetFile);
						return false;
					} else {
						MiscHelper.println("Checksum of %s %s %s is %s, expected %s. (checksums mismatch)", fileVerbParticiple, outputFileKind, outputFileId, actualSha1, sha1sum);
						return true;
					}
				} else {
					if (GitCraft.config.printExistingFileChecksumMatching || useRemote) {
						MiscHelper.println("%s %s %s is valid (checksums match)", fileVerbParticipleCap, outputFileKind, outputFileId);
					}
					return true;
				}
			} else {
				if (GitCraft.config.verifyChecksums && (GitCraft.config.printExistingFileChecksumMatchingSkipped || useRemote)) {
					MiscHelper.println("Validity cannot be determined for %s %s %s (no checksum checked)", fileVerbParticiple, outputFileKind, outputFileId);
				}
				return true;
			}
		}
		return false;
	}

	public static void downloadToFileWithChecksumIfNotExists(String url, Path output, String sha1sum, String outputFileKind, String outputFileId) {
		File targetFile = output.toFile();
		if (checksumCheckFileIsValidAndExists(targetFile, sha1sum, outputFileKind, outputFileId, false)) {
			return;
		}
		if (output.getParent() != null) {
			output.getParent().toFile().mkdirs();
		}
		do {
			try {
				MiscHelper.println("Fetching %s %s from: %s", outputFileKind, outputFileId, url);
				URLConnection url_connection = new URL(url).openConnection(java.net.Proxy.NO_PROXY);
				url_connection.setUseCaches(false);
				try (OutputStream file_output = Files.newOutputStream(output, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
					try (InputStream open_stream = new BufferedInputStream(url_connection.getInputStream())) {
						open_stream.transferTo(file_output);
					}
					file_output.flush();
				}
			} catch (Exception e1) {
				MiscHelper.println("Failed to fetch URL (retrying in %sms): %s (%s)", GitCraft.config.failedFetchRetryInterval, url, e1);
				MiscHelper.sleep(GitCraft.config.failedFetchRetryInterval);
			}
		} while (!checksumCheckFileIsValidAndExists(targetFile, sha1sum, outputFileKind, outputFileId, true));
	}

	public static void downloadToFileWithChecksumIfNotExistsNoRetry(String url, Path output, String sha1sum, String outputFileKind, String outputFileId) {
		File targetFile = output.toFile();
		if (checksumCheckFileIsValidAndExists(targetFile, sha1sum, outputFileKind, outputFileId, false)) {
			return;
		}
		if (output.getParent() != null) {
			output.getParent().toFile().mkdirs();
		}
		try {
			MiscHelper.println("Fetching %s %s from: %s", outputFileKind, outputFileId, url);
			URLConnection url_connection = new URL(url).openConnection(java.net.Proxy.NO_PROXY);
			url_connection.setUseCaches(false);
			try (OutputStream file_output = Files.newOutputStream(output, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
				try (InputStream open_stream = new BufferedInputStream(url_connection.getInputStream())) {
					open_stream.transferTo(file_output);
				}
				file_output.flush();
			}
		} catch (Exception e1) {
			MiscHelper.println("Failed to fetch URL: %s (%s)", url, e1);
			throw new RuntimeException(e1);
		}
		if (!checksumCheckFileIsValidAndExists(targetFile, sha1sum, outputFileKind, outputFileId, true)) {
			MiscHelper.panic("File download failed");
		}
	}
}
