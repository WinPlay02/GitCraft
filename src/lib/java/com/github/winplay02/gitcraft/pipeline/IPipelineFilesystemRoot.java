package com.github.winplay02.gitcraft.pipeline;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

public interface IPipelineFilesystemRoot {
	Path getRoot();

	Path getByIndex(String index);

	static Function<IPipelineFilesystemRoot, Path> getPathIndexed(String path) {
		return src -> src.getByIndex(path);
	}

	record SimpleSuppliedPipelineFilesystemRoot(Supplier<Path> root) implements IPipelineFilesystemRoot {
		public Path getRoot() {
			return this.root.get();
		}

		@Override
		public Path getByIndex(String index) {
			return this.getRoot().resolve(index);
		}
	}
}
