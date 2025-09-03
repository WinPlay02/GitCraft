package com.github.winplay02.gitcraft.migration;

import com.github.winplay02.gitcraft.util.GitCraftPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface MetadataStoreUpgrade {
	String sourceVersion();

	String targetVersion();

	void upgrade() throws IOException;

	default List<String> upgradeInfo() {
		return List.of();
	}

	default Path getLostAndFoundDirectory() {
		return GitCraftPaths.LOST_AND_FOUND.resolve(String.format("%s-%s", sourceVersion(), targetVersion()));
	}
}
