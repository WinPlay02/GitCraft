package com.github.winplay02.gitcraft.meta;

public record LibraryMeta(String name, LibraryDownloadsMeta downloads) {

	public record LibraryDownloadsMeta(ArtifactMeta artifact) {

	}
}
