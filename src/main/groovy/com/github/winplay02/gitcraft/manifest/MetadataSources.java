package com.github.winplay02.gitcraft.manifest;

import java.nio.file.Path;

public final class MetadataSources {

	public record RemoteVersionsManifest<M extends VersionsManifest<E>, E extends VersionsManifest.VersionEntry>(String url, Class<M> manifestClass) {
		public static <M extends VersionsManifest<E>, E extends VersionsManifest.VersionEntry> RemoteVersionsManifest<M, E> of(String url, Class<M> manifestClass) {
			return new RemoteVersionsManifest<>(url, manifestClass);
		}
	}

	public record RemoteMetadata<E extends VersionsManifest.VersionEntry>(E versionEntry) {
		public static <E extends VersionsManifest.VersionEntry> RemoteMetadata<E> of(E versionEntry) {
			return new RemoteMetadata<>(versionEntry);
		}
	}

	public record LocalRepository(Path directory) {
		public static LocalRepository of(Path directory) {
			return new LocalRepository(directory);
		}
	}
}
