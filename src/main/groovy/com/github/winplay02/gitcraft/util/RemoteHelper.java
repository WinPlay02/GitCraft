package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.meta.GithubRepositoryBlobContent;
import com.github.winplay02.gitcraft.integrity.GitBlobSHA1Algorithm;
import com.github.winplay02.gitcraft.integrity.IntegrityAlgorithm;
import com.github.winplay02.gitcraft.integrity.SHA1Algorithm;
import dex.mcgitmaker.GitCraft;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class RemoteHelper {

	public record LocalFileInfo(Path targetFile, String checksum, String outputFileKind, String outputFileId) {
	}

	public static final IntegrityAlgorithm SHA1 = new SHA1Algorithm();

	public static final IntegrityAlgorithm GIT_BLOB_SHA1 = new GitBlobSHA1Algorithm();

	public static String makeMinecraftAssetUrl(String hash) {
		return String.format("https://resources.download.minecraft.net/%s/%s", hash.substring(0, 2), hash);
	}

	protected static void deleteFile(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean checksumCheckFileIsValidAndExists(LocalFileInfo localFileInfo, IntegrityAlgorithm integrityAlgorithm, boolean useRemote) {
		String fileVerbParticiple = useRemote ? "downloaded" : "read";
		String fileVerbParticipleCap = useRemote ? "Downloaded" : "Read";
		if (Files.exists(localFileInfo.targetFile())) {
			if (localFileInfo.checksum() != null && GitCraft.config.verifyChecksums) {
				String actualHash = integrityAlgorithm.getChecksumFile(localFileInfo.targetFile());
				if (actualHash == null) {
					MiscHelper.panic("Could not calculate checksum of %s", localFileInfo.targetFile());
				}
				if (!actualHash.equalsIgnoreCase(localFileInfo.checksum())) {
					if (GitCraft.config.checksumRemoveInvalidFiles) {
						MiscHelper.println("%s-Checksum of %s %s %s is %s, expected %s. The mismatching file will now be removed (checksums mismatch)", integrityAlgorithm.getAlgorithmName(), fileVerbParticiple, localFileInfo.outputFileKind(), localFileInfo.outputFileId(), actualHash, localFileInfo.checksum());
						deleteFile(localFileInfo.targetFile());
						integrityAlgorithm.invalidateFile(localFileInfo.targetFile());
						return false;
					} else {
						MiscHelper.println("%s-Checksum of %s %s %s is %s, expected %s. (checksums mismatch)", integrityAlgorithm.getAlgorithmName(), fileVerbParticiple, localFileInfo.outputFileKind(), localFileInfo.outputFileId(), actualHash, localFileInfo.checksum());
						return true;
					}
				} else {
					if (GitCraft.config.printExistingFileChecksumMatching || useRemote) {
						MiscHelper.println("%s %s %s is valid (checksums match)", fileVerbParticipleCap, localFileInfo.outputFileKind(), localFileInfo.outputFileId());
					}
					return true;
				}
			} else {
				if (GitCraft.config.verifyChecksums && (GitCraft.config.printExistingFileChecksumMatchingSkipped || useRemote)) {
					MiscHelper.println("Validity cannot be determined for %s %s %s (no checksum checked)", fileVerbParticipleCap, localFileInfo.outputFileKind(), localFileInfo.outputFileId());
				}
				return true;
			}
		}
		return false;
	}

	public static void downloadToFileWithChecksumIfNotExists(String url, LocalFileInfo localFileInfo, IntegrityAlgorithm integrityAlgorithm) {
		if (checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, false)) {
			return;
		}
		if (localFileInfo.targetFile().getParent() != null) {
			try {
				Files.createDirectories(localFileInfo.targetFile().getParent());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		do {
			try {
				MiscHelper.println("Fetching %s %s from: %s", localFileInfo.outputFileKind(), localFileInfo.outputFileId(), url);
				URLConnection url_connection = new URL(url).openConnection(Proxy.NO_PROXY);
				url_connection.setUseCaches(false);
				try (OutputStream file_output = Files.newOutputStream(localFileInfo.targetFile(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
					try (InputStream open_stream = new BufferedInputStream(url_connection.getInputStream())) {
						open_stream.transferTo(file_output);
					}
					file_output.flush();
				}
			} catch (Exception e1) {
				MiscHelper.println("Failed to fetch URL (retrying in %sms): %s (%s)", GitCraft.config.failedFetchRetryInterval, url, e1);
				MiscHelper.sleep(GitCraft.config.failedFetchRetryInterval);
			}
		} while (!checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, true));
	}

	public static void downloadToFileWithChecksumIfNotExistsNoRetry(String url, LocalFileInfo localFileInfo, IntegrityAlgorithm integrityAlgorithm) {
		if (checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, false)) {
			return;
		}
		if (localFileInfo.targetFile().getParent() != null) {
			try {
				Files.createDirectories(localFileInfo.targetFile().getParent());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		do {
			try {
				MiscHelper.println("Fetching %s %s from: %s", localFileInfo.outputFileKind(), localFileInfo.outputFileId(), url);
				URLConnection url_connection = new URL(url).openConnection(Proxy.NO_PROXY);
				url_connection.setUseCaches(false);
				try (OutputStream file_output = Files.newOutputStream(localFileInfo.targetFile(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
					try (InputStream open_stream = new BufferedInputStream(url_connection.getInputStream())) {
						open_stream.transferTo(file_output);
					}
					file_output.flush();
				}
				break;
			} catch (FileNotFoundException | MalformedURLException e1) {
				MiscHelper.println("Failed to fetch URL: %s (%s)", url, e1);
				deleteFile(localFileInfo.targetFile());
				throw new RuntimeException(e1);
			} catch (Exception e1) {
				MiscHelper.println("Failed to fetch URL (retrying in %sms): %s (%s)", GitCraft.config.failedFetchRetryInterval, url, e1);
				deleteFile(localFileInfo.targetFile());
				MiscHelper.sleep(GitCraft.config.failedFetchRetryInterval);
			}
		} while (!checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, true));
		if (!checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, true)) {
			MiscHelper.panic("File download failed");
		}
	}

	public static void downloadToFileWithChecksumIfNotExistsNoRetryMaven(String url, LocalFileInfo localFileInfo) {
		String urlSha1 = urlencodedURL(url + ".sha1");
		try {
			String sha1 = SerializationHelper.fetchAllFromURL(new URL(urlSha1));
			downloadToFileWithChecksumIfNotExistsNoRetry(urlencodedURL(url), new LocalFileInfo(localFileInfo.targetFile(), sha1, localFileInfo.outputFileKind(), localFileInfo.outputFileId()), SHA1);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void downloadToFileWithChecksumIfNotExistsNoRetryGitHub(String repository, String branch, String path, LocalFileInfo localFileInfo) {
		String githubApiUrl = urlencodedURL(String.format("https://api.github.com/repos/%s/contents/%s?ref=%s", repository, path, branch));
		try {
			GithubRepositoryBlobContent apiResponse = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(githubApiUrl)), GithubRepositoryBlobContent.class);
			String sha1Blob = apiResponse.sha();
			downloadToFileWithChecksumIfNotExistsNoRetry(urlencodedURL(apiResponse.download_url()), new LocalFileInfo(localFileInfo.targetFile(), sha1Blob, localFileInfo.outputFileKind(), localFileInfo.outputFileId()), GIT_BLOB_SHA1);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String urlencodedURL(String url) {
		try {
			URL urlObject = new URL(url);
			URI uriObject = new URI(urlObject.getProtocol(), urlObject.getUserInfo(), IDN.toASCII(urlObject.getHost()), urlObject.getPort(), urlObject.getPath(), urlObject.getQuery(), urlObject.getRef());
			return uriObject.toASCIIString();
		} catch (MalformedURLException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public static String readMavenLatestRelease(String mavenMetadata) throws Exception {
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(mavenMetadata);
		return XPathFactory.newInstance().newXPath().compile("/metadata/versioning/release").evaluate(document);
	}
}
