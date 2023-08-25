package com.github.winplay02.meta;

public record LibraryMeta(String name, LibraryDownloadsMeta downloads) {

	public record LibraryDownloadsMeta(ArtifactMeta artifact) {

	}
}
