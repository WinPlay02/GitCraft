package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.GitCraftQuirks;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.FabricLoaderImpl;

public class FabricHelper {
	public static void checkFabricLoaderVersion() {
		try {
			SemanticVersion actualFabricLoaderVersion = SemanticVersion.parse(FabricLoaderImpl.VERSION);
			SemanticVersion minRequiredVersion = SemanticVersion.parse(GitCraftQuirks.MIN_SUPPORTED_FABRIC_LOADER);
			if (actualFabricLoaderVersion.compareTo((Version) minRequiredVersion) < 0) {
				MiscHelper.panic("Fabric loader is out of date. Min required version: %s, Actual provided version: %s", GitCraftQuirks.MIN_SUPPORTED_FABRIC_LOADER, FabricLoaderImpl.VERSION);
			}
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}
}
