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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class HistoricMojangLauncherMetadataProvider extends BaseMetadataProvider<MojangLauncherManifest, MojangLauncherManifest.VersionEntry> {
	private static final Map<String, String> MOJANG_SKYRISING_VERSION_ID_MAPPING = Map.<String, String>ofEntries(
		// Version Strings
		Map.entry("1.0", "1.0.0"),
		Map.entry("1.3", "1.3-pre-07261249"),
		Map.entry("1.4", "1.4-pre"),
		Map.entry("1.4.1", "1.4.1-pre-10231538"),
		Map.entry("1.4.3", "1.4.3-pre"),
		Map.entry("1.6", "1.6-pre-06251516"),
		Map.entry("1.6.2", "1.6.2-091847"),
		Map.entry("1.6.3", "1.6.3-pre-171231"),
		Map.entry("1.7", "1.7-pre"),
		Map.entry("1.7.1", "1.7.1-pre"),
		Map.entry("1.7.3", "1.7.3-pre"),
		Map.entry("1.7.7", "1.7.7-101331"),
		Map.entry("1.16", "1.16-1420"), // or should this map to 1.16-1149?
		// Pre
		Map.entry("1.12-pre3", "1.12-pre3-1409"), // or should this map to 1.12-pre3-1316?
		Map.entry("1.14 Pre-Release 1", "1.14-pre1"),
		Map.entry("1.14 Pre-Release 2", "1.14-pre2"),
		Map.entry("1.14 Pre-Release 3", "1.14-pre3"),
		Map.entry("1.14 Pre-Release 4", "1.14-pre4"),
		Map.entry("1.14 Pre-Release 5", "1.14-pre5"),
		Map.entry("1.14.1 Pre-Release 1", "1.14.1-pre1"),
		Map.entry("1.14.1 Pre-Release 2", "1.14.1-pre2"),
		Map.entry("1.14.2 Pre-Release 1", "1.14.2-pre1"),
		Map.entry("1.14.2 Pre-Release 2", "1.14.2-pre2"),
		Map.entry("1.14.2 Pre-Release 3", "1.14.2-pre3"),
		Map.entry("1.14.2 Pre-Release 4", "1.14.2-pre4-270720"), // or should this map to 1.14.2-pre4-241548?
		Map.entry("1.16.5-rc1", "1.16.5-rc1-2"), // or should this map to 1.16.5-rc1-1?
		// Snapshot Strings
		Map.entry("13w16a", "13w16a-04192037"),
		Map.entry("13w16b", "13w16b-04232151"),
		Map.entry("13w23b", "13w23b-06080101"),
		Map.entry("13w36a", "13w36a-09051446"),
		Map.entry("13w36b", "13w36b-09061310"),
		Map.entry("13w41b", "13w41b-1523"),
		Map.entry("14w04b", "14w04b-1554"),
		Map.entry("14w27b", "14w27b-07021646"),
		Map.entry("14w34c", "14w34c-08191549"),
		Map.entry("16w50a", "16w50a-1438"),
		Map.entry("19w13b", "19w13b-1653"),
		// Combat; they are already in historic condition
		Map.entry("1.14_combat-0", "combat-2"),
		Map.entry("1.14_combat-212796", "combat-1"),
		Map.entry("1.14_combat-3", "combat-3"),
		Map.entry("1.15_combat-1", "combat-4"),
		Map.entry("1.15_combat-6", "combat-5"),
		Map.entry("1.16_combat-0", "combat-6"),
		Map.entry("1.16_combat-1", "combat-7a"),
		Map.entry("1.16_combat-2", "combat-7b"),
		Map.entry("1.16_combat-3", "combat-7c"),
		// Map.entry("1.16_combat-4", "combat-8a"),
		Map.entry("1.16_combat-5", "combat-8b"),
		Map.entry("1.16_combat-6", "combat-8c"),
		// April
		Map.entry("15w14a", "af-2015"),
		Map.entry("1.RV-Pre1", "af-2016"),
		Map.entry("3D Shareware v1.34", "af-2019"),
		Map.entry("20w14infinite", "af-2020"),
		Map.entry("22w13oneblockatatime", "af-2022"),
		Map.entry("23w13a_or_b", "af-2023-2"),
		Map.entry("23w13a_or_b_original", "af-2023-1"),
		Map.entry("24w14potato", "af-2024-2"),
		Map.entry("24w14potato_original", "af-2024"),
		Map.entry("25w14craftmine", "af-2025"),
		// Other
		Map.entry("rd-132211", "rd-132211-launcher"),
		Map.entry("rd-132328", "rd-132328-launcher"),
		Map.entry("rd-160052", "rd-160052-launcher"),
		Map.entry("rd-161348", "rd-161348-launcher"),
		Map.entry("c0.0.11a", "c0.0.11a-launcher"),
		Map.entry("c0.0.13a", "c0.0.13a-launcher"),
		Map.entry("c0.0.13a_03", "c0.0.13a_03-launcher"),
		Map.entry("c0.30_01c", "c0.30-c-renew"),
		Map.entry("a1.0.14", "a1.0.14-1659"), // or should this map to a1.0.14-1603, a1.0.14-1659-launcher?
		Map.entry("a1.1.0", "a1.1.0-131933"), // or should this map to a1.1.0-101847, a1.1.0-101847-launcher?
		// Map.entry("a1.2.1", "a1.2.1_01"), // just a duplicate, no need to include this
		Map.entry("a1.2.2a", "a1.2.2-1624"),
		Map.entry("a1.2.2b", "a1.2.2-1938"),
		Map.entry("a1.2.3_01", "a1.2.3_01-0958"),
		Map.entry("b1.3b", "b1.3-1750"), // or should this map to b1.3-1647, b1.3-1713, b1.3-1731?
		Map.entry("b1.4", "b1.4-1634") // or should this map to b1.4-1507?
	);

	private static final Map<String, String> SKYRISING_MOJANG_VERSION_ID_MAPPING = MiscHelper.invertMapping(MOJANG_SKYRISING_VERSION_ID_MAPPING);

	static {
		if (MOJANG_SKYRISING_VERSION_ID_MAPPING.size() != SKYRISING_MOJANG_VERSION_ID_MAPPING.size()) {
			MiscHelper.panic("Inconsistent (non-bijective) mapping between mojang <-> skyrising version manifest subset");
		}
	}

	private static final Set<String> VERSION_IDS_INCLUDE_EVEN_IF_NOT_PAIRED = Set.of(
		"1.16_combat-4"
	);

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
				MiscHelper.mergeEqualOrNull(metaSources.values(), VersionInfo::type),
				MiscHelper.mergeEqualOrNull(metaSources.values(), VersionInfo::arguments),
				MiscHelper.mergeEqualOrNull(metaSources.values(), VersionInfo::minecraftArguments)
			);
		} catch (Exception e) {
			MiscHelper.panicBecause(e, "Couldn't merge version info of version '%s'", versionId);
			return null;
		}
	}

	private CompletableFuture<OrderedVersion> constructMostHistoricallyTruthfulVersionEntry(Executor executor, String versionId, OrderedVersion mojangLauncherVersion, VersionDetails skyrisingVersion) {
		MiscHelper.println("Merging version %s", versionId);
		List<Map.Entry<VersionDetails.ManifestEntry, CompletableFuture<VersionInfo>>> futures = skyrisingVersion.manifests().stream().map(f -> Map.entry(f, this.skyrisingMetadataProvider.fetchSpecificManifest(executor, versionId, f))).toList();
		return CompletableFuture.allOf(futures.stream().map(Map.Entry::getValue).toArray(CompletableFuture[]::new)).thenApply($ -> {
			Map<VersionDetails.ManifestEntry, VersionInfo> versionInfos = futures.stream().map(entry -> Map.entry(entry.getKey(), entry.getValue().join())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			VersionInfo info = mergeVersionInfo(versionId, mojangLauncherVersion, versionInfos);
			String semanticVersion = this.mojangLauncherMetadataProvider.lookupSemanticVersion(executor, info);
			return OrderedVersion.from(info, semanticVersion);
		});
	}

	@Override
	protected void loadVersions(Executor executor) throws IOException {
		this.versionsById.clear();
		Set<String> mojangVersionIds = this.mojangLauncherMetadataProvider.getVersions(executor).keySet();
		Set<String> skyrisingVersionIds = this.skyrisingMetadataProvider.getVersions(executor).keySet();
		Set<String> transformedSkyrisingVersionIds = MiscHelper.concatStreams(
			skyrisingVersionIds.stream().filter(SKYRISING_MOJANG_VERSION_ID_MAPPING::containsKey).map(SKYRISING_MOJANG_VERSION_ID_MAPPING::get),
			skyrisingVersionIds.stream().filter(Predicate.not(SKYRISING_MOJANG_VERSION_ID_MAPPING::containsKey))
		).collect(Collectors.toSet());
		Set<String> commonVersionIds = MiscHelper.calculateSetIntersection(mojangVersionIds, transformedSkyrisingVersionIds);
		Set<String> unpairedMojangVersions = MiscHelper.calculateAsymmetricSetDifference(mojangVersionIds, commonVersionIds);
		Map<String, CompletableFuture<OrderedVersion>> futureVersionsMap = new HashMap<>();
		// Paired
		for (String versionId : commonVersionIds) {
			OrderedVersion mojangLauncherVersion = this.mojangLauncherMetadataProvider.getVersionByVersionID(versionId);
			VersionDetails skyrisingVersion = this.skyrisingMetadataProvider.getVersionDetails(MOJANG_SKYRISING_VERSION_ID_MAPPING.containsKey(versionId) ? MOJANG_SKYRISING_VERSION_ID_MAPPING.get(versionId) : versionId);

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
				futureVersion = constructMostHistoricallyTruthfulVersionEntry(executor, versionId, mojangLauncherVersion, skyrisingVersion);
			}
			futureVersionsMap.put(versionId, futureVersion);
		}
		// Unpaired
		for (String versionId : MiscHelper.calculateSetIntersection(unpairedMojangVersions, VERSION_IDS_INCLUDE_EVEN_IF_NOT_PAIRED)) {
			OrderedVersion mojangLauncherVersion = this.mojangLauncherMetadataProvider.getVersionByVersionID(versionId);
			futureVersionsMap.put(versionId, CompletableFuture.completedFuture(mojangLauncherVersion));
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
			info = info.withUpdatedId(manifestEntry.id());
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
	public List<OrderedVersion> getParentVersions(OrderedVersion mcVersion) {
		return this.mojangLauncherMetadataProvider.getParentVersions(mcVersion);
	}
}
