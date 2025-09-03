package com.github.winplay02.gitcraft.graph;

/**
 * Vertex Type
 * @param <T> Concrete Type
 */
public interface Vertex<T extends Vertex<T>> extends Comparable<T> {
	default String description() {
		return toString();
	}
}
