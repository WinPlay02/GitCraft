package com.github.winplay02.gitcraft.graph;

/**
 * A version that can be ordered inside a graph.
 *
 * @param <T> Concrete Version
 */
public interface AbstractVersion<T extends AbstractVersion<T>> extends Comparable<T> {

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
}
