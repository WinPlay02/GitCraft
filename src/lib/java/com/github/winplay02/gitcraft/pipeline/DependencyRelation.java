package com.github.winplay02.gitcraft.pipeline;

public enum DependencyRelation {

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
	NOT_REQUIRED;

	/**
	 * @return Whether the type describes a dependency relationship
	 */
	public boolean isDependency() {
		return this == REQUIRED || this == NOT_REQUIRED;
	}
}
