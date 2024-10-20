package com.github.winplay02.gitcraft.pipeline;

public enum DependencyType {
	/**
	 * does not depend on the step at all
	 */
	NONE,

	/**
	 * requires the step to be present and run first
	 */
	REQUIRED,

	/**
	 * requires the step to run first only if it is present
	 */
	NOT_REQUIRED
}
