package com.github.winplay02.gitcraft.meta;

/**
 * Represents an abstract "Launcher" Meta that provides multiple available versions.
 *
 * @param <T> ILauncherMetaVersionEntry
 */
public interface ILauncherMeta<T extends ILauncherMetaVersionEntry> {
	/**
	 * @return Available versions
	 */
	Iterable<T> versions();
}
