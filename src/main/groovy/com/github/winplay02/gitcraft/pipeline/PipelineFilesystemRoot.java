package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public record PipelineFilesystemRoot(Supplier<Path> root) {
	public Path getRoot() {
		return this.root.get();
	}

	public Path getDecompiled() {
		return this.getRoot().resolve("decompiled");
	}

	public Path getMappings() {
		return this.getRoot().resolve("mappings");
	}

	public Path getDefaultRepository() {
		return this.getRoot().resolve("minecraft-repo");
	}

	public Path getMcVersionStore() {
		return this.getRoot().resolve("mc-versions");
	}

	public Path getMcMetaStore() {
		return this.getRoot().resolve("mc-meta");
	}

	public Path getMcMetaDownloads() {
		return this.getRoot().resolve("mc-meta-download");
	}

	public Path getLibraryStore() {
		return this.getRoot().resolve("libraries");
	}

	public Path getRemapped() {
		return this.getRoot().resolve("remapped-mc");
	}

	public Path getAssetsIndex() {
		return this.getRoot().resolve("assets-index");
	}

	public Path getAssetsObjects() {
		return this.getRoot().resolve("assets-objects");
	}

	public void initialize() throws IOException {
		Files.createDirectories(getDecompiled());
		Files.createDirectories(getMappings());
		Files.createDirectories(getMcVersionStore());
		Files.createDirectories(getMcMetaStore());
		Files.createDirectories(getMcMetaDownloads());
		Files.createDirectories(getLibraryStore());
		Files.createDirectories(getRemapped());
		Files.createDirectories(getAssetsIndex());
		Files.createDirectories(getAssetsObjects());
	}
}
