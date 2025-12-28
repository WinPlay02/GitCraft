package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.config.ApplicationConfiguration;
import com.github.winplay02.gitcraft.config.Configuration;
import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.skyrising.SkyrisingMetadataProvider;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.unpick.UnpickFlavour;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({GitCraftTestingFs.class})
@TestMethodOrder(MethodOrderer.MethodName.class)
public class GitCraftTest {

	@Test
	public void integrity() {
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryGitHub(executor, "WinPlay02/GitCraft", "master", "settings.gradle", new FileSystemNetworkManager.LocalFileInfo(LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve("settings.gradle"), null, null, "testing file", "settings"));
		}
		assertTrue(Library.IA_SHA1.fileMatchesChecksum(LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve("settings.gradle"), "b07625411efd4329f9f639bfca2068f92997d1b3"));
	}

	@Test
	public void versionGraphFilter() throws IOException {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph versionGraphComplete;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			versionGraphComplete = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		MinecraftVersionGraph vgSnapshots = versionGraphComplete.filterSnapshots();
		MinecraftVersionGraph vgStable = versionGraphComplete.filterStableRelease();
		assertEquals(versionGraphComplete.stream().count(), vgSnapshots.stream().count() + vgStable.stream().count());
		assertTrue(vgSnapshots.stream().count() < versionGraphComplete.stream().count());
		assertTrue(vgStable.stream().count() < versionGraphComplete.stream().count());
		assertNotNull(vgStable.getMinecraftVersionByName("1.20"));
		assertTrue(vgStable.containsVersion(versionGraphComplete.getMinecraftVersionBySemanticVersion("1.20")));
		assertFalse(vgStable.containsVersion(versionGraphComplete.getMinecraftVersionBySemanticVersion("1.20-rc.1")));
		assertNull(vgSnapshots.getMinecraftVersionByName("1.20"));
		assertTrue(vgSnapshots.containsVersion(versionGraphComplete.getMinecraftVersionBySemanticVersion("1.20-rc.1")));
		assertNotNull(versionGraphComplete.getMinecraftVersionBySemanticVersion("1.20-rc.1"));
		assertEquals("1.14.4", versionGraphComplete.filterMapping(MappingFlavour.MOJMAP, new MappingFlavour[0]).getMainRootVersion().launcherFriendlyVersionName());
		assertEquals("21w11a", versionGraphComplete.filterUnpick(UnpickFlavour.YARN, new UnpickFlavour[0]).getMainRootVersion().launcherFriendlyVersionName());
		assertEquals(versionGraphComplete.getMinecraftVersionByName("1.20"), versionGraphComplete.filterMinVersion(versionGraphComplete.getMinecraftVersionByName("1.20")).getMainRootVersion());
		MinecraftVersionGraph onlyVersionGraph = versionGraphComplete.filterOnlyVersion(versionGraphComplete.getMinecraftVersionByName("1.20"), versionGraphComplete.getMinecraftVersionByName("1.19"));
		assertEquals(versionGraphComplete.getMinecraftVersionByName("1.19"), onlyVersionGraph.getMainRootVersion());
		assertEquals(2L, onlyVersionGraph.stream().count());
		MinecraftVersionGraph excludeVersionGraph = versionGraphComplete.filterExcludeVersion(versionGraphComplete.getMinecraftVersionByName("1.20"), versionGraphComplete.getMinecraftVersionByName("1.19"));
		assertFalse(excludeVersionGraph.containsVersion(versionGraphComplete.getMinecraftVersionByName("1.19")));
		assertFalse(excludeVersionGraph.containsVersion(versionGraphComplete.getMinecraftVersionByName("1.20")));
		assertEquals(versionGraphComplete, versionGraphComplete.filterExcludeVersion());
		MinecraftVersionGraph minMaxVersionGraph = versionGraphComplete.filterMinVersion(versionGraphComplete.getMinecraftVersionByName("1.19")).filterMaxVersion(versionGraphComplete.getMinecraftVersionByName("1.19"));
		assertEquals(1L, minMaxVersionGraph.stream().count());
		MinecraftVersionGraph mainlineVersionGraph = versionGraphComplete.filterMainlineVersions();
		assertTrue(mainlineVersionGraph.stream().allMatch(mainlineVersionGraph::isOnMainBranch));
	}

	@Test
	public void mappingsMojang() throws IOException, URISyntaxException, InterruptedException {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
			//
			Path mappingsPath = MappingFlavour.MOJMAP.getPath(versionGraph.getMinecraftVersionByName("1.20"), MinecraftJar.CLIENT).get();
			Files.deleteIfExists(mappingsPath);
			assertFalse(Files.exists(mappingsPath));
			assertTrue(MappingFlavour.MOJMAP.exists(versionGraph.getMinecraftVersionByName("1.14.4")));
			assertFalse(MappingFlavour.MOJMAP.exists(versionGraph.getMinecraftVersionByName("1.12")));
			IStepContext.SimpleStepContext<OrderedVersion> context = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("1.20"), executor);
			assertEquals(StepStatus.SUCCESS, MappingFlavour.MOJMAP.provide(context, MinecraftJar.CLIENT));
			assertEquals(StepStatus.SUCCESS, MappingFlavour.MOJMAP.provide(context, MinecraftJar.SERVER));
			assertEquals(StepStatus.NOT_RUN, MappingFlavour.MOJMAP.provide(context, MinecraftJar.MERGED));
			assertTrue(Files.exists(mappingsPath));
			assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.MOJMAP.provide(context, MinecraftJar.CLIENT));
			assertTrue(Files.size(mappingsPath) > 0);
			assertFalse(MappingFlavour.MOJMAP.supportsComments());
			assertFalse(MappingFlavour.MOJMAP.supportsConstantUnpicking());
			assertNotNull(MappingFlavour.MOJMAP.getProvider(versionGraph.getMinecraftVersionByName("1.20"), MinecraftJar.CLIENT));
			assertEquals(MappingsNamespace.OFFICIAL.toString(), MappingFlavour.MOJMAP.getSourceNS());
			assertEquals(MappingsNamespace.NAMED.toString(), MappingFlavour.MOJMAP.getDestinationNS());
		}
	}

	@Test
	public void mappingsParchment() throws IOException, URISyntaxException, InterruptedException {
		MojangLauncherMetadataProvider metadataBootstrap1 = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap1.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap1.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
			//
			Path mappingsPath = MappingFlavour.MOJMAP_PARCHMENT.getPath(versionGraph.getMinecraftVersionByName("1.20.1"), MinecraftJar.MERGED).orElse(null);
			assertNotNull(mappingsPath);
			assertFalse(Files.exists(mappingsPath));
			assertTrue(MappingFlavour.MOJMAP_PARCHMENT.exists(versionGraph.getMinecraftVersionByName("1.16.5")));
			assertFalse(MappingFlavour.MOJMAP_PARCHMENT.exists(versionGraph.getMinecraftVersionByName("1.14.4")));
			IStepContext.SimpleStepContext<OrderedVersion> context = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("1.20.1"), executor);
			assertEquals(StepStatus.SUCCESS, MappingFlavour.MOJMAP_PARCHMENT.provide(context, MinecraftJar.MERGED));
			assertTrue(Files.exists(mappingsPath));
			assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.MOJMAP_PARCHMENT.provide(context, MinecraftJar.MERGED));
			assertTrue(Files.size(mappingsPath) > 0);
			assertTrue(MappingFlavour.MOJMAP_PARCHMENT.supportsComments());
			assertFalse(MappingFlavour.MOJMAP_PARCHMENT.supportsConstantUnpicking());
			assertEquals(MappingsNamespace.OFFICIAL.toString(), MappingFlavour.MOJMAP_PARCHMENT.getSourceNS());
			assertEquals(MappingsNamespace.NAMED.toString(), MappingFlavour.MOJMAP_PARCHMENT.getDestinationNS());
		}
	}

	@Test
	public void mappingsFabricIntermediary() throws IOException, URISyntaxException, InterruptedException {
		MojangLauncherMetadataProvider metadataBootstrap1 = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap1.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap1.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
			//
			Path mappingsPath = MappingFlavour.FABRIC_INTERMEDIARY.getPath(versionGraph.getMinecraftVersionByName("1.20"), MinecraftJar.MERGED).orElse(null);
			assertNotNull(mappingsPath);
			assertFalse(Files.exists(mappingsPath));
			assertTrue(MappingFlavour.FABRIC_INTERMEDIARY.exists(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.18.43.b")));
			assertFalse(MappingFlavour.FABRIC_INTERMEDIARY.exists(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.18.43.a")));
			IStepContext.SimpleStepContext<OrderedVersion> context = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("1.20"), executor);
			assertEquals(StepStatus.SUCCESS, MappingFlavour.FABRIC_INTERMEDIARY.provide(context, MinecraftJar.MERGED));
			assertTrue(Files.exists(mappingsPath));
			assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.FABRIC_INTERMEDIARY.provide(context, MinecraftJar.MERGED));
			assertTrue(Files.size(mappingsPath) > 0);
			assertFalse(MappingFlavour.FABRIC_INTERMEDIARY.supportsComments());
			assertFalse(MappingFlavour.FABRIC_INTERMEDIARY.supportsConstantUnpicking());
			assertEquals(MappingsNamespace.OFFICIAL.toString(), MappingFlavour.FABRIC_INTERMEDIARY.getSourceNS());
			assertEquals(MappingsNamespace.INTERMEDIARY.toString(), MappingFlavour.FABRIC_INTERMEDIARY.getDestinationNS());
		}
	}

	@Test
	public void mappingsYarn() throws IOException, URISyntaxException, InterruptedException {
		MojangLauncherMetadataProvider metadataBootstrap1 = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap1.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap1.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
			//
			Path mappingsPath = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionByName("1.20"), MinecraftJar.MERGED).orElse(null);
			assertNotNull(mappingsPath);
			assertFalse(Files.exists(mappingsPath));
			assertTrue(MappingFlavour.YARN.exists(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.18.49.a")));
			assertFalse(MappingFlavour.YARN.exists(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.18.48.b")));
			IStepContext.SimpleStepContext<OrderedVersion> context = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("1.20"), executor);
			assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context, MinecraftJar.MERGED));
			assertTrue(Files.exists(mappingsPath));
			assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context, MinecraftJar.MERGED));
			assertTrue(Files.size(mappingsPath) > 0);
			// Broken Versions
			assertFalse(MappingFlavour.YARN.exists(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.19.13.a")));
			assertFalse(MappingFlavour.YARN.exists(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.19.13.b")));
			assertFalse(MappingFlavour.YARN.exists(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.19.14.a")));
			assertFalse(MappingFlavour.YARN.exists(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.19.14.b")));
			//
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionByName("1.14.2"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("1.14.2"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionByName("1.14.3"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("1.14.3"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionByName("1.14.4"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("1.14.4"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionByName("1.19"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("1.19"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionBySemanticVersion("1.14.2-rc.1"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionBySemanticVersion("1.14.2-rc.1"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionByName("19w04b"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("19w04b"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionByName("19w08a"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("19w08a"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionByName("19w12b"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionByName("19w12b"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionBySemanticVersion("1.15.2-rc.2.combat.5"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionBySemanticVersion("1.15.2-rc.2.combat.5"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionBySemanticVersion("1.16.2-beta.3.combat.6"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionBySemanticVersion("1.16.2-beta.3.combat.6"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			{
				Path mappingsPathTest = MappingFlavour.YARN.getPath(versionGraph.getMinecraftVersionBySemanticVersion("1.14.5-combat.2"), MinecraftJar.MERGED).orElse(null);
				assertNotNull(mappingsPathTest);
				IStepContext.SimpleStepContext<OrderedVersion> context1 = new IStepContext.SimpleStepContext<>(null, versionGraph, versionGraph.getMinecraftVersionBySemanticVersion("1.14.5-combat.2"), executor);
				assertEquals(StepStatus.SUCCESS, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
				assertTrue(Files.exists(mappingsPathTest));
				assertEquals(StepStatus.UP_TO_DATE, MappingFlavour.YARN.provide(context1, MinecraftJar.MERGED));
			}
			assertTrue(MappingFlavour.YARN.supportsComments());
			assertTrue(MappingFlavour.YARN.supportsConstantUnpicking());
			assertEquals(MappingsNamespace.OFFICIAL.toString(), MappingFlavour.YARN.getSourceNS());
			assertEquals(MappingsNamespace.NAMED.toString(), MappingFlavour.YARN.getDestinationNS());
		}
	}

	protected static RevCommit findCommit(RepoWrapper repoWrapper, OrderedVersion mcVersion) throws IOException, GitAPIException {
		Iterator<RevCommit> iterator = repoWrapper.getGit().log().all().setRevFilter(new RepoWrapper.CommitMsgFilter(mcVersion.toCommitMessage())).call().iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		}
		return null;
	}

	@Test
	public void pipeline() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=1.17.1-pre1,1.18_experimental-snapshot-1,21w37a,1.18,22w13oneblockatatime"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef(GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()));
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef("1.18_experimental-snapshot-1"));
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef("22w13oneblockatatime"));
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("22w13oneblockatatime"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18"))).getParentCount());
			assertEquals(2, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("21w37a"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18_experimental-snapshot-1"))).getParentCount());
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1-pre1"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1-pre1")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/exp-vanilla-worldgen", targetCommit.getTree())) {
				assertNotNull(walk); // Exists for this version
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/src", targetCommit.getTree())) {
				assertNotNull(walk);
			}
		}
	}

	@Test
	public void pipelineOldAlpha() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=a1.2.6", "--mappings=identity_unmapped"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef(GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()));
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("a1.2.6"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("a1.2.6")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/terrain.png", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/com", targetCommit.getTree())) { //
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/exp-vanilla-worldgen", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/client", targetCommit.getTree())) {
				assertNotNull(walk);
			}
		}
	}

	@Test
	public void pipelineOldSnapshotSkyrising() throws Exception {
		SkyrisingMetadataProvider metadataBootstrap = new SkyrisingMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.SKYRISING));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--manifest-source=skyrising", "--only-version=11w47a", "--mappings=identity_unmapped"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef(GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()));
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("11w47a"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("11w47a")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/terrain.png", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/com", targetCommit.getTree())) { //
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/exp-vanilla-worldgen", targetCommit.getTree())) {
				assertNull(walk);
			}
			// split version
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/client", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/server", targetCommit.getTree())) {
				assertNotNull(walk);
			}
		}
	}

	@Test
	public void pipelineOldSnapshotSkyrisingOrnithe() throws Exception {
		SkyrisingMetadataProvider metadataBootstrap = new SkyrisingMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.SKYRISING));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--manifest-source=skyrising", "--only-version=11w47a", "--mappings=feather", "--patch-lvt", "--preening-enabled", "--nests=ornithe_nests", "--signatures=sparrow", "--exceptions=raven"});
		assertEquals(MappingFlavour.FEATHER, GitCraft.getApplicationConfiguration().usedMapping());
		assertTrue(GitCraft.getApplicationConfiguration().patchLvt());
		assertTrue(GitCraft.getApplicationConfiguration().enablePreening());
		assertEquals(NestsFlavour.ORNITHE_NESTS, GitCraft.getApplicationConfiguration().usedNests());
		assertEquals(SignaturesFlavour.SPARROW, GitCraft.getApplicationConfiguration().usedSignatures());
		assertEquals(ExceptionsFlavour.RAVEN, GitCraft.getApplicationConfiguration().usedExceptions());
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef(GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()));
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("11w47a"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("11w47a")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/terrain.png", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/com", targetCommit.getTree())) { //
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/exp-vanilla-worldgen", targetCommit.getTree())) {
				assertNull(walk);
			}
			// split version
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/client", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/server", targetCommit.getTree())) {
				assertNotNull(walk);
			}
		}
	}

	@Test
	public void pipelineReset() throws Exception {
		Configuration.reset();
		GitCraft.main(new String[]{"--only-version=1.17.1-pre1,1.18_experimental-snapshot-1,21w37a,1.18,22w13oneblockatatime", "--refresh", "--refresh-min-version=1.18"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef(GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()));
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef("1.18_experimental-snapshot-1"));
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef("22w13oneblockatatime"));
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("22w13oneblockatatime"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18"))).getParentCount());
			assertEquals(2, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("21w37a"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18_experimental-snapshot-1"))).getParentCount());
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1-pre1"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1-pre1")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/src", targetCommit.getTree())) {
				assertNotNull(walk);
			}
		}
	}

	@Test
	public void pipelineNoAssets() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=1.17.1", "--no-assets"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
				assertNull(walk);
			}
		}
	}

	@Test
	public void pipelineExternalAssets() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=21w37a", "--no-external-assets"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("21w37a"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("21w37a")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
				assertNull(walk);
			}
		}
	}

	@Test
	public void pipelineDatapack() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=1.18", "--no-datapack"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNull(walk);
			}
		}
	}

	@Test
	public void pipelineDatapackReset() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=1.18", "--no-datapack", "--refresh"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNull(walk);
			}
		}
	}

	@Test
	public void pipelineMinMaxExclude() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--min-version=1.20-rc1", "--max-version=1.20", "--exclude-version=1.20"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.20-rc1"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.20-rc1")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/src", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/exp-vanilla-worldgen", targetCommit.getTree())) {
				assertNull(walk); // Does not exist for this version
			}
		}
	}

	@Test
	public void pipelineNoDatagenSnbt() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=1.20-rc1", "--no-datagen-snbt"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.20-rc1"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.20-rc1")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
				assertNotNull(walk);
			}
		}
	}

	@Test
	public void pipelineNoDatagenRegistryReports() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=1.18_experimental-snapshot-1", "--no-datagen-report"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18_experimental-snapshot-1"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18_experimental-snapshot-1")));
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
				assertNotNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
				assertNull(walk);
			}
			try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
				assertNotNull(walk);
			}
		}
	}

	@Test
	public void pipelineUnpickRemapMojmapYarn() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=1.21.7", "--no-repo", "--no-assets", "--no-datagen-report", "--no-datagen-snbt", "--no-datapack", "--no-external-assets", "--mappings=mojmap", "--unpick=yarn"});
		//
		assertEquals(UnpickFlavour.YARN, GitCraft.getApplicationConfiguration().usedUnpickFlavour());
		assertEquals(MappingFlavour.MOJMAP, GitCraft.getApplicationConfiguration().usedMapping());
	}

	@Test
	public void pipelineUnpickRemapYarnYarn() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=1.21.7", "--no-repo", "--no-assets", "--no-datagen-report", "--no-datagen-snbt", "--no-datapack", "--no-external-assets", "--mappings=yarn", "--unpick=yarn"});
		//
		assertEquals(UnpickFlavour.YARN, GitCraft.getApplicationConfiguration().usedUnpickFlavour());
		assertEquals(MappingFlavour.YARN, GitCraft.getApplicationConfiguration().usedMapping());
	}

	@Test
	public void pipelineUnpickv3RemapMojmapYarn() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=25w34b", "--no-repo", "--no-assets", "--no-datagen-report", "--no-datagen-snbt", "--no-datapack", "--no-external-assets", "--mappings=mojmap", "--unpick=yarn"});
		//
		assertEquals(UnpickFlavour.YARN, GitCraft.getApplicationConfiguration().usedUnpickFlavour());
		assertEquals(MappingFlavour.MOJMAP, GitCraft.getApplicationConfiguration().usedMapping());
	}

	@Test
	public void pipelineUnpickv3RemapYarnYarn() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=25w34b", "--no-repo", "--no-assets", "--no-datagen-report", "--no-datagen-snbt", "--no-datapack", "--no-external-assets", "--mappings=yarn", "--unpick=yarn"});
		//
		assertEquals(UnpickFlavour.YARN, GitCraft.getApplicationConfiguration().usedUnpickFlavour());
		assertEquals(MappingFlavour.YARN, GitCraft.getApplicationConfiguration().usedMapping());
	}

	@Test
	public void pipelineMojangPlusYarnMappings() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=25w34b", "--no-repo", "--no-assets", "--no-datagen-report", "--no-datagen-snbt", "--no-datapack", "--no-external-assets", "--mappings=mojmap_yarn"});
		//
		assertEquals(UnpickFlavour.NONE, GitCraft.getApplicationConfiguration().usedUnpickFlavour());
		assertEquals(MappingFlavour.MOJMAP_YARN, GitCraft.getApplicationConfiguration().usedMapping());
	}

	@Test
	public void pipelineRaceconditionRepoAccess() throws Exception {
		MojangLauncherMetadataProvider metadataBootstrap = new MojangLauncherMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.MOJANG));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=20w13b,20w14infinite,20w14a", "--no-repo", "--no-assets", "--no-datagen-report", "--no-datagen-snbt", "--no-datapack", "--no-external-assets", "--mappings=identity_unmapped"});
		// Execution Barrier
		Configuration.reset();
		//
		GitCraft.main(new String[]{"--only-version=20w13b,20w14infinite,20w14a", "--no-assets", "--no-datagen-report", "--no-datagen-snbt", "--no-datapack", "--no-external-assets", "--mappings=identity_unmapped"});
		//
 		// If this doesn't crash, then it has succeeded
	}

	@Test
	public void pipelineSideBranchMissingSides() throws Exception {
		SkyrisingMetadataProvider metadataBootstrap = new SkyrisingMetadataProvider();
		Files.copy(LibraryPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		Configuration.editConfiguration(ApplicationConfiguration.class, original -> appConfigWithManifestSource(original, ManifestSource.SKYRISING));
		MinecraftVersionGraph _versionGraph;
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Testing-Executor").factory())) {
			_versionGraph = MinecraftVersionGraph.createFromMetadata(executor, GitCraftApplication.getApplicationConfiguration().manifestSource().getMetadataProvider());
		}
		Configuration.reset();
		//
		GitCraft.main("--manifest-source=skyrising", "--min-version=1.0.0", "--max-version=11w47a", "--mappings=feather", "--patch-lvt", "--preening-enabled", "--nests=ornithe_nests", "--signatures=sparrow", "--exceptions=raven");
		assertEquals(MappingFlavour.FEATHER, GitCraft.getApplicationConfiguration().usedMapping());
		assertTrue(GitCraft.getApplicationConfiguration().patchLvt());
		assertTrue(GitCraft.getApplicationConfiguration().enablePreening());
		assertEquals(NestsFlavour.ORNITHE_NESTS, GitCraft.getApplicationConfiguration().usedNests());
		assertEquals(SignaturesFlavour.SPARROW, GitCraft.getApplicationConfiguration().usedSignatures());
		assertEquals(ExceptionsFlavour.RAVEN, GitCraft.getApplicationConfiguration().usedExceptions());
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef(GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()));
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.0.0"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.0.1"))).getParentCount());
			assertEquals(2, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("11w47a"))).getParentCount());
			assertEquals(
				Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.0.0"))),
				Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.0.1"))).getParent(0)
			);
			Set<RevCommit> parentsOf11w47a = new HashSet<>(List.of(Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("11w47a"))).getParents()));
			assertTrue(parentsOf11w47a.contains(Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.0.0")))));
			assertTrue(parentsOf11w47a.contains(Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.0.1")))));
			{
				RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.0.0")));
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/terrain.png", targetCommit.getTree())) { //
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/com", targetCommit.getTree())) { //
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/exp-vanilla-worldgen", targetCommit.getTree())) {
					assertNull(walk);
				}
				// split version
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/client", targetCommit.getTree())) {
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/server", targetCommit.getTree())) {
					assertNotNull(walk);
				}
			}
			{
				RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.0.1")));
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/exp-vanilla-worldgen", targetCommit.getTree())) {
					assertNull(walk);
				}
				// split version
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/client", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/server", targetCommit.getTree())) {
					assertNotNull(walk);
				}
			}
			{
				RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("11w47a")));
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets", targetCommit.getTree())) { //
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/terrain.png", targetCommit.getTree())) { //
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/assets/com", targetCommit.getTree())) { //
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/external-resources", targetCommit.getTree())) {
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/data", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-snbt", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/datagen-reports", targetCommit.getTree())) {
					assertNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/resources/exp-vanilla-worldgen", targetCommit.getTree())) {
					assertNull(walk);
				}
				// split version
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/client", targetCommit.getTree())) {
					assertNotNull(walk);
				}
				try (TreeWalk walk = TreeWalk.forPath(repoWrapper.getGit().getRepository(), "minecraft/server", targetCommit.getTree())) {
					assertNotNull(walk);
				}
			}
		}
	}

	private static ApplicationConfiguration appConfigWithManifestSource(ApplicationConfiguration original, ManifestSource manifest) {
		return new ApplicationConfiguration(
			manifest,
			original.usedMapping(),
			original.fallbackMappings(),
			original.usedUnpickFlavour(),
			original.fallbackUnpickFlavours(),
			original.singleSideVersionsOnMainBranch(),
			original.onlyStableReleases(),
			original.onlySnapshots(),
			original.skipNonLinear(),
			original.onlyVersion(),
			original.minVersion(),
			original.maxVersion(),
			original.excludedVersion(),
			original.ornitheIntermediaryGeneration(),
			original.patchLvt(),
			original.usedExceptions(),
			original.usedSignatures(),
			original.usedNests(),
			original.enablePreening()
		);
	}
}
