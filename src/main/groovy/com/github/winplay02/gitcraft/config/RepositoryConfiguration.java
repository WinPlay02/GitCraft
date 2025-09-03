package com.github.winplay02.gitcraft.config;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

import static com.github.winplay02.gitcraft.config.Configuration.Utils.prim;

/**
 * Repository Configuration
 *
 * @param gitUser Name of User-Identity used for git
 * @param gitMail Mail of User-Identity used for git
 * @param gitMainlineLinearBranch Branch name used for 'mainline' versions
 * @param createVersionBranches Whether branches should be created for all versions
 * @param createStableVersionBranches Whether branches should be created for stable versions
 * @param gcAfterRun Whether garbage-collection should be run after completing a run
 */
public record RepositoryConfiguration(String gitUser,
									  String gitMail,
									  String gitMainlineLinearBranch,
									  boolean createVersionBranches,
									  boolean createStableVersionBranches,
									  boolean gcAfterRun)
	implements Configuration {

	public static final RepositoryConfiguration DEFAULT = new RepositoryConfiguration(
		"Mojang",
		"gitcraft@decompiled.mc",
		"master",
		false,
		false,
		true
	);

	@Override
	public Map<String, JsonElement> serialize() {
		return Map.of(
			"gitUser", prim(this.gitUser()),
			"gitMail", prim(this.gitMail()),
			"gitMainlineLinearBranch", prim(this.gitMainlineLinearBranch()),
			"createVersionBranches", prim(this.createVersionBranches()),
			"createStableVersionBranches", prim(this.createStableVersionBranches()),
			"gcAfterRun", prim(this.gcAfterRun())
		);
	}

	@Override
	public List<String> generateInfo() {
		if (createVersionBranches) {
			return List.of("A separate branch will be created for each version.");
		} else if (createStableVersionBranches) {
			return List.of("A separate branch will be created for each stable version.");
		}
		return List.of();
	}

	public static RepositoryConfiguration deserialize(Map<String, JsonElement> map) {
		return new RepositoryConfiguration(
			Utils.getString(map, "gitUser", DEFAULT.gitUser()),
			Utils.getString(map, "gitMail", DEFAULT.gitMail()),
			Utils.getString(map, "gitMainlineLinearBranch", DEFAULT.gitMainlineLinearBranch()),
			Utils.getBoolean(map, "createVersionBranches", DEFAULT.createVersionBranches()),
			Utils.getBoolean(map, "createStableVersionBranches", DEFAULT.createStableVersionBranches()),
			Utils.getBoolean(map, "gcAfterRun", DEFAULT.gcAfterRun())
		);
	}
}
