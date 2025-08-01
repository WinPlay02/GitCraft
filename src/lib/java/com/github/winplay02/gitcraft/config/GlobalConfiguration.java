package com.github.winplay02.gitcraft.config;

import com.github.winplay02.gitcraft.util.MiscHelper;
import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.winplay02.gitcraft.config.Configuration.Utils.prim;

/**
 * Global Configuration
 *
 * @param checksumRemoveInvalidFiles Whether non-matching files are removed from the artifact store
 * @param printExistingFileChecksumMatching Whether existing files are logged if their checksum matches
 * @param printExistingFileChecksumMatchingSkipped Whether skipped existing files are logged (their validity cannot be proven)
 * @param printNotRunSteps Whether not run steps of the pipeline are logged
 * @param failedFetchRetryInterval Interval at which a request is resend after being completed with error
 * @param remappingThreads Amount of threads used for the remapper
 * @param decompilingThreads Amount of threads used for the decompiler
 * @param useHardlinks Whether hardlinks are used when moving files to the repository (improves performance on non-reflink supporting filesystems)
 * @param maxConcurrentHttpStreams Max amount of HTTP/2 streams that can be concurrently used per connection
 * @param maxConcurrentHttpConnections Max amount of HTTP/1.1 connections can be used
 * @param maxConcurrentHttpRequestsPerOrigin Max amount of HTTP Requests that are in flight at a given time per origin
 * @param maxParallelPipelineSteps Max amount of pipeline steps that can be processed in parallel (0 means unlimited)
 */
public record GlobalConfiguration(boolean checksumRemoveInvalidFiles,
								  boolean printExistingFileChecksumMatching,
								  boolean printExistingFileChecksumMatchingSkipped,
								  boolean printNotRunSteps,
								  int failedFetchRetryInterval,
								  int remappingThreads,
								  int decompilingThreads,
								  boolean useHardlinks,
								  int maxConcurrentHttpStreams,
								  int maxConcurrentHttpConnections,
								  int maxConcurrentHttpRequestsPerOrigin,
								  int maxParallelPipelineSteps)
	implements Configuration {

	public static final int DEFAULT_FETCH_RETRY_INTERVAL = 500;
	public static final int DEFAULT_REMAPPING_THREADS = Runtime.getRuntime().availableProcessors() - 3;
	public static final int DEFAULT_DECOMPILING_THREADS = Runtime.getRuntime().availableProcessors() - 3;
	public static final int DEFAULT_MAX_CONCURRENT_HTTP_STREAMS = 8;
	public static final int DEFAULT_MAX_CONCURRENT_HTTP_CONNECTIONS = 8;
	public static final int DEFAULT_MAX_CONCURRENT_HTTP_REQUESTS_PER_ORIGIN = 32;

	public static final GlobalConfiguration DEFAULT = new GlobalConfiguration(
		true,
		false,
		false,
		false,
		DEFAULT_FETCH_RETRY_INTERVAL,
		DEFAULT_REMAPPING_THREADS,
		DEFAULT_DECOMPILING_THREADS,
		true,
		DEFAULT_MAX_CONCURRENT_HTTP_STREAMS,
		DEFAULT_MAX_CONCURRENT_HTTP_CONNECTIONS,
		DEFAULT_MAX_CONCURRENT_HTTP_REQUESTS_PER_ORIGIN,
		0
	);

	public GlobalConfiguration {
		if (failedFetchRetryInterval <= 10) {
			failedFetchRetryInterval = DEFAULT_FETCH_RETRY_INTERVAL;
		}

		if (remappingThreads <= 0) {
			remappingThreads = DEFAULT_REMAPPING_THREADS;
		}

		if (decompilingThreads <= 0) {
			decompilingThreads = DEFAULT_DECOMPILING_THREADS;
		}

		if (maxConcurrentHttpStreams <= 0) {
			maxConcurrentHttpStreams = DEFAULT_MAX_CONCURRENT_HTTP_STREAMS;
		}

		if (maxConcurrentHttpConnections <= 0) {
			maxConcurrentHttpConnections = DEFAULT_MAX_CONCURRENT_HTTP_CONNECTIONS;
		}

		if (maxConcurrentHttpRequestsPerOrigin <= 0) {
			maxConcurrentHttpRequestsPerOrigin = DEFAULT_MAX_CONCURRENT_HTTP_REQUESTS_PER_ORIGIN;
		}

		if (maxParallelPipelineSteps < 0) {
			maxParallelPipelineSteps = DEFAULT.maxParallelPipelineSteps();
		}
	}

	@Override
	public Map<String, JsonElement> serialize() {
		return MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				"checksumRemoveInvalidFiles", prim(this.checksumRemoveInvalidFiles()),
				"printExistingFileChecksumMatching", prim(this.printExistingFileChecksumMatching()),
				"printExistingFileChecksumMatchingSkipped", prim(this.printExistingFileChecksumMatchingSkipped()),
				"printNotRunSteps", prim(this.printNotRunSteps()),
				"failedFetchRetryInterval", prim(this.failedFetchRetryInterval()),
				"remappingThreads", prim(this.remappingThreads()),
				"decompilingThreads", prim(this.decompilingThreads()),
				"useHardlinks", prim(this.useHardlinks()),
				"maxConcurrentHttpStreams", prim(this.maxConcurrentHttpStreams()),
				"maxConcurrentHttpConnections", prim(this.maxConcurrentHttpConnections())
			),
			Map.of(
				"maxConcurrentHttpRequestsPerOrigin", prim(this.maxConcurrentHttpRequestsPerOrigin()),
				"maxParallelPipelineSteps", prim(this.maxParallelPipelineSteps())
			)
		);
	}

	@Override
	public List<String> generateInfo() {
		return List.of(
			String.format("Remapping Threads: %s", this.remappingThreads()),
			String.format("Decompiling Threads: %s", this.decompilingThreads()),
			String.format("Max Concurrent Https Requests / Streams / Connections: %s / %s / %s", this.maxConcurrentHttpRequestsPerOrigin(), this.maxConcurrentHttpStreams(), this.maxConcurrentHttpConnections())
		);
	}

	public static GlobalConfiguration deserialize(Map<String, JsonElement> map) {
		return new GlobalConfiguration(
			Utils.getBoolean(map, "checksumRemoveInvalidFiles", DEFAULT.checksumRemoveInvalidFiles()),
			Utils.getBoolean(map, "printExistingFileChecksumMatching", DEFAULT.printExistingFileChecksumMatching()),
			Utils.getBoolean(map, "printExistingFileChecksumMatchingSkipped", DEFAULT.printExistingFileChecksumMatchingSkipped()),
			Utils.getBoolean(map, "printNotRunSteps", DEFAULT.printNotRunSteps()),
			Utils.getInt(map, "failedFetchRetryInterval", DEFAULT.failedFetchRetryInterval()),
			Utils.getInt(map, "remappingThreads", DEFAULT.remappingThreads()),
			Utils.getInt(map, "decompilingThreads", DEFAULT.decompilingThreads()),
			Utils.getBoolean(map, "useHardlinks", DEFAULT.useHardlinks()),
			Utils.getInt(map, "maxConcurrentHttpStreams", DEFAULT.maxConcurrentHttpStreams()),
			Utils.getInt(map, "maxConcurrentHttpConnections", DEFAULT.maxConcurrentHttpConnections()),
			Utils.getInt(map, "maxConcurrentHttpRequestsPerOrigin", DEFAULT.maxConcurrentHttpRequestsPerOrigin()),
			Utils.getInt(map, "maxParallelPipelineSteps", DEFAULT.maxParallelPipelineSteps())
		);
	}
}
