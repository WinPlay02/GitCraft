package com.github.winplay02.gitcraft.types;

import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.meta.ArtifactMetadata;
import com.github.winplay02.gitcraft.meta.LibraryMetadata;
import com.github.winplay02.gitcraft.meta.VersionInfo;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a minecraft version with an order
 *
 * @param versionInfo     Version Info, e.g. from Mojang, or provided in extra-versions
 * @param semanticVersion Semantic Version. This field is used to order versions.
 * @param clientJar       Client JAR Artifact, if exists
 * @param clientMappings  Client Mappings Artifact (mojmaps), if exists
 * @param serverDist      Server Distribution, if any exists (e.g. JAR Artifact, Windows Server or ZIP)
 * @param serverMappings  Server Mappings Artifact (mojmaps), if exists
 * @param libraries       Libraries needed for this version
 * @param assetsIndex     Assets Index, containing assets for this version
 */
public record OrderedVersion(
		VersionInfo versionInfo,
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

	public static Artifact getClientJarFromInfo(VersionInfo versionInfo) {
		if (versionInfo.downloads().client() != null) {
			return new Artifact(versionInfo.downloads().client().url(), "client.jar", versionInfo.downloads().client().sha1());
		}
		return null;
	}

	public static Artifact getServerJarFromInfo(VersionInfo versionInfo) {
		if (versionInfo.downloads().server() != null) {
			return new Artifact(versionInfo.downloads().server().url(), "server.jar", versionInfo.downloads().server().sha1());
		}
		return null;
	}

	public static Artifact getServerWindowsFromInfo(VersionInfo versionInfo) {
		if (versionInfo.downloads().windows_server() != null) {
			return new Artifact(versionInfo.downloads().windows_server().url(), "server.exe", versionInfo.downloads().windows_server().sha1());
		}
		return null;
	}

	public static Artifact getServerZipFromInfo(VersionInfo versionInfo) {
		if (versionInfo.downloads().server_zip() != null) {
			return new Artifact(versionInfo.downloads().server_zip().url(), "server.zip", versionInfo.downloads().server_zip().sha1());
		}
		return null;
	}

	public static OrderedVersion from(VersionInfo versionInfo, String semanticVersion) {
		Artifact clientJar = getClientJarFromInfo(versionInfo);
		Artifact clientMappings = null;
		if (versionInfo.downloads().client_mappings() != null) {
			clientMappings = Artifact.fromURL(versionInfo.downloads().client_mappings().url(), versionInfo.downloads().client_mappings().sha1());
		}
		Artifact serverJar = getServerJarFromInfo(versionInfo);
		Artifact serverWindows = getServerWindowsFromInfo(versionInfo);
		Artifact serverZip = getServerZipFromInfo(versionInfo);
		Artifact serverMappings = null;
		if (versionInfo.downloads().server_mappings() != null) {
			serverMappings = Artifact.fromURL(versionInfo.downloads().server_mappings().url(), versionInfo.downloads().server_mappings().sha1());
		}
		// Ignores natives, not needed as we don't have a runtime
		Set<Artifact> libs = new HashSet<>();
		for (LibraryMetadata library : versionInfo.libraries()) {
			ArtifactMetadata artifactMeta = library.getArtifact();
			if (artifactMeta != null) {
				libs.add(Artifact.fromURL(artifactMeta.url(), artifactMeta.sha1()));
			}
		}
		String assetsIndexId = versionInfo.id() + "_" + versionInfo.assets();
		Artifact assetsIndex = versionInfo.assetIndex() != null ? new Artifact(versionInfo.assetIndex().url(), assetsIndexId, versionInfo.assetIndex().sha1()) : null;
		return new OrderedVersion(versionInfo, semanticVersion, clientJar, clientMappings, new ServerDistribution(serverJar, serverWindows, serverZip), serverMappings, libs, assetsIndex);
	}

	public String launcherFriendlyVersionName() {
		return this.versionInfo().id();
	}

	public int javaVersion() {
		return this.versionInfo().javaVersion() != null ? this.versionInfo().javaVersion().majorVersion() : 8;
	}

	public boolean isSnapshot() {
		return Objects.equals(this.versionInfo().type(), "snapshot");
	}

	public boolean isPending() {
		return Objects.equals(this.versionInfo().type(), "pending");
	}

	public boolean isSnapshotOrPending() {
		return this.isSnapshot() || this.isPending();
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
		return this.versionInfo().mainClass();
	}

	public ZonedDateTime timestamp() {
		if (this.versionInfo().time() == null && this.versionInfo().releaseTime() == null) {
			MiscHelper.panic("cannot find timestamp for %s, as its version meta contains neither a time nor a releaseTime!", this.versionInfo().id());
			return null; // panic throws an exception anyway so this is just here to make the compiler happy
		}
		// otherwise the check against FIRST_MERGEABLE_VERSION_RELEASE_TIME may fail
		return Arrays.stream(new ZonedDateTime[]{this.versionInfo().time(), this.versionInfo().releaseTime()}).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElseThrow();
	}

	/**
	 * @return whether the client and server for this version share obfuscation mappings
	 */
	public boolean hasSharedObfuscation() {
		return !timestamp().isBefore(GitCraftConfig.RELEASE_TIME_1_3);
	}

	/**
	 * @return whether the client and server for this version share version id
	 */
	public boolean hasSharedVersioning() {
		return !timestamp().isBefore(GitCraftConfig.RELEASE_TIME_B1_0);
	}

	public boolean canBeMerged() {
		return !timestamp().isBefore(GitCraftConfig.RELEASE_TIME_A1_0_15);
	}

	public String assetsIndexId() {
		return this.versionInfo().assets();
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
