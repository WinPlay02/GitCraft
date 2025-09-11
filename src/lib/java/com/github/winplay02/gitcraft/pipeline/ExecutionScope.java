package com.github.winplay02.gitcraft.pipeline;

public enum ExecutionScope {

	/**
	 * Execution is limited to one instance per node within a version graph.
	 */
	VERSION,

	/**
	 * Execution is limited to one instance per branch within a version graph.
	 */
	BRANCH,

	/**
	 * Execution is limited to one instance per version graph.
	 */
	GRAPH

}
