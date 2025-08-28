package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.config.Configuration;
import com.google.gson.JsonElement;
import groovy.lang.Tuple2;

import java.util.List;
import java.util.Map;

public record LauncherConfig(
	String username,
	boolean launchDemo,
	Tuple2<Integer, Integer> customResolution,
	String quickPlayPath,
	String quickPlaySingleplayer,
	String quickPlayMultiplayer,
	String quickPlayRealms) implements Configuration  {

	public static final LauncherConfig DEFAULT = new LauncherConfig(
		"TestingUser",
		false,
		null,
		null,
		null,
		null,
		null
	);

	public static LauncherConfig deserialize(Map<String, JsonElement> map) {
		return DEFAULT; // transient, do not deserialize
	}

	@Override
	public Map<String, JsonElement> serialize() {
		return Map.of(); // transient, do not serialize
	}

	@Override
	public List<String> generateInfo() {
		return List.of(
			String.format("Username: %s", this.username()),
			String.format("Launch in demo mode: %s", this.launchDemo()),
			String.format("Custom resolution: %s", this.customResolution() != null ? String.format("%sx%s", this.customResolution().getV1(), this.customResolution().getV2()) : "(not set)"),
			String.format("Quickplay (path, singleplayer, multiplayer, realms): %s, %s, %s, %s", this.quickPlayPath(), this.quickPlaySingleplayer(), this.quickPlayMultiplayer(), this.quickPlayRealms())
		);
	}
}
