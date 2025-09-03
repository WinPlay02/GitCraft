package com.github.winplay02.gitcraft.config;

import com.google.gson.JsonElement;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GitCraft Transient Application Configuration (not loaded from the configuration file)
 *
 * @param noRepo Whether the committing to the repository step should be skipped
 * @param overrideRepositoryPath Path to a repository that should be used instead of the calculated repository path
 * @param refreshDecompilation Whether existing artifacts should be deleted and generated (useful, e.g. if there are decompiler updates)
 * @param refreshOnlyVersion Whether a specific versions should be refreshed
 * @param refreshMinVersion A min version that should be refreshed (all versions greater than this version are also refreshed)
 * @param refreshMaxVersion A max version that should be refreshed (all versions less than this version are also refreshed)
 */
public record TransientApplicationConfiguration(boolean noRepo,
												Path overrideRepositoryPath,
												boolean refreshDecompilation,
												String[] refreshOnlyVersion,
												String refreshMinVersion,
												String refreshMaxVersion)
	implements Configuration {

	public static final TransientApplicationConfiguration DEFAULT = new TransientApplicationConfiguration(
		false,
		null,
		false,
		null,
		null,
		null
	);

	@Override
	public Map<String, JsonElement> serialize() {
		return Map.of(); // transient, do not serialize
	}

	@Override
	public List<String> generateInfo() {
		List<String> info = new ArrayList<>();
		info.add(String.format("Repository creation and versioning is: %s", this.noRepo() ? "skipped" : "enabled"));
		if (this.overrideRepositoryPath() != null) {
			info.add(String.format("Repository path is overridden. This may lead to various errors (see help). Proceed with caution. Target: %s", this.overrideRepositoryPath()));
		}
		if (this.refreshDecompilation() && !this.isRefreshOnlyVersion() && !this.isRefreshMinVersion() && !this.isRefreshMaxVersion()) {
			info.add(String.format("All / specified version(s) will be: %s", this.refreshDecompilation() ? "deleted and decompiled again" : "reused if existing"));
		} else {
			if (this.isRefreshOnlyVersion()) {
				info.add(String.format("Versions to refresh artifacts: %s", String.join(", ", this.refreshOnlyVersion())));
			} else if (this.isRefreshMinVersion() && this.isRefreshMaxVersion()) {
				info.add(String.format("Versions to refresh artifacts: from %s to %s", this.refreshMinVersion(), this.refreshMaxVersion()));
			} else if (this.isRefreshMinVersion()) {
				info.add(String.format("Versions to refresh artifacts: all from %s", this.refreshMinVersion()));
			} else if (this.isRefreshMaxVersion()) {
				info.add(String.format("Versions to refresh artifacts: all up to %s", this.refreshMaxVersion()));
			}
		}
		return info;
	}

	public boolean isRefreshOnlyVersion() {
		return this.refreshOnlyVersion() != null;
	}

	public boolean isRefreshMinVersion() {
		return this.refreshMinVersion() != null;
	}

	public boolean isRefreshMaxVersion() {
		return this.refreshMaxVersion() != null;
	}

	public static TransientApplicationConfiguration deserialize(Map<String, JsonElement> map) {
		return DEFAULT; // transient, do not deserialize
	}
}
