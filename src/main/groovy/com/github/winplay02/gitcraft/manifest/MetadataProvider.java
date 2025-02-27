package com.github.winplay02.gitcraft.manifest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.meta.VersionInfo;

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
	 * @return A map containing all available versions, keyed by a unique name (see {@linkplain VersionInfo#id VersionInfo.id}).
	 */
	Map<String, E> getVersions() throws IOException;

	/**
	 * Finds parent nodes to the provided version. Used to construct the {@link com.github.winplay02.gitcraft.graph.AbstractVersionGraph}.
	 *
	 * @param mcVersion Subject version
	 * @return List of parent versions, or an empty list, if the provided version is the root version. {@code null} is returned, if the default ordering should be used (specified by {@link E})
	 */
	List<String> getParentVersion(E mcVersion);

	E getVersionByVersionID(String versionId);

	/**
	 * @return whether this version should <i>definitely</i> not appear in a main branch of the version graph
	 */
	boolean shouldExcludeFromMainBranch(E mcVersion);
}
