package com.github.winplay02.gitcraft.types;

import com.github.winplay02.gitcraft.GitCraftQuirks;
import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.manifest.metadata.ArtifactMetadata;
import com.github.winplay02.gitcraft.manifest.metadata.LibraryMetadata;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
) implements AbstractVersion<OrderedVersion> {

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
			List<ArtifactMetadata> artifactMeta = library.getArtifact();
			for (ArtifactMetadata singleArtifactMeta : artifactMeta) {
				if (singleArtifactMeta != null) {
					libs.add(Artifact.fromURL(singleArtifactMeta.url(), singleArtifactMeta.sha1()));
				}
			}
		}
		String assetsIndexId = versionInfo.assetIndex() != null ? versionInfo.id() + "_" + versionInfo.assets() + "_" + versionInfo.assetIndex().sha1() : null;
		Artifact assetsIndex = versionInfo.assetIndex() != null ? new Artifact(versionInfo.assetIndex().url(), assetsIndexId, versionInfo.assetIndex().sha1()) : null;
		return new OrderedVersion(versionInfo, semanticVersion, clientJar, clientMappings, new ServerDistribution(serverJar, serverWindows, serverZip), serverMappings, libs, assetsIndex);
	}

	public String launcherFriendlyVersionName() {
		return this.versionInfo().id();
	}

	public int javaVersion() {
		return this.versionInfo().javaVersion() != null ? this.versionInfo().javaVersion().majorVersion() : 8;
	}

    // Found in all manifests
	public boolean isSnapshot() {
		return Objects.equals(this.versionInfo().type(), "snapshot");
	}

	// Can be found in Mojang and Skyrising manifests
	public boolean isPending() {
		return Objects.equals(this.versionInfo().type(), "pending");
	}

	// Mojang and Skyrising
	public boolean isOldBeta() {
		return Objects.equals(this.versionInfo().type(), "old_beta");
	}

	// Mojang and Skyrising
	public boolean isOldAlpha() {
		return Objects.equals(this.versionInfo().type(), "old_alpha");
	}

	// Skyrising
	public boolean isAlphaServer() {
		return Objects.equals(this.versionInfo().type(), "alpha_server");
	}

	// Skyrising
	public boolean isClassicServer() {
		return Objects.equals(this.versionInfo().type(), "classic_server");
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

	public boolean hasSideMissing() {
		return this.hasSharedVersioning() && (!this.hasClientCode() || !this.hasServerCode());
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
		return !timestamp().isBefore(GitCraftQuirks.RELEASE_TIME_1_3);
	}

	/**
	 * @return whether the client and server for this version share version id
	 */
	public boolean hasSharedVersioning() {
		return !timestamp().isBefore(GitCraftQuirks.RELEASE_TIME_B1_0);
	}

	public boolean canBeMerged() {
		return !timestamp().isBefore(GitCraftQuirks.RELEASE_TIME_A1_0_15);
	}

	public String assetsIndexId() {
		return this.versionInfo().assets();
	}

	@Override
	public String friendlyVersion() {
		return this.launcherFriendlyVersionName();
	}

	@Override
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

	public boolean hasSideInCommon(OrderedVersion o) {
		return (this.hasClientCode() && o.hasClientCode()) || (this.hasServerCode() && o.hasServerCode());
	}
}
