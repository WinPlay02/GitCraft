package com.github.winplay02.gitcraft.manifest.vanilla;

import com.github.winplay02.gitcraft.meta.ILauncherMeta;
import com.github.winplay02.gitcraft.meta.ILauncherMetaVersionEntry;

import java.util.List;

public record MinecraftLauncherMeta(LauncherLatestInformation latest,
									List<LauncherVersionEntry> versions) implements ILauncherMeta<MinecraftLauncherMeta.LauncherVersionEntry> {
	public record LauncherLatestInformation(String release, String snapshot) {
	}

	public record LauncherVersionEntry(String id, String type, String url, String time, String releaseTime, String sha1,
									   int complianceLevel) implements ILauncherMetaVersionEntry {

	}
}
