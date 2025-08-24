package com.github.winplay02.gitcraft.manifest.historic;

import com.github.winplay02.gitcraft.manifest.BaseMetadataProvider;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.metadata.ArtifactMetadata;
import com.github.winplay02.gitcraft.manifest.metadata.VersionDetails;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.manifest.skyrising.SkyrisingMetadataProvider;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherManifest;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HistoricMojangLauncherMetadataProvider extends BaseMetadataProvider<MojangLauncherManifest, MojangLauncherManifest.VersionEntry> {
	private final MojangLauncherMetadataProvider mojangLauncherMetadataProvider;
	private final SkyrisingMetadataProvider skyrisingMetadataProvider;

	public HistoricMojangLauncherMetadataProvider(MojangLauncherMetadataProvider mojangLauncherMetadataProvider, SkyrisingMetadataProvider skyrisingMetadataProvider) {
		this.mojangLauncherMetadataProvider = mojangLauncherMetadataProvider;
		this.skyrisingMetadataProvider = skyrisingMetadataProvider;
	}

	private static ArtifactMetadata resolveArtifactConflict(List<ArtifactMetadata> artifactList) {
		Map<ArtifactMetadata, String> artifactsHashes = artifactList.stream().map(f -> Map.entry(f, f.sha1())).filter(entry -> entry.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		if (artifactsHashes.values().stream().map(String::toLowerCase).distinct().count() == 1) {
			return artifactsHashes.keySet().stream()
				.filter(f -> f.url().contains("mojang.com")) // prefer mojang URL
				.findFirst().or(() -> artifactsHashes.keySet().stream().findAny())
				.orElseThrow();
		}
		return null;
	}

	private static VersionInfo mergeVersionInfo(String versionId, OrderedVersion mojangLauncherVersion, Map<VersionDetails.ManifestEntry, VersionInfo> metaSources) {
		if (metaSources.isEmpty()) {
			MiscHelper.panic("Version '%s' has no attainable manifests", versionId);
		}
		try {
			VersionInfo preferredVersionInfo = metaSources.entrySet().stream().filter(f -> f.getKey().lastModified() != null || f.getKey().time() != null)
				.min(Comparator.comparing(f -> Math.abs(MiscHelper.coalesce(f.getKey().lastModified(), f.getKey().time()).toInstant().toEpochMilli() - mojangLauncherVersion.timestamp().toInstant().toEpochMilli())))
				.map(Map.Entry::getValue).or(() -> metaSources.entrySet().stream().filter(f -> f.getKey().lastModified() == null && f.getKey().time() == null).map(Map.Entry::getValue).findAny()).orElseThrow();
			return new VersionInfo(
				MiscHelper.mergeEqualOrNullWithPreference(preferredVersionInfo::assetIndex, metaSources.values(), VersionInfo::assetIndex),
				MiscHelper.mergeEqualOrNullWithPreference(preferredVersionInfo::assets, metaSources.values(), VersionInfo::assets),
				new VersionInfo.Downloads(
					MiscHelper.mergeEqualOrNullResolveConflict(metaSources.values(), MiscHelper.chain(VersionInfo::downloads, VersionInfo.Downloads::client), HistoricMojangLauncherMetadataProvider::resolveArtifactConflict),
					MiscHelper.mergeEqualOrNullResolveConflict(metaSources.values(), MiscHelper.chain(VersionInfo::downloads, VersionInfo.Downloads::client_mappings), HistoricMojangLauncherMetadataProvider::resolveArtifactConflict),
					MiscHelper.mergeEqualOrNullResolveConflict(metaSources.values(), MiscHelper.chain(VersionInfo::downloads, VersionInfo.Downloads::server), HistoricMojangLauncherMetadataProvider::resolveArtifactConflict),
					MiscHelper.mergeEqualOrNullResolveConflict(metaSources.values(), MiscHelper.chain(VersionInfo::downloads, VersionInfo.Downloads::server_mappings), HistoricMojangLauncherMetadataProvider::resolveArtifactConflict),
					MiscHelper.mergeEqualOrNullResolveConflict(metaSources.values(), MiscHelper.chain(VersionInfo::downloads, VersionInfo.Downloads::windows_server), HistoricMojangLauncherMetadataProvider::resolveArtifactConflict),
					MiscHelper.mergeEqualOrNullResolveConflict(metaSources.values(), MiscHelper.chain(VersionInfo::downloads, VersionInfo.Downloads::server_zip), HistoricMojangLauncherMetadataProvider::resolveArtifactConflict)
				),
				versionId,
				MiscHelper.mergeEqualOrNull(metaSources.values(), VersionInfo::javaVersion),
				MiscHelper.mergeListDistinctValues(metaSources.values(), VersionInfo::libraries),
				MiscHelper.mergeEqualOrNull(metaSources.values(), VersionInfo::mainClass),
				(ZonedDateTime) MiscHelper.mergeMaxOrNull(metaSources.values(), VersionInfo::releaseTime),
				(ZonedDateTime) MiscHelper.mergeMaxOrNull(metaSources.values(), VersionInfo::time),
				MiscHelper.mergeEqualOrNull(metaSources.values(), VersionInfo::type)
			);
		} catch (Exception e) {
			MiscHelper.panicBecause(e, "Couldn't merge version info of version '%s'", versionId);
			return null;
		}
	}

	private CompletableFuture<OrderedVersion> constructMostHistoricallyTruthfulVersionEntry(Executor executor, OrderedVersion mojangLauncherVersion, VersionDetails skyrisingVersion) {
		MiscHelper.println("Merging version %s", mojangLauncherVersion.launcherFriendlyVersionName());
		List<Map.Entry<VersionDetails.ManifestEntry, CompletableFuture<VersionInfo>>> futures = skyrisingVersion.manifests().stream().map(f -> Map.entry(f, this.skyrisingMetadataProvider.fetchSpecificManifest(executor, mojangLauncherVersion.launcherFriendlyVersionName(), f))).toList();
		return CompletableFuture.allOf(futures.stream().map(Map.Entry::getValue).toArray(CompletableFuture[]::new)).thenApply($ -> {
			Map<VersionDetails.ManifestEntry, VersionInfo> versionInfos = futures.stream().map(entry -> Map.entry(entry.getKey(), entry.getValue().join())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			VersionInfo info = mergeVersionInfo(mojangLauncherVersion.launcherFriendlyVersionName(), mojangLauncherVersion, versionInfos);
			String semanticVersion = this.mojangLauncherMetadataProvider.lookupSemanticVersion(executor, info);
			return OrderedVersion.from(info, semanticVersion);
		});
	}

	@Override
	protected void loadVersions(Executor executor) throws IOException {
		this.versionsById.clear();
		this.mojangLauncherMetadataProvider.initializeAndLoadVersions(executor);
		this.skyrisingMetadataProvider.initializeAndLoadVersions(executor);
		Set<String> commonVersionIds = MiscHelper.calculateSetIntersection(this.mojangLauncherMetadataProvider.getVersions(null).keySet(), this.skyrisingMetadataProvider.getVersions(null).keySet());
		Map<String, CompletableFuture<OrderedVersion>> futureVersionsMap = new HashMap<>();
		for (String versionId : commonVersionIds) {
			OrderedVersion mojangLauncherVersion = this.mojangLauncherMetadataProvider.getVersionByVersionID(versionId);
			VersionDetails skyrisingVersion = this.skyrisingMetadataProvider.getVersionDetails(versionId); //

			Optional<VersionDetails.ManifestEntry> bestMatchingManifestEntry = Optional.empty();
			if (skyrisingVersion
				.manifests().stream().allMatch(f -> f.lastModified() != null || f.time() != null)) {
				bestMatchingManifestEntry = skyrisingVersion
					.manifests().stream()
					.min(Comparator.comparing(f -> Math.abs(MiscHelper.coalesce(f.lastModified(), f.time()).toInstant().toEpochMilli() - mojangLauncherVersion.timestamp().toInstant().toEpochMilli())));
			}
			CompletableFuture<OrderedVersion> futureVersion = null;
			if (bestMatchingManifestEntry.isPresent()) {
				futureVersion = this.loadVersionFromManifest(executor, new MojangLauncherManifest.VersionEntry(versionId, bestMatchingManifestEntry.orElseThrow().url(), bestMatchingManifestEntry.orElseThrow().hash()), this.manifestMetadata);
			}
			else {
				futureVersion = constructMostHistoricallyTruthfulVersionEntry(executor, mojangLauncherVersion, skyrisingVersion);
			}
			futureVersionsMap.put(versionId, futureVersion);
		}
		CompletableFuture.allOf(futureVersionsMap.values().toArray(new CompletableFuture[0])).join();
		for (Map.Entry<String, CompletableFuture<OrderedVersion>> futureEntry : futureVersionsMap.entrySet()) {
			this.versionsById.put(futureEntry.getKey(), futureEntry.getValue().join());
		}
	}

	@Override
	protected CompletableFuture<OrderedVersion> loadVersionFromManifest(Executor executor, MojangLauncherManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		CompletableFuture<VersionInfo> futureInfo = this.fetchVersionMetadata(executor, manifestEntry.id(), manifestEntry.url(), manifestEntry.sha1(), targetDir, "version info", VersionInfo.class);
		return futureInfo.thenApply(info -> {
			String semanticVersion = this.mojangLauncherMetadataProvider.lookupSemanticVersion(executor, info);
			return OrderedVersion.from(info, semanticVersion);
		});
	}

	@Override
	protected void loadVersionsFromRepository(Executor executor, Path dir, Consumer<OrderedVersion> loader) throws IOException {
		MiscHelper.panic("Not implemented for HistoricMojangLauncherMetadataProvider, no extra manifests can be specified here");
	}

	@Override
	protected boolean isExistingVersionMetadataValid(MojangLauncherManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		return false;
	}

	@Override
	public ManifestSource getSource() {
		return ManifestSource.MOJANG_HISTORIC;
	}

	@Override
	public String getName() {
		return "Historic Mojang Launcher Metadata";
	}

	@Override
	public String getInternalName() {
		return "historic-mojang";
	}

	@Override
	public List<String> getParentVersion(OrderedVersion mcVersion) {
		return this.mojangLauncherMetadataProvider.getParentVersion(mcVersion);
	}
}
