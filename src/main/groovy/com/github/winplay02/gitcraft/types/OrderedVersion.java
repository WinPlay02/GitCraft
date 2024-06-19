package com.github.winplay02.gitcraft.types;

import com.github.winplay02.gitcraft.meta.ArtifactMeta;
import com.github.winplay02.gitcraft.meta.LibraryMeta;
import com.github.winplay02.gitcraft.meta.VersionMeta;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.time.ZonedDateTime;
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
 * @param serverDist      Server Distribution, if any exists (e.g. JAR Artifact, Windows Server or ZIP)
 * @param serverMappings  Server Mappings Artifact (mojmaps), if exists
 * @param libraries       Libraries needed for this version
 * @param assetsIndex     Assets Index, containing assets for this version
 */
public record OrderedVersion(
		VersionMeta versionMeta,
		String semanticVersion,
		Artifact clientJar,
		Artifact clientMappings,
		ServerDistribution serverDist,
		Artifact serverMappings,
		Set<Artifact> libraries,
		Artifact assetsIndex
) implements Comparable<OrderedVersion> {

	/*
	 * For some old Minecraft versions, the download URLs end in the same
	 * file names for both the client and server jars, thus we cannot use
	 * Artifact.fromURL, as it would place both artifacts in the same place
	 * locally.
	 */

	public static Artifact getClientJarFromMeta(VersionMeta versionMeta) {
		if (versionMeta.downloads().client() != null) {
			return new Artifact(versionMeta.downloads().client().url(), "client.jar", versionMeta.downloads().client().sha1());
		}
		return null;
	}

	public static Artifact getServerJarFromMeta(VersionMeta versionMeta) {
		if (versionMeta.downloads().server() != null) {
			return new Artifact(versionMeta.downloads().server().url(), "server.jar", versionMeta.downloads().server().sha1());
		}
		return null;
	}

	public static Artifact getServerWindowsFromMeta(VersionMeta versionMeta) {
		if (versionMeta.downloads().windows_server() != null) {
			return Artifact.fromURL(versionMeta.downloads().windows_server().url(), versionMeta.downloads().windows_server().sha1());
		}
		return null;
	}

	public static Artifact getServerZipFromMeta(VersionMeta versionMeta) {
		if (versionMeta.downloads().server_zip() != null) {
			return Artifact.fromURL(versionMeta.downloads().server_zip().url(), versionMeta.downloads().server_zip().sha1());
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
		Artifact serverWindows = getServerWindowsFromMeta(versionMeta);
		Artifact serverZip = getServerZipFromMeta(versionMeta);
		Artifact serverMappings = null;
		if (versionMeta.downloads().server_mappings() != null) {
			serverMappings = Artifact.fromURL(versionMeta.downloads().server_mappings().url(), versionMeta.downloads().server_mappings().sha1());
		}
		// Ignores natives, not needed as we don't have a runtime
		Set<Artifact> libs = new HashSet<>();
		for (LibraryMeta library : versionMeta.libraries()) {
			ArtifactMeta artifactMeta = library.getArtifactDownload();
			if (artifactMeta != null) {
				libs.add(Artifact.fromURL(artifactMeta.url(), artifactMeta.sha1()));
			}
		}
		String assetsIndexId = versionMeta.id() + "_" + versionMeta.assets();
		Artifact assetsIndex = versionMeta.assetIndex() != null ? new Artifact(versionMeta.assetIndex().url(), assetsIndexId, versionMeta.assetIndex().sha1()) : null;
		return new OrderedVersion(versionMeta, semanticVersion, clientJar, clientMappings, new ServerDistribution(serverJar, serverWindows, serverZip), serverMappings, libs, assetsIndex);
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
		return this.serverDist().hasServerCode();
	}

	public boolean hasServerJar() {
		return this.serverDist().serverJar() != null;
	}

	public boolean hasServerWindows() {
		return this.serverDist().windowsServer() != null;
	}

	public boolean hasServerZip() {
		return this.serverDist().serverZip() != null;
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

	public ZonedDateTime timestamp() {
		return this.versionMeta().time();
	}

	public String assetsIndexId() {
		return this.versionMeta().assets();
	}

	public String toCommitMessage() {
		return this.launcherFriendlyVersionName() + "\n\nSemVer: " + this.semanticVersion();
	}

	@Override
	public int compareTo(OrderedVersion o) {
		SemanticVersion thisVersion = null;
		SemanticVersion otherVersion = null;
		try {
			thisVersion = SemanticVersion.parse(semanticVersion());
		} catch (VersionParsingException e) {
			MiscHelper.panicBecause(e, "Could not parse version %s (%s) as semantic version", launcherFriendlyVersionName(), semanticVersion());
		}
		try {
			otherVersion = SemanticVersion.parse(o.semanticVersion());
		} catch (VersionParsingException e) {
			MiscHelper.panicBecause(e, "Could not parse version %s (%s) as semantic version", o.launcherFriendlyVersionName(), o.semanticVersion());
		}
		int c = thisVersion.compareTo((Version) otherVersion);
		if (c == 0) {
			String thisBuild = thisVersion.getBuildKey().orElse("");
			String otherBuild = otherVersion.getBuildKey().orElse("");
			c = thisBuild.compareTo(otherBuild);
		}
		return c;
	}
}
