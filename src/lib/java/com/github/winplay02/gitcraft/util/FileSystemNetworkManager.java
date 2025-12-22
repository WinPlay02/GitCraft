package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.integrity.IntegrityAlgorithm;
import com.github.winplay02.gitcraft.pipeline.StepStatus;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemNetworkManager {

	public record LocalFileInfo(Path targetFile, String checksum, IntegrityAlgorithm integrityAlgorithm,
								String outputFileKind, String outputFileId) {
	}

	protected record NetworkProgressInfo(CompletableFuture<StepStatus> future, URI url,
										 IntegrityAlgorithm integrityAlgorithm, String integrityChecksum) {
	}

	protected static final Map<Path, NetworkProgressInfo> downloadJobs = new HashMap<>();
	protected static final ReadWriteLock downloadJobsLock = new ReentrantReadWriteLock();

	private interface LockGuard extends AutoCloseable {
		@Override
		void close();
	}

	private static LockGuard acquireDownloadJobsWriteLock() {
		downloadJobsLock.writeLock().lock();
		return downloadJobsLock.writeLock()::unlock;
	}

	private static LockGuard acquireDownloadJobsReadLock() {
		downloadJobsLock.readLock().lock();
		return downloadJobsLock.readLock()::unlock;
	}

	protected static final Map<Path, NetworkProgressInfo> completedJobs = new ConcurrentHashMap<>();

	public static CompletableFuture<StepStatus> fetchRemoteSerialFSAccess(Executor executor, URI url, LocalFileInfo localFileInfo, boolean retry, boolean tolerateHashUnavailable, int concurrentLimit) {
		if (completedJobs.containsKey(localFileInfo.targetFile()) &&
			Objects.equals(completedJobs.get(localFileInfo.targetFile()).integrityChecksum, localFileInfo.checksum()) &&
			Objects.equals(completedJobs.get(localFileInfo.targetFile()).integrityAlgorithm, localFileInfo.integrityAlgorithm())) {
			return CompletableFuture.completedFuture(StepStatus.UP_TO_DATE);
		}
		if (completedJobs.containsKey(localFileInfo.targetFile()) &&
			!Objects.equals(completedJobs.get(localFileInfo.targetFile()).integrityChecksum, localFileInfo.checksum()) &&
			Objects.equals(completedJobs.get(localFileInfo.targetFile()).integrityAlgorithm, localFileInfo.integrityAlgorithm())) {
			MiscHelper.panic("Cannot fulfill download to %s, there are multiple requests with different outcomes to the same file", localFileInfo.targetFile());
		}
		try (LockGuard $ = acquireDownloadJobsWriteLock()) {
			if (downloadJobs.containsKey(localFileInfo.targetFile())) {
				return downloadJobs.get(localFileInfo.targetFile()).future();
			}
			CompletableFuture<StepStatus> f = CompletableFuture.supplyAsync(() -> {
				if (checksumCheckFileIsValidAndExists(localFileInfo, false, tolerateHashUnavailable)) {
					return StepStatus.UP_TO_DATE;
				}
				if (completedJobs.containsKey(localFileInfo.targetFile())) {
					MiscHelper.panic("Cannot fulfill download to %s, there are multiple requests with different outcomes to the same file", localFileInfo.targetFile());
				}
				do {
					try {
						MiscHelper.println("Fetching %s %s from: %s", localFileInfo.outputFileKind(), localFileInfo.outputFileId(), url);
						try {
							FileSystemNetworkManager.fetchFileAsync(url, localFileInfo.targetFile(), concurrentLimit).get();
							if (!retry) {
								break;
							}
						} catch (ExecutionException ee) {
							throw ee.getCause();
						}
					} catch (FileNotFoundException | URISyntaxException e1) {
						MiscHelper.println("\u001B[31mFailed to fetch URL: %s (%s)\u001B[0m", url, e1);
						MiscHelper.deleteFile(localFileInfo.targetFile());
						MiscHelper.panicBecause(e1, "File download failed");
					} catch (Throwable e1) {
						MiscHelper.println("\u001B[31mFailed to fetch URL (retrying in %sms): %s (%s)\u001B[0m", Library.CONF_GLOBAL.failedFetchRetryInterval(), url, e1);
						e1.printStackTrace();
						MiscHelper.deleteFile(localFileInfo.targetFile());
						MiscHelper.sleep(Library.CONF_GLOBAL.failedFetchRetryInterval());
					}
				} while (!checksumCheckFileIsValidAndExists(localFileInfo, true, true));
				if (!retry && !checksumCheckFileIsValidAndExists(localFileInfo, true, true)) {
					MiscHelper.panic("File download failed");
				}
				try (LockGuard $$ = acquireDownloadJobsReadLock()) {
					completedJobs.put(localFileInfo.targetFile(), downloadJobs.get(localFileInfo.targetFile()));
				}
				return StepStatus.SUCCESS;
			}, executor);
			NetworkProgressInfo networkProgressInfo = new NetworkProgressInfo(f, url, localFileInfo.integrityAlgorithm(), localFileInfo.checksum());
			downloadJobs.put(localFileInfo.targetFile(), networkProgressInfo);
			return networkProgressInfo.future();
		}
	}

	protected static final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

	protected static final Map<String, Semaphore> connectionLimiter = new ConcurrentHashMap<>();

	protected static CompletableFuture<HttpResponse<Path>> fetchFileAsync(URI uri, Path targetFile, int concurrentLimit) {
		HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
		final Semaphore semaphore = connectionLimiter.computeIfAbsent(uri.getHost().toLowerCase(Locale.ROOT), $ ->
				new Semaphore(concurrentLimit > 0 ?
						Math.min(concurrentLimit, Library.CONF_GLOBAL.maxConcurrentHttpRequestsPerOrigin())
						: Library.CONF_GLOBAL.maxConcurrentHttpRequestsPerOrigin()));
		if (targetFile.getParent() != null) {
			try {
				Files.createDirectories(targetFile.getParent());
			} catch (IOException e) {
				MiscHelper.panicBecause(e, "Cannot create directories to store artifact %s in", targetFile);
			}
		}
		semaphore.acquireUninterruptibly();
		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofFile(targetFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE)).thenApply(response -> {
			if (response.statusCode() == 404) {
				MiscHelper.throwUnchecked(new FileNotFoundException(uri.toString()));
			}
			return response;
		}).whenComplete(($, $$) -> semaphore.release());
	}

	public static String fetchAllFromURLSync(URL url) throws IOException, URISyntaxException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(url.toURI()).GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			throw new FileNotFoundException(url.toString());
		}
		return response.body();
	}

	private static boolean checksumCheckFileIsValidAndExists(LocalFileInfo localFileInfo, boolean useRemote, boolean tolerateHashUnavailable) {
		String fileVerbParticiple = useRemote ? "downloaded" : "read";
		String fileVerbParticipleCap = useRemote ? "Downloaded" : "Read";
		if (Files.exists(localFileInfo.targetFile())) {
			if (localFileInfo.checksum() != null && Library.CONF_INTEGRITY.verifyChecksums()) {
				String actualHash = localFileInfo.integrityAlgorithm().getChecksumFile(localFileInfo.targetFile());
				if (actualHash == null) {
					MiscHelper.panic("Could not calculate checksum of %s", localFileInfo.targetFile());
				}
				if (!actualHash.equalsIgnoreCase(localFileInfo.checksum())) {
					if (Library.CONF_GLOBAL.checksumRemoveInvalidFiles()) {
						MiscHelper.println("%s-Checksum of %s %s %s is %s, expected %s. The mismatching file will now be removed \u001B[31m(checksums mismatch)\u001B[0m", localFileInfo.integrityAlgorithm().getAlgorithmName(), fileVerbParticiple, localFileInfo.outputFileKind(), localFileInfo.outputFileId(), actualHash, localFileInfo.checksum());
						MiscHelper.deleteFile(localFileInfo.targetFile());
						localFileInfo.integrityAlgorithm().invalidateFile(localFileInfo.targetFile());
						return false;
					} else {
						MiscHelper.println("%s-Checksum of %s %s %s is %s, expected %s. \u001B[31m(checksums mismatch)\u001B[0m", localFileInfo.integrityAlgorithm().getAlgorithmName(), fileVerbParticiple, localFileInfo.outputFileKind(), localFileInfo.outputFileId(), actualHash, localFileInfo.checksum());
						return true;
					}
				} else {
					if (Library.CONF_GLOBAL.printExistingFileChecksumMatching() || useRemote) {
						MiscHelper.println("%s %s %s is valid \u001B[32m(checksums match)\u001B[0m", fileVerbParticipleCap, localFileInfo.outputFileKind(), localFileInfo.outputFileId());
					}
					return true;
				}
			} else {
				if (Library.CONF_INTEGRITY.verifyChecksums() && (Library.CONF_GLOBAL.printExistingFileChecksumMatchingSkipped() || useRemote)) {
					if (tolerateHashUnavailable) {
						MiscHelper.println("Validity cannot be determined for %s %s %s (no checksum checked)", fileVerbParticipleCap, localFileInfo.outputFileKind(), localFileInfo.outputFileId());
					} else {
						MiscHelper.println("Validity cannot be determined for %s %s %s (no checksum checked, file is deleted)", fileVerbParticipleCap, localFileInfo.outputFileKind(), localFileInfo.outputFileId());
					}
				}
				return tolerateHashUnavailable;
			}
		}
		return false;
	}
}
