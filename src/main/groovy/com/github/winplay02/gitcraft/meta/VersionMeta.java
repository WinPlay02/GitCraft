package com.github.winplay02.gitcraft.meta;

import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.google.gson.annotations.JsonAdapter;

import java.util.List;

public record VersionMeta(ArtifactMeta assetIndex, String assets, VersionDownloadsMeta downloads, String id,
						  JavaVersionMeta javaVersion, List<LibraryMeta> libraries, String mainClass,
						  String releaseTime, String time, String type, VersionArguments arguments) {

	public record VersionDownloadsMeta(ArtifactMeta client, ArtifactMeta client_mappings, ArtifactMeta server,
									   ArtifactMeta server_mappings) {

	}

	public record JavaVersionMeta(int majorVersion) {

	}

	public record VersionArguments(List<VersionArgumentWithRules> game, List<VersionArgumentWithRules> jvm) {
	}

	public record VersionArgumentWithRules(
		@JsonAdapter(SerializationHelper.ConvertToList.class) List<String> value,
		List<VersionArgumentRule> rules) {
	}

	public record VersionArgumentRule(String action, VersionArgumentRuleFeatures features, VersionArgumentOS os) {
	}

	public record VersionArgumentRuleFeatures(boolean is_demo_user, boolean has_custom_resolution,
											  boolean has_quick_plays_support, boolean is_quick_play_singleplayer,
											  boolean is_quick_play_multiplayer, boolean is_quick_play_realms) {
	}

	public record VersionArgumentOS(String name, String arch) {
	}
}

