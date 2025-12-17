package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.LibraryPaths;
import com.github.winplay02.gitcraft.manifest.metadata.ArtifactMetadata;
import com.github.winplay02.gitcraft.manifest.metadata.GithubRepositoryBlobContent;
import com.github.winplay02.gitcraft.pipeline.StepStatus;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import com.github.winplay02.gitcraft.util.FileSystemNetworkManager.LocalFileInfo;

public class RemoteHelper {

	public static StepStatus downloadToFileWithChecksumIfNotExists(Executor executor, String url, LocalFileInfo localFileInfo) {
		return downloadToFileWithChecksumIfNotExists(executor, url, localFileInfo, true);
	}

	public static StepStatus downloadToFileWithChecksumIfNotExists(Executor executor, String url, LocalFileInfo localFileInfo, boolean retry) {
		URI uri = null;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			MiscHelper.println("\u001B[31mFailed to fetch URL: %s (%s)\u001B[0m", url, e);
			MiscHelper.deleteFile(localFileInfo.targetFile());
			MiscHelper.panicBecause(e, "File download failed");
		}
		while (true) {
			try {
				return FileSystemNetworkManager.fetchRemoteSerialFSAccess(executor, uri, localFileInfo, retry, false, -1).get();
			} catch (InterruptedException e) {
				MiscHelper.println("Interrupted while waiting for download of %s to complete", url);
				continue;
			} catch (ExecutionException e) {
				MiscHelper.panicBecause(e, "Exception occurred while waiting for download of %s", url);
			}
			break;
		}
		return null; // should never happen
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
			SerializationHelper.writeAllToPath(LibraryPaths.MAVEN_CACHE, SerializationHelper.serialize(mavenCache));
		} catch (NoSuchFileException ignored) {}
		// If it can't save, it probably means, we're in a testing environment, after cleaning up
		// If this occurs during normal use, it just won't cache anything. Too bad.
	}

	public static void loadMavenCache() throws IOException {
		if (Files.exists(LibraryPaths.MAVEN_CACHE)) {
			mavenCache = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(LibraryPaths.MAVEN_CACHE), MavenCache.class);
		}
	}

	public static ArtifactMetadata createMavenURLFromMavenArtifact(String mavenUrl) throws IOException {
		return new ArtifactMetadata(mavenCache.getSha1ForURL(urlencodedURL(mavenUrl + ".sha1")), -1, urlencodedURL(mavenUrl));
	}

	public static StepStatus downloadToFileWithChecksumIfNotExistsNoRetryMaven(Executor executor, String url, LocalFileInfo localFileInfo) {
		try {
			String sha1 = mavenCache.getSha1ForURL(urlencodedURL(url + ".sha1"));
			return downloadToFileWithChecksumIfNotExists(executor, urlencodedURL(url), new LocalFileInfo(localFileInfo.targetFile(), sha1, Library.IA_SHA1, localFileInfo.outputFileKind(), localFileInfo.outputFileId()), false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static StepStatus downloadToFileWithChecksumIfNotExistsNoRetryGitHub(Executor executor, String repository, String branch, String path, LocalFileInfo localFileInfo) {
		String githubApiUrl = urlencodedURL(String.format("https://api.github.com/repos/%s/contents/%s?ref=%s", repository, path, branch));
		try {
			GithubRepositoryBlobContent apiResponse = SerializationHelper.deserialize(FileSystemNetworkManager.fetchAllFromURLSync(new URL(githubApiUrl)), GithubRepositoryBlobContent.class);
			String sha1Blob = apiResponse.sha();
			return downloadToFileWithChecksumIfNotExists(executor, apiResponse.download_url(), new LocalFileInfo(localFileInfo.targetFile(), sha1Blob, Library.IA_GIT_BLOB_SHA1, localFileInfo.outputFileKind(), localFileInfo.outputFileId()), false);
		} catch (IOException | URISyntaxException | InterruptedException e) {
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
