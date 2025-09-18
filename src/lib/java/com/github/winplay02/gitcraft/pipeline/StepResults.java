package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record StepResults<T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig>(Set<StorageKey> result) {
	public static <T extends AbstractVersion<T>, C extends IStepContext<C, T>, D extends IStepConfig> StepResults<T, C, D> ofEmpty() {
		return new StepResults<>(ConcurrentHashMap.newKeySet());
	}

	public void addKey(StorageKey storageKey) {
		this.result.add(storageKey);
	}

	public Path getPathForKeyAndAdd(IPipeline<T, C, D> pipeline, C context, D config, StorageKey storageKey) {
		this.result.add(storageKey);
		return pipeline.getStoragePath(storageKey, context, config);
	}

	public Path getPathForDifferentVersionKeyAndAdd(IPipeline<T, C, D> pipeline, C context, D config, StorageKey storageKey, T version) {
		this.result.add(storageKey);
		if (!version.equals(context.targetVersion())) {
			pipeline.relinkStoragePathToDifferentVersion(storageKey, context, config, version);
		}
		return pipeline.getStoragePath(storageKey, context, config);
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

	public void addAll(StepResults<T, C, D> results) {
		this.result.addAll(results.result);
	}
}
