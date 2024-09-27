package com.github.winplay02.gitcraft.mappings.yarn;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MetaVersionsSource;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
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
import java.io.UncheckedIOException;
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

	private MetaVersionsSource<FabricYarnVersionMeta> yarnVersions = new MetaVersionsSource<>(
		"https://meta.fabricmc.net/v2/versions/yarn",
		SerializationHelper.TYPE_LIST_FABRIC_YARN_VERSION_META,
		FabricYarnVersionMeta::gameVersion
	);

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
		return mcVersion.compareTo(GitCraft.config.manifestSource.getMetadataProvider().getVersionByVersionID(GitCraftConfig.YARN_MAPPINGS_START_VERSION_ID)) >= 0;
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

	private StepStatus fetchUnpickArtifacts(OrderedVersion mcVersion) throws IOException {
		// Try constants JAR for unpicking
		Path unpickingConstantsJar = getUnpickConstantsPath(mcVersion);
		FabricYarnVersionMeta yarnVersion = getTargetYarnBuild(mcVersion);
		if (yarnVersion == null) {
			return StepStatus.FAILED;
		}
		try {
			return RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(yarnVersion.makeMavenURLConstants(), new RemoteHelper.LocalFileInfo(unpickingConstantsJar, null, "yarn unpicking constants", mcVersion.launcherFriendlyVersionName()));
		} catch (RuntimeException ignored) {
			Files.deleteIfExists(unpickingConstantsJar);
		}
		MiscHelper.println("Yarn unpicking constants do not exist for %s, skipping download...", mcVersion.launcherFriendlyVersionName());
		return StepStatus.FAILED;
	}

	@Override
	public StepStatus provideMappings(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		// fabric yarn is provided for the merged jar
		if (minecraftJar != MinecraftJar.MERGED) {
			return StepStatus.NOT_RUN;
		}
		Path mappingsFile = getMappingsPathInternal(mcVersion, minecraftJar);
		Path unpickDefinitionsFile = getUnpickDefinitionsPath(mcVersion);
		// Try existing
		if (Files.exists(mappingsFile) && validateMappings(mappingsFile)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsFile);
		// Get latest build info
		FabricYarnVersionMeta yarnVersion = getYarnLatestBuild(mcVersion);
		if (yarnVersion == null) {
			MiscHelper.println("Tried to use yarn for version %s. Yarn mappings do not exist for this version in meta.fabricmc.net. Falling back to generated version...", mcVersion.launcherFriendlyVersionName());
			yarnVersion = new FabricYarnVersionMeta(mcVersion.launcherFriendlyVersionName(), "+build.", 1, String.format("net.fabricmc:yarn:%s+build.%s:unknown-fallback", mcVersion.launcherFriendlyVersionName(), 1), String.format("%s+build.%s", mcVersion.launcherFriendlyVersionName(), 1), !mcVersion.isSnapshotOrPending());
		}
		// Try latest yarn merged v2 JAR build
		{
			Path mappingsFileJar = GitCraftPaths.MAPPINGS.resolve(String.format("%s-yarn-build.%s.jar", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
			try {
				StepStatus result = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(yarnVersion.makeMavenURLMergedV2(), new RemoteHelper.LocalFileInfo(mappingsFileJar, null, "yarn mapping", mcVersion.launcherFriendlyVersionName()));
				try (FileSystem fs = FileSystems.newFileSystem(mappingsFileJar)) {
					Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
					Path unpickDefinitions = fs.getPath("extras", "definitions.unpick");
					if (Files.exists(unpickDefinitions) && unpickDefinitionsFile != null) {
						Files.copy(unpickDefinitions, unpickDefinitionsFile, StandardCopyOption.REPLACE_EXISTING);
						fetchUnpickArtifacts(mcVersion); // ignore result for now
					}
					Files.copy(mappingsPathInJar, mappingsFile, StandardCopyOption.REPLACE_EXISTING);
				}
				return StepStatus.merge(result, StepStatus.SUCCESS);
			} catch (IOException | RuntimeException ignored) {
				Files.deleteIfExists(mappingsFileJar);
			}
			MiscHelper.println("Merged Yarn mappings do not exist for %s, merging with intermediary ourselves...", mcVersion.launcherFriendlyVersionName());
		}
		// Merge with Intermediary mappings
		{
			Tuple2<Path, StepStatus> mappingsFileUnmerged = mappingsPathYarnUnmerged(mcVersion, yarnVersion);
			// intermediary is also provided for the merged jar
			StepStatus intermediaryResult = intermediaryMappings.provideMappings(mcVersion, minecraftJar);
			MemoryMappingTree mappingTree = new MemoryMappingTree();
			// Intermediary first
			MappingSourceNsSwitch nsSwitchIntermediary = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString());
			intermediaryMappings.visit(mcVersion, minecraftJar, nsSwitchIntermediary);
			// Then named yarn
			MappingSourceNsSwitch nsSwitchYarn = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString());
			// unmerged yarn mappings (1.14 - 1.14.3 (exclusive)) seem to have their mappings backwards
			if (mcVersion.compareTo(GitCraft.config.manifestSource.getMetadataProvider().getVersionByVersionID(GitCraftConfig.YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION_ID)) < 0) {
				MiscHelper.println("Yarn mappings for version %s are known to have switched namespaces", mcVersion.launcherFriendlyVersionName());
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

	private FabricYarnVersionMeta getTargetYarnBuild(OrderedVersion mcVersion) {
		if (isYarnBrokenVersion(mcVersion)) { // exclude broken versions
			return null;
		}
		FabricYarnVersionMeta yarnVersion = getYarnLatestBuild(mcVersion);
		if (yarnVersion == null) {
			yarnVersion = new FabricYarnVersionMeta(mcVersion.launcherFriendlyVersionName(), "+build.", 1, String.format("net.fabricmc:yarn:%s+build.%s:unknown-fallback", mcVersion.launcherFriendlyVersionName(), 1), String.format("%s+build.%s", mcVersion.launcherFriendlyVersionName(), 1), !mcVersion.isSnapshotOrPending());
		}
		return yarnVersion;
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		FabricYarnVersionMeta yarnVersion = getTargetYarnBuild(mcVersion);
		if (yarnVersion == null) {
			return null;
		}
		return GitCraftPaths.MAPPINGS.resolve(String.format("%s-yarn-build.%s.tiny", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	@Override
	public Map<String, Path> getAdditionalMappingInformation(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		if (minecraftJar == MinecraftJar.MERGED) {
			Path unpickConstants = getUnpickConstantsPath(mcVersion);
			Path unpickDefinitions = getUnpickDefinitionsPath(mcVersion);
			if (Files.exists(unpickConstants) && Files.exists(unpickDefinitions)) {
				return Map.of(KEY_UNPICK_CONSTANTS, unpickConstants, KEY_UNPICK_DEFINITIONS, unpickDefinitions);
			}
		}
		return super.getAdditionalMappingInformation(mcVersion, minecraftJar);
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		Path path = getMappingsPathInternal(mcVersion, MinecraftJar.MERGED);
		try (BufferedReader br = Files.newBufferedReader(path)) {
			Tiny2FileReader.read(br, visitor);
		}
	}

	protected Path getUnpickDefinitionsPath(OrderedVersion mcVersion) {
		FabricYarnVersionMeta yarnVersion = getTargetYarnBuild(mcVersion);
		if (yarnVersion == null) {
			return null;
		}
		return GitCraftPaths.MAPPINGS.resolve(String.format("%s-yarn-build.%s-unpick-definitions.unpick", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	protected Path getUnpickConstantsPath(OrderedVersion mcVersion) {
		FabricYarnVersionMeta yarnVersion = getTargetYarnBuild(mcVersion);
		if (yarnVersion == null) {
			return null;
		}
		return GitCraftPaths.MAPPINGS.resolve(String.format("%s-yarn-build.%s-constants.jar", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	private FabricYarnVersionMeta getYarnLatestBuild(OrderedVersion mcVersion) {
		try {
			return yarnVersions.getLatest(FabricIntermediaryMappings.mappingsIntermediaryPathQuirkVersion(mcVersion.launcherFriendlyVersionName()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean isYarnBrokenVersion(OrderedVersion mcVersion) {
		return GitCraftConfig.yarnBrokenVersions.contains(mcVersion.launcherFriendlyVersionName())
			/* not really broken, but does not exist: */
			|| GitCraftConfig.yarnMissingVersions.contains(mcVersion.launcherFriendlyVersionName())
			/* not broken, but does not exist, because of a re-upload */
			|| GitCraftConfig.yarnMissingReuploadedVersions.contains(mcVersion.launcherFriendlyVersionName());
	}

	private static Tuple2<Path, StepStatus> mappingsPathYarnUnmerged(OrderedVersion mcVersion, FabricYarnVersionMeta yarnVersion) {
		try {
			Path mappingsFileUnmerged = GitCraftPaths.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s.tiny", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
			if (Files.exists(mappingsFileUnmerged) && validateMappings(mappingsFileUnmerged)) {
				return Tuple2.tuple(mappingsFileUnmerged, StepStatus.UP_TO_DATE);
			}
			Path mappingsFileUnmergedJar = GitCraftPaths.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s.jar", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
			StepStatus result = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(yarnVersion.makeMavenURLUnmergedV2(), new RemoteHelper.LocalFileInfo(mappingsFileUnmergedJar, null, "unmerged yarn mapping", mcVersion.launcherFriendlyVersionName()));
			try (FileSystem fs = FileSystems.newFileSystem(mappingsFileUnmergedJar)) {
				Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
				Files.copy(mappingsPathInJar, mappingsFileUnmerged, StandardCopyOption.REPLACE_EXISTING);
			}
			return Tuple2.tuple(mappingsFileUnmerged, result);
		} catch (IOException | RuntimeException e) {
			MiscHelper.println("Yarn mappings in tiny-v2 format do not exist for %s, falling back to tiny-v1 mappings...", mcVersion.launcherFriendlyVersionName());
			try {
				Path mappingsFileUnmergedv1 = GitCraftPaths.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s-v1.tiny", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
				if (Files.exists(mappingsFileUnmergedv1) && validateMappings(mappingsFileUnmergedv1)) {
					return Tuple2.tuple(mappingsFileUnmergedv1, StepStatus.UP_TO_DATE);
				}
				Path mappingsFileUnmergedJarv1 = GitCraftPaths.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s-v1.jar", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
				StepStatus result = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(yarnVersion.makeMavenURLUnmergedV1(), new RemoteHelper.LocalFileInfo(mappingsFileUnmergedJarv1, null, "unmerged yarn mapping (v1 fallback)", mcVersion.launcherFriendlyVersionName()));
				try (FileSystem fs = FileSystems.newFileSystem(mappingsFileUnmergedJarv1)) {
					Path mappingsPathInJar = fs.getPath("mappings", "mappings.tiny");
					Files.copy(mappingsPathInJar, mappingsFileUnmergedv1, StandardCopyOption.REPLACE_EXISTING);
				}
				return Tuple2.tuple(mappingsFileUnmergedv1, result);
			} catch (IOException e2) {
				MiscHelper.println("Yarn mappings for version %s cannot be fetched. Giving up after trying merged-v2, v2, and v1 mappings.", mcVersion.launcherFriendlyVersionName());
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
