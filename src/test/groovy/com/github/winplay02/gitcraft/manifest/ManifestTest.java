package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.GitCraftTestingFs;
import com.github.winplay02.gitcraft.LibraryPaths;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.manifest.historic.HistoricMojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.manifest.skyrising.SkyrisingMetadataProvider;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({GitCraftTestingFs.class})
public class ManifestTest {
	@Test
	public void metadataBootstrapDefaultMeta() throws IOException {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.deleteIfExists(metadataBootstrap.getSemverCachePath());
		metadataBootstrap = new MojangLauncherMetadataProvider();
		assertTrue(metadataBootstrap.semverCache.isEmpty());
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), metadataBootstrap.getSemverCachePath());
		metadataBootstrap = new MojangLauncherMetadataProvider();
		metadataBootstrap.loadSemverCache();
		assertFalse(metadataBootstrap.semverCache.isEmpty());
		metadataBootstrap.semverCache.remove("1.20");
		metadataBootstrap.semverCache.remove("rd-132328");
		metadataBootstrap.metadataSources.clear();
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			metadataBootstrap.getVersions(executor);
		}
		assertTrue(metadataBootstrap.versionsById.containsKey("1.20"));
		assertTrue(metadataBootstrap.versionsById.containsKey("rd-132328"));
		assertEquals("0.0.0-rd.132328", metadataBootstrap.versionsById.get("rd-132328").semanticVersion());
	}

	@Test
	public void metadataBootstrapHistoricLauncherMeta() throws IOException {
		HistoricMojangLauncherMetadataProvider metadataBootstrap = new HistoricMojangLauncherMetadataProvider(new MojangLauncherMetadataProvider(), new SkyrisingMetadataProvider());
		Files.deleteIfExists(metadataBootstrap.getSemverCachePath());
		metadataBootstrap = new HistoricMojangLauncherMetadataProvider(new MojangLauncherMetadataProvider(), new SkyrisingMetadataProvider());
		assertTrue(metadataBootstrap.semverCache.isEmpty());
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), metadataBootstrap.getSemverCachePath());
		MojangLauncherMetadataProvider mojangProvider = new MojangLauncherMetadataProvider();
		metadataBootstrap = new HistoricMojangLauncherMetadataProvider(mojangProvider, new SkyrisingMetadataProvider());
		metadataBootstrap.loadSemverCache();
		// assertFalse(metadataBootstrap.semverCache.isEmpty());
		metadataBootstrap.semverCache.remove("1.20");
		metadataBootstrap.semverCache.remove("rd-132328");
		metadataBootstrap.metadataSources.clear();
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			metadataBootstrap.getVersions(executor);
		}
		assertFalse(metadataBootstrap.versionsById.isEmpty());
		assertTrue(metadataBootstrap.versionsById.containsKey("1.20"));
		assertTrue(metadataBootstrap.versionsById.size() < mojangProvider.versionsById.size());
		assertEquals("fcc539e68fc40b9e89708f245468dd58e66fe35d", metadataBootstrap.versionsById.get("1.20").assetsIndex().sha1sum()); // oldest known
		assertNotEquals(mojangProvider.versionsById.get("1.20").assetsIndex().sha1sum(), metadataBootstrap.versionsById.get("1.20").assetsIndex().sha1sum());
	}

	@Test
	public void metadataBootstrapSkyrisingMeta() throws IOException {
		SkyrisingMetadataProvider metadataBootstrap = new SkyrisingMetadataProvider();
		Files.deleteIfExists(metadataBootstrap.getSemverCachePath());
		metadataBootstrap = new SkyrisingMetadataProvider();
		assertTrue(metadataBootstrap.semverCache.isEmpty());
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), metadataBootstrap.getSemverCachePath());
		metadataBootstrap = new SkyrisingMetadataProvider();
		metadataBootstrap.loadSemverCache();
		assertFalse(metadataBootstrap.semverCache.isEmpty());
		metadataBootstrap.semverCache.remove("11w47a");
		metadataBootstrap.semverCache.remove("1.0.1");
		metadataBootstrap.metadataSources.clear();
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			metadataBootstrap.getVersions(executor);
		}
		assertTrue(metadataBootstrap.versionsById.containsKey("11w47a"));
		assertTrue(metadataBootstrap.versionsById.containsKey("1.0.1"));
		assertEquals("1.1.0-alpha.11.47.a", metadataBootstrap.versionsById.get("11w47a").semanticVersion());
		assertTrue(metadataBootstrap.getParentVersion(metadataBootstrap.versionsById.get("11w47a")).containsAll(List.of("1.0.0", "1.0.1")));
	}

	@Test
	public void metadataSemverCacheTest() throws IOException {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), metadataBootstrap.getSemverCachePath(), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MojangLauncherMetadataProvider();
		metadataBootstrap.loadSemverCache();
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			MinecraftVersionGraph versionGraphComplete = MinecraftVersionGraph.createFromMetadata(executor, metadataBootstrap);
			assertNotNull(versionGraphComplete);
		}
	}
}
