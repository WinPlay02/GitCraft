package com.github.winplay02.gitcraft.migration;

import com.github.winplay02.gitcraft.LibraryPaths;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Transition0_2_0_To0_3_0 implements MetadataStoreUpgrade {
	@Override
	public String sourceVersion() {
		return "0.2.0";
	}

	@Override
	public String targetVersion() {
		return "0.3.0";
	}

	@Override
	public void upgrade() throws IOException {
		Path extraVersionsDir = LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve("extra-versions");
		Path targetExtraVersionsDir = GitCraftPaths.FILESYSTEM_ROOT.getMcExtraVersionStore().resolve("mojang-launcher");
		// Move from artifact-store to lost-and-found
		if (Files.exists(targetExtraVersionsDir) && !MiscHelper.isDirectoryEmpty(targetExtraVersionsDir)) {
			Path lostAndFoundDirectory = this.getLostAndFoundDirectory();
			Files.createDirectories(lostAndFoundDirectory);
			MiscHelper.moveLargeDir(targetExtraVersionsDir, lostAndFoundDirectory.resolve("extra-versions"));
			MiscHelper.println("Existing directory '%s' was moved to lost-and-found", targetExtraVersionsDir.relativize(GitCraftPaths.FILESYSTEM_ROOT.getRoot()));
		}
		// Try moving first
		MiscHelper.moveLargeDir(extraVersionsDir, targetExtraVersionsDir);
	}

	@Override
	public List<String> upgradeInfo() {
		return MetadataStoreUpgrade.super.upgradeInfo();
	}
}
