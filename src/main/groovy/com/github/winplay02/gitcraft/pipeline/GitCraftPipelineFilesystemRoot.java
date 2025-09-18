package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

public final class GitCraftPipelineFilesystemRoot {

	private GitCraftPipelineFilesystemRoot() {}

	public static Function<IPipelineFilesystemRoot, Path> getDecompiled() {
		return root -> root.getByIndex("decompiled");
	}

	public static Function<IPipelineFilesystemRoot, Path> getMappings() {
		return root -> root.getByIndex("mappings");
	}

	public static Function<IPipelineFilesystemRoot, Path> getDefaultRepository() {
		return root -> root.getByIndex("minecraft-repo");
	}

	public static Function<IPipelineFilesystemRoot, Path> getMcVersionStore() {
		return root -> root.getByIndex("mc-versions");
	}

	public static Function<IPipelineFilesystemRoot, Path> getMcMetaStore() {
		return root -> root.getByIndex("mc-meta");
	}

	public static Function<IPipelineFilesystemRoot, Path> getMcExtraVersionStore() {
		return root -> root.getByIndex("extra-versions");
	}

	public static Function<IPipelineFilesystemRoot, Path> getMcMetaDownloads() {
		return root -> root.getByIndex("mc-meta-download");
	}

	public static Function<IPipelineFilesystemRoot, Path> getLibraryStore() {
		return root -> root.getByIndex("libraries");
	}

	public static Function<IPipelineFilesystemRoot, Path> getRemapped() {
		return root -> root.getByIndex("remapped-mc");
	}

	public static Function<IPipelineFilesystemRoot, Path> getAssetsIndex() {
		return root -> root.getByIndex("assets-index");
	}

	public static Function<IPipelineFilesystemRoot, Path> getAssetsObjects() {
		return root -> root.getByIndex("assets-objects");
	}

	public static Function<IPipelineFilesystemRoot, Path> getPatchesStore() {
		return root -> root.getByIndex("patches");
	}

	public static Function<IPipelineFilesystemRoot, Path> getPatchedStore() {
		return root -> root.getByIndex("patched");
	}

	public static Function<IPipelineFilesystemRoot, Path> getRuntimeDirectory() {
		return root -> root.getByIndex("runtime");
	}

	public static void initialize(IPipelineFilesystemRoot fsRoot) throws IOException {
		Files.createDirectories(getDecompiled().apply(fsRoot));
		Files.createDirectories(getMappings().apply(fsRoot));
		Files.createDirectories(getMcVersionStore().apply(fsRoot));
		Files.createDirectories(getMcMetaStore().apply(fsRoot));
		Files.createDirectories(getMcMetaDownloads().apply(fsRoot));
		Files.createDirectories(getMcExtraVersionStore().apply(fsRoot));
		Files.createDirectories(getLibraryStore().apply(fsRoot));
		Files.createDirectories(getRemapped().apply(fsRoot));
		Files.createDirectories(getAssetsIndex().apply(fsRoot));
		Files.createDirectories(getAssetsObjects().apply(fsRoot));
		Files.createDirectories(getPatchesStore().apply(fsRoot));
		Files.createDirectories(getPatchedStore().apply(fsRoot));
		Files.createDirectories(getRuntimeDirectory().apply(fsRoot));
	}
}
