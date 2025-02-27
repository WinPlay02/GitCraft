package com.github.winplay02.gitcraft.manifest;

/**
 * Represents a versions manifest json that provides metadata for available versions.
 *
 * @param <E> VersionsManifest.VersionEntry
 */
public interface VersionsManifest<E extends VersionsManifest.VersionEntry> {
	/**
	 * @return Available versions
	 */
	Iterable<E> versions();

	/**
	 * Represents a manifest entry that provides metadata for a specific version.
	 * This version has to be identifiable by an id.
	 */
	interface VersionEntry {
		/**
		 * @return Version ID
		 */
		String id();
	}
}
