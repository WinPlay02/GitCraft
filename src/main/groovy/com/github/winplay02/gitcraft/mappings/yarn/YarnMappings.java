package com.github.winplay02.gitcraft.mappings.yarn;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.GitCraftQuirks;
import com.github.winplay02.gitcraft.mappings.Mapping;

import com.github.winplay02.gitcraft.meta.GameVersionBuildMeta;

import com.github.winplay02.gitcraft.meta.MetaUrls;
import com.github.winplay02.gitcraft.meta.RemoteVersionMetaSource;
import com.github.winplay02.gitcraft.meta.VersionMetaSource;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationTypes;
import groovy.lang.Tuple2;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class YarnMappings extends Mapping {

	public static final VersionMetaSource<GameVersionBuildMeta> YARN_VERSIONS = new RemoteVersionMetaSource<>(
		MetaUrls.FABRIC_YARN,
		SerializationTypes.TYPE_LIST_GAME_VERSION_BUILD_META,
		GameVersionBuildMeta::gameVersion
	);

	public static GameVersionBuildMeta usePotentialBuildOverride(GameVersionBuildMeta meta) {
		// TODO is something like this still needed?
		int build = GitCraftQuirks.yarnBrokenBuildOverride.getOrDefault(Tuple2.tuple(meta.gameVersion(), meta.build()), meta.build());
		if (meta.build() == build) {
			return meta;
		}
		String prefix = meta.gameVersion() + meta.separator();
		return new GameVersionBuildMeta(
			meta.gameVersion(),
			meta.separator(),
			build,
			meta.maven().replace(prefix + meta.build(), prefix + build),
			meta.version().replace(prefix + meta.build(), prefix + build),
			meta.stable());
	}

	protected FabricIntermediaryMappings intermediaryMappings;

	public YarnMappings(FabricIntermediaryMappings intermediaryMappings) {
		this.intermediaryMappings = intermediaryMappings;
	}

	@Override
	public String getName() {
		return "Yarn";
	}

	@Override
	public boolean supportsComments() {
		return true;
	}

	@Override
	public boolean supportsConstantUnpicking() {
		return true;
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.NAMED.toString();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		if (isYarnBrokenVersion(mcVersion)) { // exclude broken versions
			return false;
		}
		return mcVersion.compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(GitCraftQuirks.YARN_MAPPINGS_START_VERSION_ID)) >= 0;
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		// fabric yarn is provided for the merged jar
		return minecraftJar == MinecraftJar.MERGED && doMappingsExist(mcVersion);
	}

	@Override
	public boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		// the merged mappings can be used for all jars
		return doMappingsExist(mcVersion);
	}

	public static Path getYarnMergedV2JarPath(OrderedVersion targetVersion, GameVersionBuildMeta yarnVersion) {
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-build.%s.jar", targetVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	public static StepStatus fetchYarnMergedV2Jar(IStepContext<?, OrderedVersion> versionContext, GameVersionBuildMeta yarnVersion) {
		Path mappingsFileJar = getYarnMergedV2JarPath(versionContext.targetVersion(), yarnVersion);
		return RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), yarnVersion.makeMergedV2JarMavenUrl(GitCraft.FABRIC_MAVEN), new FileSystemNetworkManager.LocalFileInfo(mappingsFileJar, null, null, "yarn mapping", versionContext.targetVersion().launcherFriendlyVersionName()));
	}

	@Override
	public StepStatus provideMappings(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		// fabric yarn is provided for the merged jar
		if (minecraftJar != MinecraftJar.MERGED) {
			return StepStatus.NOT_RUN;
		}
		Path mappingsFile = getMappingsPathInternal(versionContext.targetVersion(), minecraftJar);
		// Try existing
		if (Files.exists(mappingsFile) && validateMappings(mappingsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsFile);
		// Get latest build info
		GameVersionBuildMeta yarnVersion = getTargetYarnBuild(versionContext.targetVersion());
		if (yarnVersion == null) {
			return StepStatus.FAILED;
		}
		// Try latest yarn merged v2 JAR build
		{
			Path mappingsFileJar = getYarnMergedV2JarPath(versionContext.targetVersion(), yarnVersion);
			try {
				StepStatus result = fetchYarnMergedV2Jar(versionContext, yarnVersion);
				try (FileSystem fs = FileSystems.newFileSystem(mappingsFileJar)) {
					{
						Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
						Files.copy(mappingsPathInJar, mappingsFile, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				return StepStatus.merge(result, StepStatus.SUCCESS);
			} catch (IOException | RuntimeException ignored) {
				Files.deleteIfExists(mappingsFileJar);
			}
			MiscHelper.println("Merged Yarn mappings do not exist for %s, merging with intermediary ourselves...", versionContext.targetVersion().launcherFriendlyVersionName());
		}
		// Merge with Intermediary mappings
		{
			Tuple2<Path, StepStatus> mappingsFileUnmerged = mappingsPathYarnUnmerged(versionContext, yarnVersion);
			// intermediary is also provided for the merged jar
			StepStatus intermediaryResult = intermediaryMappings.provideMappings(versionContext, minecraftJar);
			MemoryMappingTree mappingTree = new MemoryMappingTree();
			// Intermediary first
			MappingSourceNsSwitch nsSwitchIntermediary = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString());
			intermediaryMappings.visit(versionContext.targetVersion(), minecraftJar, nsSwitchIntermediary);
			// Then named yarn
			MappingSourceNsSwitch nsSwitchYarn = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString());
			// unmerged yarn mappings (1.14 - 1.14.3 (exclusive)) seem to have their mappings backwards
			if (versionContext.targetVersion().compareTo(GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().getVersionByVersionID(GitCraftQuirks.YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION_ID)) < 0) {
				MiscHelper.println("Yarn mappings for version %s are known to have switched namespaces", versionContext.targetVersion().launcherFriendlyVersionName());
				MappingReader.read(mappingsFileUnmerged.getV1(), new MappingNsRenamer(nsSwitchYarn, Map.of(MappingsNamespace.INTERMEDIARY.toString(), MappingsNamespace.NAMED.toString(), MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString())));
			} else {
				MappingReader.read(mappingsFileUnmerged.getV1(), nsSwitchYarn);
			}
			yarn_fixInnerClasses(mappingTree);
			try (MappingWriter writer = MappingWriter.create(mappingsFile, MappingFormat.TINY_2_FILE)) {
				MappingNsCompleter nsCompleter = new MappingNsCompleter(writer, Map.of(MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString()), true);
				MappingDstNsReorder dstReorder = new MappingDstNsReorder(nsCompleter, List.of(MappingsNamespace.INTERMEDIARY.toString(), MappingsNamespace.NAMED.toString()));
				MappingSourceNsSwitch sourceNsSwitch = new MappingSourceNsSwitch(dstReorder, MappingsNamespace.OFFICIAL.toString());
				mappingTree.accept(sourceNsSwitch);
			}
			return StepStatus.merge(mappingsFileUnmerged.getV2(), intermediaryResult, StepStatus.SUCCESS);
		}
	}

	public static GameVersionBuildMeta getTargetYarnBuild(OrderedVersion mcVersion) {
		if (isYarnBrokenVersion(mcVersion)) { // exclude broken versions
			return null;
		}
		GameVersionBuildMeta yarnVersion = getYarnLatestBuild(mcVersion);
		if (yarnVersion == null) {
			MiscHelper.println("Tried to use yarn for version %s. Yarn mappings do not exist for this version in meta.fabricmc.net. Falling back to generated version...", mcVersion.launcherFriendlyVersionName());
			yarnVersion = new GameVersionBuildMeta(mcVersion.launcherFriendlyVersionName(), "+build.", 1, String.format("net.fabricmc:yarn:%s+build.%s", mcVersion.launcherFriendlyVersionName(), 1), String.format("%s+build.%s", mcVersion.launcherFriendlyVersionName(), 1), !mcVersion.isSnapshotOrPending());
		}
		return usePotentialBuildOverride(yarnVersion);
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		GameVersionBuildMeta yarnVersion = getTargetYarnBuild(mcVersion);
		if (yarnVersion == null) {
			return null;
		}
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-build.%s.tiny", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		Path path = getMappingsPathInternal(mcVersion, MinecraftJar.MERGED);
		try (BufferedReader br = Files.newBufferedReader(path)) {
			Tiny2FileReader.read(br, visitor);
		}
	}

	public static GameVersionBuildMeta getYarnLatestBuild(OrderedVersion mcVersion) {
		try {
			return YARN_VERSIONS.getLatest(FabricIntermediaryMappings.mappingsIntermediaryPathQuirkVersion(mcVersion.launcherFriendlyVersionName()));
		} catch (IOException | URISyntaxException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isYarnBrokenVersion(OrderedVersion mcVersion) {
		return GitCraftQuirks.yarnBrokenVersions.contains(mcVersion.launcherFriendlyVersionName())
			/* not really broken, but does not exist: */
			|| GitCraftQuirks.yarnMissingVersions.contains(mcVersion.launcherFriendlyVersionName())
			/* not broken, but does not exist, because of a re-upload */
			|| GitCraftQuirks.yarnMissingReuploadedVersions.contains(mcVersion.launcherFriendlyVersionName());
	}

	private static Tuple2<Path, StepStatus> mappingsPathYarnUnmerged(IStepContext<?, OrderedVersion> versionContext, GameVersionBuildMeta yarnVersion) {
		try {
			Path mappingsFileUnmerged = GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-unmerged-build.%s.tiny", versionContext.targetVersion().launcherFriendlyVersionName(), yarnVersion.build()));
			if (Files.exists(mappingsFileUnmerged) && validateMappings(mappingsFileUnmerged)) {
				return Tuple2.tuple(mappingsFileUnmerged, StepStatus.UP_TO_DATE);
			}
			Path mappingsFileUnmergedJar = GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-unmerged-build.%s.jar", versionContext.targetVersion().launcherFriendlyVersionName(), yarnVersion.build()));
			StepStatus result = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), yarnVersion.makeV2JarMavenUrl(GitCraft.FABRIC_MAVEN), new FileSystemNetworkManager.LocalFileInfo(mappingsFileUnmergedJar, null, null, "unmerged yarn mapping", versionContext.targetVersion().launcherFriendlyVersionName()));
			try (FileSystem fs = FileSystems.newFileSystem(mappingsFileUnmergedJar)) {
				Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
				Files.copy(mappingsPathInJar, mappingsFileUnmerged, StandardCopyOption.REPLACE_EXISTING);
			}
			return Tuple2.tuple(mappingsFileUnmerged, result);
		} catch (IOException | RuntimeException e) {
			MiscHelper.println("Yarn mappings in tiny-v2 format do not exist for %s, falling back to tiny-v1 mappings...", versionContext.targetVersion().launcherFriendlyVersionName());
			try {
				Path mappingsFileUnmergedv1 = GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-unmerged-build.%s-v1.tiny", versionContext.targetVersion().launcherFriendlyVersionName(), yarnVersion.build()));
				if (Files.exists(mappingsFileUnmergedv1) && validateMappings(mappingsFileUnmergedv1)) {
					return Tuple2.tuple(mappingsFileUnmergedv1, StepStatus.UP_TO_DATE);
				}
				Path mappingsFileUnmergedJarv1 = GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve(String.format("%s-yarn-unmerged-build.%s-v1.jar", versionContext.targetVersion().launcherFriendlyVersionName(), yarnVersion.build()));
				StepStatus result = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(versionContext.executorService(), yarnVersion.makeJarMavenUrl(GitCraft.FABRIC_MAVEN), new FileSystemNetworkManager.LocalFileInfo(mappingsFileUnmergedJarv1, null, null, "unmerged yarn mapping (v1 fallback)", versionContext.targetVersion().launcherFriendlyVersionName()));
				try (FileSystem fs = FileSystems.newFileSystem(mappingsFileUnmergedJarv1)) {
					Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
					Files.copy(mappingsPathInJar, mappingsFileUnmergedv1, StandardCopyOption.REPLACE_EXISTING);
				}
				return Tuple2.tuple(mappingsFileUnmergedv1, result);
			} catch (IOException e2) {
				MiscHelper.println("Yarn mappings for version %s cannot be fetched. Giving up after trying merged-v2, v2, and v1 mappings.", versionContext.targetVersion().launcherFriendlyVersionName());
				throw new RuntimeException(e);
			}
		}
	}

	private static void yarn_fixInnerClasses(MemoryMappingTree mappingTree) {
		int named = mappingTree.getNamespaceId(MappingsNamespace.NAMED.toString());

		for (MappingTree.ClassMapping entry : mappingTree.getClasses()) {
			String name = entry.getName(named);

			if (name != null) {
				continue;
			}

			entry.setDstName(yarn_matchEnclosingClass(entry.getSrcName(), mappingTree), named);
		}
	}

	private static String yarn_matchEnclosingClass(String sharedName, MemoryMappingTree mappingTree) {
		final int named = mappingTree.getNamespaceId(MappingsNamespace.NAMED.toString());
		final String[] path = sharedName.split(Pattern.quote("$"));

		for (int i = path.length - 2; i >= 0; i--) {
			final String currentPath = String.join("$", Arrays.copyOfRange(path, 0, i + 1));
			final MappingTree.ClassMapping match = mappingTree.getClass(currentPath);

			if (match != null && match.getName(named) != null) {
				return match.getName(named) + "$" + String.join("$", Arrays.copyOfRange(path, i + 1, path.length));
			}
		}

		return sharedName;
	}
}
