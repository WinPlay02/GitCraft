package com.github.winplay02.gitcraft.manifest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;

public interface MetadataProvider<E extends AbstractVersion<E>> {
	ManifestSource getSource();

	/**
	 * @return A human-readable string that identifies this manifest provider.
	 */
	String getName();

	/**
	 * A string that uniquely identifies this metadata provider.
	 * This string should be usable in a filesystem path
	 *
	 * @return Name that identifies this manifest provider
	 */
	String getInternalName();

	/**
	 * Loads versions using given executor if they were not already loaded. Executor must be not <c>null</c>.
	 * @return A map containing all available versions, keyed by a unique name (see {@linkplain VersionInfo#id VersionInfo.id}).
	 */
	Map<String, E> getVersions(Executor executor) throws IOException;

	/**
	 * When calling the versions must be already loaded. Crashes if not.
	 * @return A map containing all available versions, keyed by a unique name (see {@linkplain VersionInfo#id VersionInfo.id}).
	 */
	Map<String, E> getVersionsAssumeLoaded();

	/**
	 * Finds parent nodes to the provided version. Used to construct the {@link com.github.winplay02.gitcraft.graph.AbstractVersionGraph}.
	 *
	 * @param mcVersion Subject version
	 * @return List of parent versions, or an empty list, if the provided version is the root version. {@code null} is returned, if the default ordering should be used (specified by {@link E})
	 */
	List<E> getParentVersions(E mcVersion);

	E getVersionByVersionID(String versionId);

	/**
	 * @return whether this version should <i>definitely</i> not appear in a main branch of the version graph
	 */
	boolean shouldExcludeFromMainBranch(E mcVersion);

	/**
	 * @return whether this version should be excluded from the version graph
	 */
	boolean shouldExclude(E mcVersion);
}
