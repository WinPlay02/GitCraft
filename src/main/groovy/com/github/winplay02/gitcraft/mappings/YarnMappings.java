package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.meta.FabricYarnVersionMeta;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RemoteHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.github.winplay02.gitcraft.GitCraft;
import groovy.lang.Tuple2;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class YarnMappings extends Mapping {

	private Map<String, FabricYarnVersionMeta> yarnVersions = null;

	protected FabricIntermediaryMappings intermediaryMappings;

	public YarnMappings(FabricIntermediaryMappings intermediaryMappings) {
		this.intermediaryMappings = intermediaryMappings;
	}

	@Override
	public String getName() {
		return "Yarn";
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
		return mcVersion.compareTo(GitCraftConfig.YARN_MAPPINGS_START_VERSION) >= 0;
	}

	@Override
	public Step.StepResult prepareMappings(OrderedVersion mcVersion) throws IOException {
		Path mappingsFile = getMappingsPathInternal(mcVersion);
		// Try existing
		if (Files.exists(mappingsFile)) {
			return Step.StepResult.UP_TO_DATE;
		}
		// Get latest build info
		FabricYarnVersionMeta yarnVersion = getYarnLatestBuild(mcVersion);
		if (yarnVersion == null) {
			MiscHelper.println("Tried to use yarn for version %s. Yarn mappings do not exist for this version in meta.fabricmc.net. Falling back to generated version...", mcVersion.launcherFriendlyVersionName());
			yarnVersion = new FabricYarnVersionMeta(mcVersion.launcherFriendlyVersionName(), "+build.", 1, String.format("net.fabricmc:yarn:%s+build.%s:unknown-fallback", mcVersion.launcherFriendlyVersionName(), 1), String.format("%s+build.%s", mcVersion.launcherFriendlyVersionName(), 1), !mcVersion.isSnapshotOrPending());
		}
		// Try latest yarn merged v2 JAR build
		{
			Path mappingsFileJar = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-build.%s.jar", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
			try {
				Step.StepResult result = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(yarnVersion.makeMavenURLMergedV2(), new RemoteHelper.LocalFileInfo(mappingsFileJar, null, "yarn mapping", mcVersion.launcherFriendlyVersionName()));
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mappingsFileJar)) {
					Path mappingsPathInJar = fs.get().getPath("mappings", "mappings.tiny");
					Files.copy(mappingsPathInJar, mappingsFile, StandardCopyOption.REPLACE_EXISTING);
				}
				return Step.StepResult.merge(result, Step.StepResult.SUCCESS);
			} catch (IOException | RuntimeException ignored) {
				Files.deleteIfExists(mappingsFileJar);
			}
			MiscHelper.println("Merged Yarn mappings do not exist for %s, merging with intermediary ourselves...", mcVersion.launcherFriendlyVersionName());
		}
		// Merge with Intermediary mappings
		{
			Tuple2<Path, Step.StepResult> mappingsFileUnmerged = mappingsPathYarnUnmerged(mcVersion, yarnVersion);
			Step.StepResult intermediaryResult = intermediaryMappings.prepareMappings(mcVersion);
			Path mappingsFileIntermediary = intermediaryMappings.getMappingsPathInternal(mcVersion);
			MemoryMappingTree mappingTree = new MemoryMappingTree();
			// Intermediary first
			MappingSourceNsSwitch nsSwitchIntermediary = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString());
			MappingReader.read(mappingsFileIntermediary, nsSwitchIntermediary);
			// Then named yarn
			MappingSourceNsSwitch nsSwitchYarn = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString());
			// unmerged yarn mappings (1.14 - 1.14.3 (exclusive)) seem to have their mappings backwards
			if (mcVersion.compareTo(GitCraftConfig.YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION) < 0) {
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
			return Step.StepResult.merge(mappingsFileUnmerged.getV2(), intermediaryResult, Step.StepResult.SUCCESS);
		}
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion) {
		if (isYarnBrokenVersion(mcVersion)) { // exclude broken versions
			return null;
		}
		FabricYarnVersionMeta yarnVersion = getYarnLatestBuild(mcVersion);
		if (yarnVersion == null) {
			yarnVersion = new FabricYarnVersionMeta(mcVersion.launcherFriendlyVersionName(), "+build.", 1, String.format("net.fabricmc:yarn:%s+build.%s:unknown-fallback", mcVersion.launcherFriendlyVersionName(), 1), String.format("%s+build.%s", mcVersion.launcherFriendlyVersionName(), 1), !mcVersion.isSnapshotOrPending());
		}
		return GitCraft.MAPPINGS.resolve(String.format("%s-yarn-build.%s.tiny", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
	}

	private FabricYarnVersionMeta getYarnLatestBuild(OrderedVersion mcVersion) {
		if (yarnVersions == null) {
			try {
				List<FabricYarnVersionMeta> yarnVersionMetas = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(GitCraftConfig.URL_FABRIC_YARN_META)), SerializationHelper.TYPE_LIST_FABRIC_YARN_VERSION_META);
				yarnVersions = yarnVersionMetas.stream().collect(Collectors.groupingBy(FabricYarnVersionMeta::gameVersion)).values().stream().map(fabricYarnVersionMetas -> fabricYarnVersionMetas.stream().max(Comparator.naturalOrder())).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(FabricYarnVersionMeta::gameVersion, Function.identity()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return yarnVersions.get(FabricIntermediaryMappings.mappingsIntermediaryPathQuirkVersion(mcVersion.launcherFriendlyVersionName()));
	}

	private static boolean isYarnBrokenVersion(OrderedVersion mcVersion) {
		return GitCraftConfig.yarnBrokenVersions.contains(mcVersion.launcherFriendlyVersionName())
				/* not really broken, but does not exist: */
				|| GitCraftConfig.yarnMissingVersions.contains(mcVersion.launcherFriendlyVersionName())
				/* not broken, but does not exist, because of a re-upload */
				|| GitCraftConfig.yarnMissingReuploadedVersions.contains(mcVersion.launcherFriendlyVersionName());
	}

	private static Tuple2<Path, Step.StepResult> mappingsPathYarnUnmerged(OrderedVersion mcVersion, FabricYarnVersionMeta yarnVersion) {
		try {
			Path mappingsFileUnmerged = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s.tiny", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
			if (Files.exists(mappingsFileUnmerged)) {
				return Tuple2.tuple(mappingsFileUnmerged, Step.StepResult.UP_TO_DATE);
			}
			Path mappingsFileUnmergedJar = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s.jar", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
			Step.StepResult result = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(yarnVersion.makeMavenURLUnmergedV2(), new RemoteHelper.LocalFileInfo(mappingsFileUnmergedJar, null, "unmerged yarn mapping", mcVersion.launcherFriendlyVersionName()));
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mappingsFileUnmergedJar)) {
				Path mappingsPathInJar = fs.get().getPath("mappings", "mappings.tiny");
				Files.copy(mappingsPathInJar, mappingsFileUnmerged, StandardCopyOption.REPLACE_EXISTING);
			}
			return Tuple2.tuple(mappingsFileUnmerged, result);
		} catch (IOException | RuntimeException e) {
			MiscHelper.println("Yarn mappings in tiny-v2 format do not exist for %s, falling back to tiny-v1 mappings...", mcVersion.launcherFriendlyVersionName());
			try {
				Path mappingsFileUnmergedv1 = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s-v1.tiny", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
				if (Files.exists(mappingsFileUnmergedv1)) {
					return Tuple2.tuple(mappingsFileUnmergedv1, Step.StepResult.UP_TO_DATE);
				}
				Path mappingsFileUnmergedJarv1 = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s-v1.jar", mcVersion.launcherFriendlyVersionName(), yarnVersion.build()));
				Step.StepResult result = RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetryMaven(yarnVersion.makeMavenURLUnmergedV1(), new RemoteHelper.LocalFileInfo(mappingsFileUnmergedJarv1, null, "unmerged yarn mapping (v1 fallback)", mcVersion.launcherFriendlyVersionName()));
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mappingsFileUnmergedJarv1)) {
					Path mappingsPathInJar = fs.get().getPath("mappings", "mappings.tiny");
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
