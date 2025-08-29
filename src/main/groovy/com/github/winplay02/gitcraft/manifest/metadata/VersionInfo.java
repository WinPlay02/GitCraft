package com.github.winplay02.gitcraft.manifest.metadata;

import com.github.winplay02.gitcraft.util.SerializationTypes;
import com.google.gson.annotations.JsonAdapter;

import java.time.ZonedDateTime;
import java.util.List;

public record VersionInfo(ArtifactMetadata assetIndex, String assets, Downloads downloads, String id,
						  JavaVersion javaVersion, List<LibraryMetadata> libraries, String mainClass,
						  ZonedDateTime releaseTime, ZonedDateTime time, String type, VersionArguments arguments,
						  String minecraftArguments) {
	public record Downloads(ArtifactMetadata client, ArtifactMetadata client_mappings, ArtifactMetadata server,
							ArtifactMetadata server_mappings, ArtifactMetadata windows_server, ArtifactMetadata server_zip) {
	}

	public record JavaVersion(int majorVersion) {
	}

	public VersionInfo withUpdatedId(String versionId) {
		if (this.id().equals(versionId)) {
			return this;
		}
		return new VersionInfo(this.assetIndex(), this.assets(), this.downloads(), versionId, this.javaVersion(), this.libraries(), this.mainClass(), this.releaseTime(), this.time(), this.type(), this.arguments(), this.minecraftArguments());
	}

	public record VersionArguments(List<VersionArgumentWithRules> game, List<VersionArgumentWithRules> jvm) {
	}

	public record VersionArgumentWithRules(
		@JsonAdapter(SerializationTypes.ConvertToList.class) List<String> value,
		List<VersionArgumentRule> rules) {
	}

	public record VersionArgumentRule(String action, VersionArgumentRuleFeatures features, VersionArgumentOS os) {
	}

	public record VersionArgumentRuleFeatures(boolean is_demo_user, boolean has_custom_resolution,
											  boolean has_quick_plays_support, boolean is_quick_play_singleplayer,
											  boolean is_quick_play_multiplayer, boolean is_quick_play_realms) {
		public static final VersionArgumentRuleFeatures EMPTY = new VersionArgumentRuleFeatures(false, false, false, false, false, false);
	}

	public record VersionArgumentOS(String name, String version, String arch) {
	}
}

