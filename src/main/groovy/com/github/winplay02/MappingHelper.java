package com.github.winplay02;

import com.github.winplay02.meta.FabricYarnVersionMeta;
import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.data.McVersion;
import dex.mcgitmaker.loom.FileSystemUtil;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.ProGuardReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MappingHelper {

	public enum MappingFlavour {
		MOJMAP, FABRIC_INTERMEDIARY, YARN;

		@Override
		public String toString() {
			return super.toString().toLowerCase(Locale.ROOT);
		}

		public String getSourceNS() {
			return MappingsNamespace.OFFICIAL.toString();
		}

		public String getDestinationNS() {
			return switch (this) {
				case MOJMAP, YARN -> MappingsNamespace.NAMED.toString();
				case FABRIC_INTERMEDIARY -> MappingsNamespace.INTERMEDIARY.toString();
			};
		}

		private boolean isYarnBrokenVersion(McVersion mcVersion) {
			return GitCraftConfig.yarnBrokenVersions.contains(mcVersion.version)
					/* not really broken, but does not exist: */
					|| GitCraftConfig.yarnMissingVersions.contains(mcVersion.version)
					/* not broken, but does not exist, because of a re-upload */
					|| GitCraftConfig.yarnMissingReuploadedVersions.contains(mcVersion.version);
		}

		public boolean doMappingsExist(McVersion mcVersion) {
			return switch (this) {
				case MOJMAP -> mcVersion.hasMappings;
				case YARN -> {
					if (isYarnBrokenVersion(mcVersion)) { // exclude broken versions
						yield false;
					}
					try {
						yield SemanticVersion.parse(mcVersion.loaderVersion).compareTo((Version) GitCraftConfig.YARN_MAPPINGS_START_VERSION) >= 0;
					} catch (VersionParsingException e) {
						throw new RuntimeException(e);
					}
				}
				case FABRIC_INTERMEDIARY -> {
					try {
						yield SemanticVersion.parse(mcVersion.loaderVersion).compareTo((Version) GitCraftConfig.INTERMEDIARY_MAPPINGS_START_VERSION) >= 0;
					} catch (VersionParsingException e) {
						throw new RuntimeException(e);
					}
				}
			};
		}

		public Optional<Path> getMappingsPath(McVersion mcVersion) {
			return switch (this) {
				case MOJMAP -> {
					try {
						yield Optional.of(mappingsPathMojMap(mcVersion));
					} catch (IOException e) {
						yield Optional.empty();
					}
				}
				case YARN ->
						Optional.ofNullable(isYarnBrokenVersion(mcVersion) ? null : mappingsPathYarn(mcVersion)); // exclude broken versions
				case FABRIC_INTERMEDIARY -> Optional.ofNullable(mappingsPathIntermediary(mcVersion));
			};
		}

		public IMappingProvider getMappingsProvider(McVersion mcVersion) {
			if (!doMappingsExist(mcVersion)) {
				MiscHelper.panic("Tried to use %s-mappings for version %s. These mappings do not exist for this version.", this, mcVersion.version);
			}
			Optional<Path> mappingsPath = getMappingsPath(mcVersion);
			if (mappingsPath.isEmpty()) {
				MiscHelper.panic("An error occurred while getting mapping information for %s (version %s)", this, mcVersion.version);
			}
			return TinyUtils.createTinyMappingProvider(mappingsPath.get(), getSourceNS(), getDestinationNS());
		}
	}
	
	public static final String FABRIC_YARN_META = "https://meta.fabricmc.net/v2/versions/yarn";

	private static Map<String, FabricYarnVersionMeta> yarnVersions = null;

	public static FabricYarnVersionMeta getYarnLatestBuild(McVersion mcVersion) {
		if (yarnVersions == null) {
			try {
				List<FabricYarnVersionMeta> yarnVersionMetas = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(FABRIC_YARN_META)), SerializationHelper.TYPE_LIST_FABRIC_YARN_VERSION_META);
				yarnVersions = yarnVersionMetas.stream().collect(Collectors.groupingBy(FabricYarnVersionMeta::gameVersion)).values().stream().map(fabricYarnVersionMetas -> fabricYarnVersionMetas.stream().max(Comparator.naturalOrder())).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(FabricYarnVersionMeta::gameVersion, Function.identity()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return yarnVersions.get(mappingsIntermediaryPathQuirkVersion(mcVersion.version));
	}

	public static MemoryMappingTree createIntermediaryMappingsProvider(McVersion mcVersion) throws IOException {
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		Path intermediaryPath = mappingsPathIntermediary(mcVersion);
		if (intermediaryPath != null) {
			MappingReader.read(intermediaryPath, mappingTree);
		} else {
			if (GitCraft.config.loomFixRecords) {
				MiscHelper.panic("Loom should be used to fix invalid records, but intermediary mappings are not (yet) available");
			}
		}
		return mappingTree;
	}

	private static Path mappingsPathYarn(McVersion mcVersion) {
		FabricYarnVersionMeta yarnVersion = getYarnLatestBuild(mcVersion);
		if (yarnVersion == null) {
			// MiscHelper.panic("Tried to use yarn for version %s. Yarn mappings do not exist for this version.", mcVersion.version);
			MiscHelper.println("Tried to use yarn for version %s. Yarn mappings do not exist for this version in meta.fabricmc.net. Falling back to generated version...", mcVersion.version);
			yarnVersion = new FabricYarnVersionMeta(mcVersion.version, "+build.", 1, String.format("net.fabricmc:yarn:%s+build.%s:unknown-fallback", mcVersion.version, 1), String.format("%s+build.%s", mcVersion.version, 1), !mcVersion.snapshot);
		}
		Path mappingsFile = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-build.%s.tiny", mcVersion.version, yarnVersion.build()));
		if (mappingsFile.toFile().exists()) {
			return mappingsFile;
		}
		Path mappingsFileJar = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-build.%s.jar", mcVersion.version, yarnVersion.build()));
		try {
			RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetry(yarnVersion.makeMavenURLMergedV2(), mappingsFileJar, null, "yarn mapping", mcVersion.version);
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mappingsFileJar)) {
				Path mappingsPathInJar = fs.get().getPath("mappings", "mappings.tiny");
				Files.copy(mappingsPathInJar, mappingsFile, StandardCopyOption.REPLACE_EXISTING);
			}
			return mappingsFile;
		} catch (IOException | RuntimeException ignored) {
			mappingsFileJar.toFile().delete();
		}
		MiscHelper.println("Merged Yarn mappings do not exist for %s, merging with intermediary ourselves...", mcVersion.version);
		Path mappingsFileUnmerged = mappingsPathYarnUnmerged(mcVersion, yarnVersion);
		Path mappingsFileIntermediary = mappingsPathIntermediary(mcVersion);
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		try {
			// Intermediary first
			MappingSourceNsSwitch nsSwitchIntermediary = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString());
			MappingReader.read(mappingsFileIntermediary, nsSwitchIntermediary);
			// Then named yarn
			MappingSourceNsSwitch nsSwitchYarn = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.INTERMEDIARY.toString());
			try {
				// unmerged yarn mappings (1.14 - 1.14.3 (exclusive)) seem to have their mappings backwards
				SemanticVersion targetVersion = SemanticVersion.parse(mcVersion.loaderVersion);
				if (targetVersion.compareTo((Version) GitCraftConfig.YARN_CORRECTLY_ORIENTATED_MAPPINGS_VERSION) < 0) {
					MiscHelper.println("Yarn mappings for version %s are known to have switched namespaces", mcVersion.version);
					MappingReader.read(mappingsFileUnmerged, new MappingNsRenamer(nsSwitchYarn, Map.of(MappingsNamespace.INTERMEDIARY.toString(), MappingsNamespace.NAMED.toString(), MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString())));
				} else {
					MappingReader.read(mappingsFileUnmerged, nsSwitchYarn);
				}
			} catch (VersionParsingException e) {
				throw new RuntimeException(e);
			}
			yarn_fixInnerClasses(mappingTree);
			try (MappingWriter writer = MappingWriter.create(mappingsFile, MappingFormat.TINY_2)) {
				MappingNsCompleter nsCompleter = new MappingNsCompleter(writer, Map.of(MappingsNamespace.NAMED.toString(), MappingsNamespace.INTERMEDIARY.toString()), true);
				MappingDstNsReorder dstReorder = new MappingDstNsReorder(nsCompleter, List.of(MappingsNamespace.INTERMEDIARY.toString(), MappingsNamespace.NAMED.toString()));
				MappingSourceNsSwitch sourceNsSwitch = new MappingSourceNsSwitch(dstReorder, MappingsNamespace.OFFICIAL.toString());
				mappingTree.accept(sourceNsSwitch);
			}
			return mappingsFile;
		} catch (IOException e) {
			throw new RuntimeException(e);
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

	private static Path mappingsPathYarnUnmerged(McVersion mcVersion, FabricYarnVersionMeta yarnVersion) {
		try {
			Path mappingsFileUnmerged = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s.tiny", mcVersion.version, yarnVersion.build()));
			if (!mappingsFileUnmerged.toFile().exists()) {
				Path mappingsFileUnmergedJar = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s.jar", mcVersion.version, yarnVersion.build()));
				RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetry(yarnVersion.makeMavenURLUnmergedV2(), mappingsFileUnmergedJar, null, "unmerged yarn mapping", mcVersion.version);
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mappingsFileUnmergedJar)) {
					Path mappingsPathInJar = fs.get().getPath("mappings", "mappings.tiny");
					Files.copy(mappingsPathInJar, mappingsFileUnmerged, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			return mappingsFileUnmerged;
		} catch (IOException | RuntimeException e) {
			MiscHelper.println("Yarn mappings in tiny-v2 format do not exist for %s, falling back to tiny-v1 mappings...", mcVersion.version);
			try {
				Path mappingsFileUnmergedv1 = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s-v1.tiny", mcVersion.version, yarnVersion.build()));
				if (!mappingsFileUnmergedv1.toFile().exists()) {
					Path mappingsFileUnmergedJarv1 = GitCraft.MAPPINGS.resolve(String.format("%s-yarn-unmerged-build.%s-v1.jar", mcVersion.version, yarnVersion.build()));
					RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetry(yarnVersion.makeMavenURLUnmergedV1(), mappingsFileUnmergedJarv1, null, "unmerged yarn mapping (v1 fallback)", mcVersion.version);
					try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mappingsFileUnmergedJarv1)) {
						Path mappingsPathInJar = fs.get().getPath("mappings", "mappings.tiny");
						Files.copy(mappingsPathInJar, mappingsFileUnmergedv1, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				return mappingsFileUnmergedv1;
			} catch (IOException e2) {
				MiscHelper.println("Yarn mappings for version %s cannot be fetched. Giving up after trying merged-v2, v2, and v1 mappings.", mcVersion.version);
				throw new RuntimeException(e);
			}
		}
	}

	private static String mappingsIntermediaryPathQuirkVersion(String version) {
		return GitCraftConfig.yarnInconsistentVersionNaming.getOrDefault(version, version);
	}

	private static Path mappingsPathIntermediary(McVersion mcVersion) {
		try {
			if (SemanticVersion.parse(mcVersion.loaderVersion).compareTo((Version) GitCraftConfig.INTERMEDIARY_MAPPINGS_START_VERSION) < 0) {
				return null;
			}
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
		Path mappingsFile = GitCraft.MAPPINGS.resolve(mcVersion.version + "-intermediary.tiny");
		if (!mappingsFile.toFile().exists()) {
			RemoteHelper.downloadToFileWithChecksumIfNotExistsNoRetry(RemoteHelper.urlencodedURL(String.format("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/%s.tiny", mappingsIntermediaryPathQuirkVersion(mcVersion.version))), mappingsFile, null, "intermediary mapping", mcVersion.version);
		}
		return mappingsFile;
	}

	private static Path mappingsPathMojMap(McVersion mcVersion) throws IOException {
		Path mappingsFile = GitCraft.MAPPINGS.resolve(mcVersion.version + "-moj.tiny");

		if (!mappingsFile.toFile().exists()) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();

			// Make official the source namespace
			MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.OFFICIAL.toString());

			try (BufferedReader clientBufferedReader = Files.newBufferedReader(mcVersion.artifacts.clientMappings().fetchArtifact().toPath(), StandardCharsets.UTF_8); BufferedReader serverBufferedReader = Files.newBufferedReader(mcVersion.artifacts.serverMappings().fetchArtifact().toPath(), StandardCharsets.UTF_8)) {
				ProGuardReader.read((Reader) clientBufferedReader, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
				ProGuardReader.read((Reader) serverBufferedReader, MappingsNamespace.NAMED.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
			}
			try (MappingWriter w = MappingWriter.create(mappingsFile, MappingFormat.TINY_2)) {
				mappingTree.accept(w);
			}
		}

		return mappingsFile;
	}
}
