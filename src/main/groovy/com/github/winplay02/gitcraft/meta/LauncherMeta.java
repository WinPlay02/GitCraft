package com.github.winplay02.gitcraft.meta;

import java.util.List;

public record LauncherMeta(List<LauncherVersionEntry> versions) {
	public record LauncherVersionEntry(String id, String type, String url, String time, String releaseTime, String sha1,
									   int complianceLevel) {

	}
}
