package com.github.winplay02.gitcraft.config;

import com.github.winplay02.gitcraft.util.MiscHelper;
import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.winplay02.gitcraft.config.Configuration.Utils.prim;

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
								  int maxConcurrentHttpRequestsPerOrigin)
	implements Configuration {

	public static final int DEFAULT_FETCH_RETRY_INTERVAL = 500;
	public static final int DEFAULT_REMAPPING_THREADS = Runtime.getRuntime().availableProcessors() - 3;
	public static final int DEFAULT_DECOMPILING_THREADS = Runtime.getRuntime().availableProcessors() - 3;
	public static final int DEFAULT_MAX_CONCURRENT_HTTP_STREAMS = 8;
	public static final int DEFAULT_MAX_CONCURRENT_HTTP_CONNECTIONS = 8;
	public static final int DEFAULT_MAX_CONCURRENT_HTTP_REQUESTS_PER_ORIGIN = 32;

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
				"maxConcurrentHttpRequestsPerOrigin", prim(this.maxConcurrentHttpRequestsPerOrigin())
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
			Utils.getBoolean(map, "checksumRemoveInvalidFiles", true),
			Utils.getBoolean(map, "printExistingFileChecksumMatching", false),
			Utils.getBoolean(map, "printExistingFileChecksumMatchingSkipped", false),
			Utils.getBoolean(map, "printNotRunSteps", false),
			Utils.getInt(map, "failedFetchRetryInterval", DEFAULT_FETCH_RETRY_INTERVAL),
			Utils.getInt(map, "remappingThreads", DEFAULT_REMAPPING_THREADS),
			Utils.getInt(map, "decompilingThreads", DEFAULT_DECOMPILING_THREADS),
			Utils.getBoolean(map, "useHardlinks", true),
			Utils.getInt(map, "maxConcurrentHttpStreams", DEFAULT_MAX_CONCURRENT_HTTP_STREAMS),
			Utils.getInt(map, "maxConcurrentHttpConnections", DEFAULT_MAX_CONCURRENT_HTTP_CONNECTIONS),
			Utils.getInt(map, "maxConcurrentHttpRequestsPerOrigin", DEFAULT_MAX_CONCURRENT_HTTP_REQUESTS_PER_ORIGIN)
		);
	}
}
