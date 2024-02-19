package com.github.winplay02.gitcraft.meta;

/**
 * Represents an abstract version provided by a launcher meta source.
 * This version has to be identifiable by an id.
 */
public interface ILauncherMetaVersionEntry {
	/**
	 * @return Version ID
	 */
	String id();
}
