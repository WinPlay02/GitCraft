package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.GitCraftTestingFs;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.manifest.vanilla.MinecraftLauncherManifest;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({GitCraftTestingFs.class})
public class ManifestTest {
	@Test
	public void metadataBootstrapDefaultMeta() throws IOException {
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.deleteIfExists(metadataBootstrap.getSemverCachePath());
		metadataBootstrap = new MinecraftLauncherManifest();
		assertTrue(metadataBootstrap.semverCache.isEmpty());
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), metadataBootstrap.getSemverCachePath());
		metadataBootstrap = new MinecraftLauncherManifest();
		metadataBootstrap.loadSemverCache();
		assertFalse(metadataBootstrap.semverCache.isEmpty());
		metadataBootstrap.semverCache.remove("1.20");
		metadataBootstrap.semverCache.remove("rd-132328");
		metadataBootstrap.singleMetaUrls.clear();
		metadataBootstrap.getVersionMeta();
		assertTrue(metadataBootstrap.versionMeta.containsKey("1.20"));
		assertTrue(metadataBootstrap.versionMeta.containsKey("rd-132328"));
		assertEquals("0.0.0-rd.132328", metadataBootstrap.versionMeta.get("rd-132328").semanticVersion());
	}

	@Test
	public void metadataSemverCacheTest() throws IOException {
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), metadataBootstrap.getSemverCachePath(), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		metadataBootstrap.loadSemverCache();
		MinecraftVersionGraph versionGraphComplete = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
		assertNotNull(versionGraphComplete);
	}
}
