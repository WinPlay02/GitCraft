package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.integrity.GitBlobSHA1Algorithm;
import com.github.winplay02.gitcraft.integrity.IntegrityAlgorithm;
import com.github.winplay02.gitcraft.integrity.SHA1Algorithm;
import com.github.winplay02.gitcraft.meta.ArtifactMetadata;
import com.github.winplay02.gitcraft.meta.GithubRepositoryBlobContent;
import com.github.winplay02.gitcraft.pipeline.Step;
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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class RemoteHelper {

	public record LocalFileInfo(Path targetFile, String checksum, String outputFileKind, String outputFileId) {
	}

	public static final IntegrityAlgorithm SHA1 = new SHA1Algorithm();

	public static final IntegrityAlgorithm GIT_BLOB_SHA1 = new GitBlobSHA1Algorithm();

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
						MiscHelper.println("%s-Checksum of %s %s %s is %s, expected %s. The mismatching file will now be removed \u001B[31m(checksums mismatch)\u001B[0m", integrityAlgorithm.getAlgorithmName(), fileVerbParticiple, localFileInfo.outputFileKind(), localFileInfo.outputFileId(), actualHash, localFileInfo.checksum());
						MiscHelper.deleteFile(localFileInfo.targetFile());
						integrityAlgorithm.invalidateFile(localFileInfo.targetFile());
						return false;
					} else {
						MiscHelper.println("%s-Checksum of %s %s %s is %s, expected %s. \u001B[31m(checksums mismatch)\u001B[0m", integrityAlgorithm.getAlgorithmName(), fileVerbParticiple, localFileInfo.outputFileKind(), localFileInfo.outputFileId(), actualHash, localFileInfo.checksum());
						return true;
					}
				} else {
					if (GitCraft.config.printExistingFileChecksumMatching || useRemote) {
						MiscHelper.println("%s %s %s is valid \u001B[32m(checksums match)\u001B[0m", fileVerbParticipleCap, localFileInfo.outputFileKind(), localFileInfo.outputFileId());
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

	public static Step.StepResult downloadToFileWithChecksumIfNotExists(String url, LocalFileInfo localFileInfo, IntegrityAlgorithm integrityAlgorithm) {
		if (checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, false)) {
			return Step.StepResult.UP_TO_DATE;
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
				MiscHelper.println("\u001B[31mFailed to fetch URL (retrying in %sms): %s (%s)\u001B[0m", GitCraft.config.failedFetchRetryInterval, url, e1);
				MiscHelper.sleep(GitCraft.config.failedFetchRetryInterval);
			}
		} while (!checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, true));
		return Step.StepResult.SUCCESS;
	}

	public static Step.StepResult downloadToFileWithChecksumIfNotExistsNoRetry(String url, LocalFileInfo localFileInfo, IntegrityAlgorithm integrityAlgorithm) {
		if (checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, false)) {
			return Step.StepResult.UP_TO_DATE;
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
				MiscHelper.println("\u001B[31mFailed to fetch URL: %s (%s)\u001B[0m", url, e1);
				MiscHelper.deleteFile(localFileInfo.targetFile());
				throw new RuntimeException(e1);
			} catch (Exception e1) {
				MiscHelper.println("\u001B[31mFailed to fetch URL (retrying in %sms): %s (%s)\u001B[0m", GitCraft.config.failedFetchRetryInterval, url, e1);
				MiscHelper.deleteFile(localFileInfo.targetFile());
				MiscHelper.sleep(GitCraft.config.failedFetchRetryInterval);
			}
		} while (!checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, true));
		if (!checksumCheckFileIsValidAndExists(localFileInfo, integrityAlgorithm, true)) {
			MiscHelper.panic("File download failed");
		}
		return Step.StepResult.SUCCESS;
	}

	public static String createMavenURLFromMavenArtifact(String baseUrl, String mavenArtifact) {
		String[] components = mavenArtifact.split(":");
		String group = components[0];
		String artifact = components[1];
		String version = components[2];
		return String.format("%s/%s/%s/%s/%s-%s.jar", baseUrl, group.replace(".", "/"), artifact, version, artifact, version);
	}

	private static MavenCache mavenCache = new MavenCache();

	public static void saveMavenCache() throws IOException {
		try {
			SerializationHelper.writeAllToPath(GitCraftPaths.MAVEN_CACHE, SerializationHelper.serialize(mavenCache));
		} catch (NoSuchFileException ignored) {}
		// If it can't save, it probably means, we're in a testing environment, after cleaning up
		// If this occurs during normal use, it just won't cache anything. Too bad.
	}

	public static void loadMavenCache() throws IOException {
		if (Files.exists(GitCraftPaths.MAVEN_CACHE)) {
			mavenCache = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(GitCraftPaths.MAVEN_CACHE), MavenCache.class);
		}
	}

	public static ArtifactMetadata createMavenURLFromMavenArtifact(String mavenUrl) throws IOException {
		return new ArtifactMetadata(mavenCache.getSha1ForURL(urlencodedURL(mavenUrl + ".sha1")), -1, urlencodedURL(mavenUrl));
	}

	public static Step.StepResult downloadToFileWithChecksumIfNotExistsNoRetryMaven(String url, LocalFileInfo localFileInfo) {
		try {
			String sha1 = mavenCache.getSha1ForURL(urlencodedURL(url + ".sha1"));
			return downloadToFileWithChecksumIfNotExistsNoRetry(urlencodedURL(url), new LocalFileInfo(localFileInfo.targetFile(), sha1, localFileInfo.outputFileKind(), localFileInfo.outputFileId()), SHA1);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Step.StepResult downloadToFileWithChecksumIfNotExistsNoRetryGitHub(String repository, String branch, String path, LocalFileInfo localFileInfo) {
		String githubApiUrl = urlencodedURL(String.format("https://api.github.com/repos/%s/contents/%s?ref=%s", repository, path, branch));
		try {
			GithubRepositoryBlobContent apiResponse = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(githubApiUrl)), GithubRepositoryBlobContent.class);
			String sha1Blob = apiResponse.sha();
			return downloadToFileWithChecksumIfNotExistsNoRetry(apiResponse.download_url(), new LocalFileInfo(localFileInfo.targetFile(), sha1Blob, localFileInfo.outputFileKind(), localFileInfo.outputFileId()), GIT_BLOB_SHA1);
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
