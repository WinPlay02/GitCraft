package com.github.winplay02.gitcraft.pipeline;

public enum ParallelismPolicy {
	/**
	 * Execution is allowed to happen fully in parallel.
	 */
	SAFELY_FULLY_PARALLEL,

	/**
	 * Execution is restricted to one instance at a time, making it sequential.
	 */
	UNSAFE_RESTRICTED_TO_SEQUENTIAL;

	public boolean isParallel() {
		return this == SAFELY_FULLY_PARALLEL;
	}

	public boolean isRestrictedToSequential() {
		return this == UNSAFE_RESTRICTED_TO_SEQUENTIAL;
	}
}
