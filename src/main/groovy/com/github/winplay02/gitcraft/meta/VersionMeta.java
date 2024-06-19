package com.github.winplay02.gitcraft.meta;

import com.github.winplay02.gitcraft.util.MiscHelper;

import java.time.ZonedDateTime;
import java.util.List;

public record VersionMeta(ArtifactMeta assetIndex, String assets, VersionDownloadsMeta downloads, String id,
						  JavaVersionMeta javaVersion, List<LibraryMeta> libraries, String mainClass,
						  ZonedDateTime releaseTime, ZonedDateTime time, String type) {

	public static VersionMeta merge(String versionId, List<VersionMeta> metaSources) {
		return new VersionMeta(
			MiscHelper.mergeEqualOrFallbackToFirst(metaSources, VersionMeta::assetIndex),
			MiscHelper.mergeEqualOrNull(metaSources, VersionMeta::assets),
			new VersionDownloadsMeta(
				MiscHelper.mergeEqualOrFallbackToFirst(metaSources, MiscHelper.chain(VersionMeta::downloads, VersionDownloadsMeta::client)),
				MiscHelper.mergeEqualOrFallbackToFirst(metaSources, MiscHelper.chain(VersionMeta::downloads, VersionDownloadsMeta::client_mappings)),
				MiscHelper.mergeEqualOrFallbackToFirst(metaSources, MiscHelper.chain(VersionMeta::downloads, VersionDownloadsMeta::server)),
				MiscHelper.mergeEqualOrFallbackToFirst(metaSources, MiscHelper.chain(VersionMeta::downloads, VersionDownloadsMeta::server_mappings)),
				MiscHelper.mergeEqualOrNull(metaSources, MiscHelper.chain(VersionMeta::downloads, VersionDownloadsMeta::windows_server)),
				MiscHelper.mergeEqualOrNull(metaSources, MiscHelper.chain(VersionMeta::downloads, VersionDownloadsMeta::server_zip))
			),
			versionId,
			MiscHelper.mergeEqualOrNull(metaSources, VersionMeta::javaVersion),
			MiscHelper.mergeListDistinctValues(metaSources, VersionMeta::libraries),
			MiscHelper.mergeEqualOrNull(metaSources, VersionMeta::mainClass),
			(ZonedDateTime) MiscHelper.mergeMaxOrNull(metaSources, VersionMeta::releaseTime),
			(ZonedDateTime) MiscHelper.mergeMaxOrNull(metaSources, VersionMeta::time),
			MiscHelper.mergeEqualOrNull(metaSources, VersionMeta::type)
		);
	}

	public record VersionDownloadsMeta(ArtifactMeta client, ArtifactMeta client_mappings, ArtifactMeta server,
									   ArtifactMeta server_mappings, ArtifactMeta windows_server, ArtifactMeta server_zip) {

	}

	public record JavaVersionMeta(int majorVersion) {

	}
}

