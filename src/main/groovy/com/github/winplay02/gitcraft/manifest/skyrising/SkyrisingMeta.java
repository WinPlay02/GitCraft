package com.github.winplay02.gitcraft.manifest.skyrising;

import com.github.winplay02.gitcraft.meta.ILauncherMeta;
import com.github.winplay02.gitcraft.meta.ILauncherMetaVersionEntry;

import java.time.ZonedDateTime;
import java.util.List;

public record SkyrisingMeta(SkyrisingLatestInformation latest,
							List<SkyrisingVersionEntry> versions) implements ILauncherMeta<SkyrisingMeta.SkyrisingVersionEntry> {

	public record SkyrisingLatestInformation(String old_alpha, String classic_server, String alpha_server,
											 String old_beta, String release, String snapshot, String pending) {
	}

	public record SkyrisingVersionEntry(String id, String type, String url, ZonedDateTime time,
										ZonedDateTime releaseTime,
										String details) implements ILauncherMetaVersionEntry {

	}
}
