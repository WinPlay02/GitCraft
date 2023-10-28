package com.github.winplay02.gitcraft.types;

import com.github.winplay02.gitcraft.meta.ArtifactMeta;
import com.github.winplay02.gitcraft.meta.LibraryMeta;
import com.github.winplay02.gitcraft.meta.VersionMeta;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a minecraft version with an order
 *
 * @param versionMeta     Version Meta, e.g. from Mojang, or provided in extra-versions
 * @param semanticVersion Semantic Version. This field is used to order versions.
 * @param clientJar       Client JAR Artifact, if exists
 * @param clientMappings  Client Mappings Artifact (mojmaps), if exists
 * @param serverJar       Server JAR Artifact, if exists
 * @param serverMappings  Server Mappings Artifact (mojmaps), if exists
 * @param libraries       Libraries needed for this version
 * @param assetsIndex     Assets Index, containing assets for this version
 */
public record OrderedVersion(
		VersionMeta versionMeta,
		String semanticVersion,
		Artifact clientJar,
		Artifact clientMappings,
		Artifact serverJar,
		Artifact serverMappings,
		Set<Artifact> libraries,
		Artifact assetsIndex
) implements Comparable<OrderedVersion> {

	public static Artifact getClientJarFromMeta(VersionMeta versionMeta) {
		if (versionMeta.downloads().client() != null) {
			return Artifact.fromURL(versionMeta.downloads().client().url(), versionMeta.downloads().client().sha1());
		}
		return null;
	}

	public static Artifact getServerJarFromMeta(VersionMeta versionMeta) {
		if (versionMeta.downloads().server() != null) {
			return Artifact.fromURL(versionMeta.downloads().server().url(), versionMeta.downloads().server().sha1());
		}
		return null;
	}

	public static OrderedVersion from(VersionMeta versionMeta, String semanticVersion) {
		Artifact clientJar = getClientJarFromMeta(versionMeta);
		Artifact clientMappings = null;
		if (versionMeta.downloads().client_mappings() != null) {
			clientMappings = Artifact.fromURL(versionMeta.downloads().client_mappings().url(), versionMeta.downloads().client_mappings().sha1());
		}
		Artifact serverJar = getServerJarFromMeta(versionMeta);
		Artifact serverMappings = null;
		if (versionMeta.downloads().server_mappings() != null) {
			serverMappings = Artifact.fromURL(versionMeta.downloads().server_mappings().url(), versionMeta.downloads().server_mappings().sha1());
		}
		// Ignores natives, not needed as we don't have a runtime
		Set<Artifact> libs = new HashSet<>();
		for (LibraryMeta library : versionMeta.libraries()) {
			ArtifactMeta artifactMeta = library.downloads().artifact();
			if (artifactMeta != null) {
				libs.add(Artifact.fromURL(artifactMeta.url(), artifactMeta.sha1()));
			}
		}
		String assetsIndexId = versionMeta.id() + "_" + versionMeta.assets();
		Artifact assetsIndex = new Artifact(versionMeta.assetIndex().url(), assetsIndexId, versionMeta.assetIndex().sha1());
		return new OrderedVersion(versionMeta, semanticVersion, clientJar, clientMappings, serverJar, serverMappings, libs, assetsIndex);
	}

	public String launcherFriendlyVersionName() {
		return versionMeta.id();
	}

	public int javaVersion() {
		return versionMeta.javaVersion() != null ? versionMeta.javaVersion().majorVersion() : 8;
	}

	public boolean isSnapshot() {
		return Objects.equals(versionMeta.type(), "snapshot");
	}

	public boolean isPending() {
		return Objects.equals(versionMeta.type(), "pending");
	}

	public boolean isSnapshotOrPending() {
		return isSnapshot() || isPending();
	}

	public boolean hasClientCode() {
		return this.clientJar() != null;
	}

	public boolean hasServerCode() {
		return this.serverJar() != null;
	}

	public boolean hasClientMojMaps() {
		return this.clientMappings() != null;
	}

	public boolean hasServerMojMaps() {
		return this.serverMappings() != null;
	}

	public boolean hasFullMojMaps() {
		return this.hasClientMojMaps() && this.hasServerMojMaps();
	}

	public String mainClass() {
		return this.versionMeta().mainClass();
	}

	public String timestamp() {
		return this.versionMeta().time();
	}

	public String assetsIndexId() {
		return this.versionMeta().assets();
	}

	public String toCommitMessage() {
		return this.launcherFriendlyVersionName() + "\n\nSemVer: " + this.semanticVersion();
	}

	public int compareTo(SemanticVersion o) {
		try {
			return SemanticVersion.parse(semanticVersion()).compareTo((Version) o);
		} catch (VersionParsingException e) {
			MiscHelper.panicBecause(e, "Could not parse version %s (%s) as semantic version", launcherFriendlyVersionName(), semanticVersion());
		}
		return 0;
	}

	@Override
	public int compareTo(OrderedVersion o) {
		SemanticVersion thisVersion = null;
		try {
			thisVersion = SemanticVersion.parse(semanticVersion());
		} catch (VersionParsingException e) {
			MiscHelper.panicBecause(e, "Could not parse version %s (%s) as semantic version", launcherFriendlyVersionName(), semanticVersion());
		}
		try {
			return thisVersion.compareTo((Version) SemanticVersion.parse(o.semanticVersion()));
		} catch (VersionParsingException e) {
			MiscHelper.panicBecause(e, "Could not parse version %s (%s) as semantic version", o.launcherFriendlyVersionName(), o.semanticVersion());
		}
		return 0;
	}
}
