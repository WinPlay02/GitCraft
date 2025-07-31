package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record StepResults<T extends AbstractVersion<T>>(Set<StorageKey> result) {
	public static <T extends AbstractVersion<T>> StepResults<T> ofEmpty() {
		return new StepResults<T>(ConcurrentHashMap.newKeySet());
	}

	public void addKey(StorageKey storageKey) {
		this.result.add(storageKey);
	}

	public Path getPathForKeyAndAdd(Pipeline<T> pipeline, StepWorker.Context<T> context, StorageKey storageKey) {
		this.result.add(storageKey);
		return pipeline.getStoragePath(storageKey, context);
	}

	public Path getPathForDifferentVersionKeyAndAdd(Pipeline<T> pipeline, StepWorker.Context<T> context, StorageKey storageKey, T version) {
		this.result.add(storageKey);
		if (!version.equals(context.targetVersion())) {
			pipeline.relinkStoragePathToDifferentVersion(storageKey, context, version);
		}
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

	public void addAll(StepResults<T> results) {
		this.result.addAll(results.result);
	}
}
