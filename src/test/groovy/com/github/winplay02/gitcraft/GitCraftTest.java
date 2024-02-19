package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.vanilla.MinecraftLauncherManifest;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Objects;

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
		RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryGitHub("WinPlay02/GitCraft", "master", "settings.gradle", new RemoteHelper.LocalFileInfo(GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve("settings.gradle"), null, "testing file", "settings"));
		assertTrue(RemoteHelper.SHA1.fileMatchesChecksum(GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve("settings.gradle"), "7c24c3faf018f76b636e9b7263added23beae48a"));
	}

	@Test
	public void versionGraphFilter() throws IOException {
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraphComplete = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
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
		assertEquals("1.14.4", versionGraphComplete.filterMapping(MappingFlavour.MOJMAP, new MappingFlavour[0]).getRootVersion().launcherFriendlyVersionName());
		assertEquals(versionGraphComplete.getMinecraftVersionByName("1.20"), versionGraphComplete.filterMinVersion(versionGraphComplete.getMinecraftVersionByName("1.20")).getRootVersion());
		MinecraftVersionGraph onlyVersionGraph = versionGraphComplete.filterOnlyVersion(versionGraphComplete.getMinecraftVersionByName("1.20"), versionGraphComplete.getMinecraftVersionByName("1.19"));
		assertEquals(versionGraphComplete.getMinecraftVersionByName("1.19"), onlyVersionGraph.getRootVersion());
		assertEquals(2L, onlyVersionGraph.stream().count());
		MinecraftVersionGraph excludeVersionGraph = versionGraphComplete.filterExcludeVersion(versionGraphComplete.getMinecraftVersionByName("1.20"), versionGraphComplete.getMinecraftVersionByName("1.19"));
		assertFalse(excludeVersionGraph.containsVersion(versionGraphComplete.getMinecraftVersionByName("1.19")));
		assertFalse(excludeVersionGraph.containsVersion(versionGraphComplete.getMinecraftVersionByName("1.20")));
		assertEquals(versionGraphComplete, versionGraphComplete.filterExcludeVersion());
		MinecraftVersionGraph minMaxVersionGraph = versionGraphComplete.filterMinVersion(versionGraphComplete.getMinecraftVersionByName("1.19")).filterMaxVersion(versionGraphComplete.getMinecraftVersionByName("1.19"));
		assertEquals(1L, minMaxVersionGraph.stream().count());
		MinecraftVersionGraph mainlineVersionGraph = versionGraphComplete.filterMainlineVersions();
		assertFalse(mainlineVersionGraph.stream().anyMatch(MinecraftVersionGraph::isVersionNonLinearSnapshot));
	}

	@Test
	public void mappingsMojang() throws IOException {
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
		//
		Path mappingsPath = GitCraft.MOJANG_MAPPINGS.get().getMappingsPathInternal(versionGraph.getMinecraftVersionByName("1.20"));
		Files.deleteIfExists(mappingsPath);
		assertFalse(Files.exists(mappingsPath));
		assertTrue(GitCraft.MOJANG_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionByName("1.14.4")));
		assertFalse(GitCraft.MOJANG_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionByName("1.12")));
		assertEquals(Step.StepResult.SUCCESS, GitCraft.MOJANG_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.20")));
		assertTrue(Files.exists(mappingsPath));
		assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.MOJANG_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.20")));
		assertTrue(Files.size(mappingsPath) > 0);
		assertFalse(GitCraft.MOJANG_MAPPINGS.get().supportsComments());
		assertFalse(GitCraft.MOJANG_MAPPINGS.get().supportsConstantUnpicking());
		assertNotNull(GitCraft.MOJANG_MAPPINGS.get().getMappingsProvider(versionGraph.getMinecraftVersionByName("1.20")));
		assertEquals(MappingsNamespace.OFFICIAL.toString(), GitCraft.MOJANG_MAPPINGS.get().getSourceNS());
		assertEquals(MappingsNamespace.NAMED.toString(), GitCraft.MOJANG_MAPPINGS.get().getDestinationNS());
	}

	@Test
	public void mappingsParchment() throws IOException {
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
		//
		Path mappingsPath = GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("1.20.1")).orElse(null);
		assertNotNull(mappingsPath);
		assertFalse(Files.exists(mappingsPath));
		assertTrue(GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionByName("1.16.5")));
		assertFalse(GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionByName("1.14.4")));
		assertEquals(Step.StepResult.SUCCESS, GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.20.1")));
		assertTrue(Files.exists(mappingsPath));
		assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.20.1")));
		assertTrue(Files.size(mappingsPath) > 0);
		assertTrue(GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().supportsComments());
		assertFalse(GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().supportsConstantUnpicking());
		assertEquals(MappingsNamespace.OFFICIAL.toString(), GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().getSourceNS());
		assertEquals(MappingsNamespace.NAMED.toString(), GitCraft.MOJANG_PARCHMENT_MAPPINGS.get().getDestinationNS());
	}

	@Test
	public void mappingsFabricIntermediary() throws IOException {
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
		//
		Path mappingsPath = GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("1.20")).orElse(null);
		assertNotNull(mappingsPath);
		assertFalse(Files.exists(mappingsPath));
		assertTrue(GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.18.43.b")));
		assertFalse(GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.18.43.a")));
		assertEquals(Step.StepResult.SUCCESS, GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.20")));
		assertTrue(Files.exists(mappingsPath));
		assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.20")));
		assertTrue(Files.size(mappingsPath) > 0);
		assertFalse(GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().supportsComments());
		assertFalse(GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().supportsConstantUnpicking());
		assertEquals(MappingsNamespace.OFFICIAL.toString(), GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().getSourceNS());
		assertEquals(MappingsNamespace.INTERMEDIARY.toString(), GitCraft.FABRIC_INTERMEDIARY_MAPPINGS.get().getDestinationNS());
	}

	@Test
	public void mappingsYarn() throws IOException {
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
		//
		Path mappingsPath = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("1.20")).orElse(null);
		assertNotNull(mappingsPath);
		assertFalse(Files.exists(mappingsPath));
		assertTrue(GitCraft.YARN_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.18.49.a")));
		assertFalse(GitCraft.YARN_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.18.48.b")));
		assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.20")));
		assertTrue(Files.exists(mappingsPath));
		assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.20")));
		assertTrue(Files.size(mappingsPath) > 0);
		// Broken Versions
		assertFalse(GitCraft.YARN_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.19.13.a")));
		assertFalse(GitCraft.YARN_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.19.13.b")));
		assertFalse(GitCraft.YARN_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.19.14.a")));
		assertFalse(GitCraft.YARN_MAPPINGS.get().doMappingsExist(versionGraph.getMinecraftVersionBySemanticVersion("1.14-alpha.19.14.b")));
		//
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("1.14.2")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.14.2")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.14.2")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("1.14.3")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.14.3")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.14.3")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("1.14.4")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.14.4")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.14.4")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("1.19")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.19")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("1.19")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionBySemanticVersion("1.14.2-rc.1")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionBySemanticVersion("1.14.2-rc.1")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionBySemanticVersion("1.14.2-rc.1")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("19w04b")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("19w04b")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("19w04b")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("19w08a")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("19w08a")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("19w08a")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionByName("19w12b")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("19w12b")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionByName("19w12b")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionBySemanticVersion("1.15.2-rc.2.combat.5")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionBySemanticVersion("1.15.2-rc.2.combat.5")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionBySemanticVersion("1.15.2-rc.2.combat.5")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionBySemanticVersion("1.16.2-beta.3.combat.6")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionBySemanticVersion("1.16.2-beta.3.combat.6")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionBySemanticVersion("1.16.2-beta.3.combat.6")));
		}
		{
			Path mappingsPathTest = GitCraft.YARN_MAPPINGS.get().getMappingsPath(versionGraph.getMinecraftVersionBySemanticVersion("1.14.5-combat.2")).orElse(null);
			assertNotNull(mappingsPathTest);
			assertEquals(Step.StepResult.SUCCESS, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionBySemanticVersion("1.14.5-combat.2")));
			assertTrue(Files.exists(mappingsPathTest));
			assertEquals(Step.StepResult.UP_TO_DATE, GitCraft.YARN_MAPPINGS.get().prepareMappings(versionGraph.getMinecraftVersionBySemanticVersion("1.14.5-combat.2")));
		}
		assertTrue(GitCraft.YARN_MAPPINGS.get().supportsComments());
		assertTrue(GitCraft.YARN_MAPPINGS.get().supportsConstantUnpicking());
		assertEquals(MappingsNamespace.OFFICIAL.toString(), GitCraft.YARN_MAPPINGS.get().getSourceNS());
		assertEquals(MappingsNamespace.NAMED.toString(), GitCraft.YARN_MAPPINGS.get().getDestinationNS());
	}

	protected static RevCommit findCommit(RepoWrapper repoWrapper, OrderedVersion mcVersion) throws IOException, GitAPIException {
		Iterator<RevCommit> iterator = repoWrapper.getGit().log().all().setRevFilter(new Step.CommitMsgFilter(mcVersion.toCommitMessage())).call().iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		}
		return null;
	}

	@Test
	public void pipeline() throws Exception {
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
		//
		GitCraft.main(new String[]{"--only-version=1.17.1,1.18_experimental-snapshot-1,21w37a,1.18,22w13oneblockatatime"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef(GitCraft.config.gitMainlineLinearBranch));
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef("1.18_experimental-snapshot-1"));
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef("22w13oneblockatatime"));
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("22w13oneblockatatime"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18"))).getParentCount());
			assertEquals(2, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("21w37a"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18_experimental-snapshot-1"))).getParentCount());
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1")));
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
	public void pipelineReset() throws Exception {
		GitCraft.main(new String[]{"--only-version=1.17.1,1.18_experimental-snapshot-1,21w37a,1.18,22w13oneblockatatime", "--refresh", "--refresh-min-version=1.18"});
		try (RepoWrapper repoWrapper = GitCraft.getRepository()) {
			assertNotNull(repoWrapper);
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef(GitCraft.config.gitMainlineLinearBranch));
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef("1.18_experimental-snapshot-1"));
			assertNotNull(repoWrapper.getGit().getRepository().getRefDatabase().findRef("22w13oneblockatatime"));
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("22w13oneblockatatime"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18"))).getParentCount());
			assertEquals(2, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("21w37a"))).getParentCount());
			assertEquals(1, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.18_experimental-snapshot-1"))).getParentCount());
			assertEquals(0, Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1"))).getParentCount());
			RevCommit targetCommit = Objects.requireNonNull(findCommit(repoWrapper, GitCraft.versionGraph.getMinecraftVersionByName("1.17.1")));
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
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
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
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
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
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
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
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
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
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
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
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
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
		MinecraftLauncherManifest metadataBootstrap = new MinecraftLauncherManifest();
		Files.copy(GitCraftPaths.lookupCurrentWorkingDirectory().resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve(String.format("semver-cache-%s.json", metadataBootstrap.getInternalName())), StandardCopyOption.REPLACE_EXISTING);
		metadataBootstrap = new MinecraftLauncherManifest();
		MinecraftVersionGraph versionGraph = MinecraftVersionGraph.createFromMetadata(ManifestSource.MOJANG_MINECRAFT_LAUNCHER, metadataBootstrap);
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
}
