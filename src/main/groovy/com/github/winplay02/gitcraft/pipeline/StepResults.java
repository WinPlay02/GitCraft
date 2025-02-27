package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record StepResults(Set<StorageKey> result) {
	public static StepResults ofEmpty() {
		return new StepResults(ConcurrentHashMap.newKeySet());
	}

	public void addKey(StorageKey storageKey) {
		this.result.add(storageKey);
	}

	public Path getPathForKeyAndAdd(Pipeline pipeline, StepWorker.Context context, StorageKey storageKey) {
		this.result.add(storageKey);
		return pipeline.getStoragePath(storageKey, context);
	}

	public Path getPathForDifferentVersionKeyAndAdd(Pipeline pipeline, StepWorker.Context context, StorageKey storageKey, OrderedVersion version) {
		this.result.add(storageKey);
		pipeline.relinkStoragePathToDifferentVersion(storageKey, context, version);
		return pipeline.getStoragePath(storageKey, context);
	}

	public Optional<StorageKey> getKeyIfExists(StorageKey key) {
		return this.result.contains(key) ? Optional.of(key) : Optional.empty();
	}

	public Optional<StorageKey> getKeyByPriority(StorageKey... keys) {
		for (StorageKey key : keys) {
			if (this.result.contains(key)) {
				return Optional.of(key);
			}
		}
		return Optional.empty();
	}

	public void addAll(StepResults results) {
		this.result.addAll(results.result);
	}
}
