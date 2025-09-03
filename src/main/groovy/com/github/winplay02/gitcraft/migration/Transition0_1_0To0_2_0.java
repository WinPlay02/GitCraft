package com.github.winplay02.gitcraft.migration;

import com.github.winplay02.gitcraft.util.GitCraftPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class Transition0_1_0To0_2_0 implements MetadataStoreUpgrade {
	@Override
	public String sourceVersion() {
		return "0.1.0";
	}

	@Override
	public String targetVersion() {
		return "0.2.0";
	}

	@Override
	public void upgrade() throws IOException {
		if (Files.exists(GitCraftPaths.LEGACY_METADATA_STORE)) {
			Files.delete(GitCraftPaths.LEGACY_METADATA_STORE);
		}
	}

	@Override
	public List<String> upgradeInfo() {
		return List.of(
			"WARNING: There were breaking changes to existing repos",
			"- More known versions are automatically downloaded (like experimental snapshots)",
			"- Commits may now be merges of multiple previous versions",
			"- Datagen is executed by default (registry reports, NBT -> SNBT)",
			"- Vanilla worldgen datapack is now downloaded for versions, where there are no other ways of obtaining these files",
			"- Comments are enabled for yarn generation",
			"- Constant unpicking is now done for yarn generation",
			"More information in --help"
		);
	}

}
