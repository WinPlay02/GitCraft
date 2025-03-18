package com.github.winplay02.gitcraft.graph;

/**
 * A version that can be ordered inside a graph.
 *
 * @param <T> Concrete Version
 */
public interface AbstractVersion<T extends AbstractVersion<T>> extends Vertex<T> {

	/**
	 * @return Semantic version string, usable for ordering versions
	 */
	String semanticVersion();

	/**
	 * @return Friendly version string, mostly in a human-readable format
	 */
	String friendlyVersion();

	/**
	 * Convert this version into a uniquely identifiable commit message.
	 *
	 * @return Commit Message identifying this version
	 */
	String toCommitMessage();

	@Override
	default String description() {
		return String.format("%s (%s)", friendlyVersion(), semanticVersion());
	}

	/**
	 * @return This version as part of the path used on a filesystem
	 */
	default String pathName() {
		return friendlyVersion();
	}
}
