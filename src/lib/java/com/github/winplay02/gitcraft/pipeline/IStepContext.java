package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.util.concurrent.ExecutorService;

public interface IStepContext<C extends IStepContext<C, T>, T extends AbstractVersion<T>> {
	RepoWrapper repository();

	AbstractVersionGraph<T> versionGraph();

	T targetVersion();

	ExecutorService executorService();

	C withDifferingVersion(T targetVersion);

	record SimpleStepContext<T extends AbstractVersion<T>>(RepoWrapper repository, AbstractVersionGraph<T> versionGraph, T targetVersion, ExecutorService executorService) implements IStepContext<SimpleStepContext<T>, T> {

		public SimpleStepContext<T> withDifferingVersion(T targetVersion) {
			return new SimpleStepContext<>(repository, versionGraph, targetVersion, executorService);
		}

		@Override
		public String toString() {
			return "version: %s".formatted(targetVersion.friendlyVersion());
		}
	}
}
