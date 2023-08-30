package com.github.winplay02;

import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.data.McVersion;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MinecraftVersionGraph implements Iterable<McVersion> {

	public static final String REPO_TAG_MOJMAP = "mojmap";

	private static HashMap<String, String> semverCache = null;

	private MinecraftVersionGraph() {
	}

	private MinecraftVersionGraph(MinecraftVersionGraph previous, Predicate<Map.Entry<SemanticVersion, McVersion>> predicate, String... tags) {
		this.repoTags = new HashSet<>(previous.repoTags);
		this.overriddenEdges = new HashMap<>(previous.overriddenEdges);
		this.versions = previous.versions.entrySet().stream().filter(predicate)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
					MiscHelper.panic("Duplicate keys in tree map");
					return null;
				}, TreeMap::new));
		this.nonLinearVersions = previous.nonLinearVersions.entrySet().stream().filter(predicate)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
					MiscHelper.panic("Duplicate keys in tree map");
					return null;
				}, TreeMap::new));
		this.repoTags.addAll(Arrays.asList(tags));
		reconnectGraph(previous);
	}

	private void reconnectGraph(MinecraftVersionGraph previous) {
		McVersion root = this.getRootVersion();
		for (McVersion version : this) {
			if (this.getPreviousNode(version).isEmpty() && !version.equals(root)) {
				McVersion nearest_previous_node = previous.getPreviousNode(version).orElse(null);
				if (nearest_previous_node == null) {
					MiscHelper.panic("Previous graph was already not properly connected");
				}
				while (!this.containsVersion(nearest_previous_node)) {
					nearest_previous_node = previous.getPreviousNode(nearest_previous_node).orElse(null);
				}
				this.overriddenEdges.put(version, nearest_previous_node);
			}
		}
		testGraphConnectivity();
	}

	private boolean testGraphConnectivity() {
		McVersion root = this.getRootVersion();
		for (McVersion version : this) {
			if (this.getPreviousNode(version).isEmpty() && !version.equals(root)) {
				MiscHelper.panic("Version %s is not connected any other version in the graph", version.version);
				return false;
			}
		}
		return true;
	}

	protected static final Pattern LINEAR_SNAPSHOT_REGEX = Pattern.compile("(^\\d\\dw\\d\\d[a-z]$)|(^\\d.\\d+(.\\d+)?(-(pre|rc)\\d|_[a-z_\\-]+snapshot-\\d+)$)");

	public static boolean isVersionNonLinearSnapshot(McVersion mcVersion) {
		return mcVersion.snapshot && !(LINEAR_SNAPSHOT_REGEX.matcher(mcVersion.version).matches());
	}

	public static String fixupSemver(String proposedSemVer) {
		if (Objects.equals(proposedSemVer, "1.19-22.w.13.oneBlockAtATime")) {
			return "1.19-alpha.22.13.oneblockatatime";
		}
		if (Objects.equals(proposedSemVer, "1.16.2-Combat.Test.8")) { // this is wrong here, fabric gets it correct
			return "1.16.3-combat.8";
		}
		if (proposedSemVer.contains("-Experimental")) {
			return proposedSemVer.replace("-Experimental", "-alpha.0.0.Experimental");
		}
		return proposedSemVer;
	}

	private static void loadSemverCache() {
		if (semverCache == null) {
			Path cachePath = GitCraft.CURRENT_WORKING_DIRECTORY.resolve("semver-cache.json");
			if (cachePath.toFile().exists()) {
				try {
					semverCache = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(cachePath), SerializationHelper.TYPE_HASH_MAP_STRING_STRING);
				} catch (IOException e) {
					semverCache = new HashMap<>();
					MiscHelper.println("This is not a fatal error: %s", e);
				}
			} else {
				semverCache = new HashMap<>();
			}
		}
	}

	public static void lookupLoaderVersion(McVersion mcVersion) {
		if (mcVersion.loaderVersion == null) {
			loadSemverCache();
			if (semverCache.containsKey(mcVersion.version)) {
				try {
					SemanticVersion.parse(semverCache.get(mcVersion.version));
					mcVersion.loaderVersion = mcVersion.version;
					return;
				} catch (VersionParsingException ignored) {
				}
			}
			net.fabricmc.loader.impl.game.minecraft.McVersion x = null;
			Path x_path = null;
			while (x == null) {
				try {
					x_path = mcVersion.artifacts.clientJar().fetchArtifact().toPath();
					x = McVersionLookup.getVersion(List.of(x_path), mcVersion.mainClass, null);
				} catch (Exception e) {
					MiscHelper.println("Semver lookup failed. Retrying... ");
					if (x_path != null) {
						x_path.toFile().delete();
					}
					MiscHelper.sleep(250);
				}
			}
			mcVersion.loaderVersion = fixupSemver(x.getNormalized());
			MiscHelper.println("Semver mapped for: %s as %s", x.getRaw(), mcVersion.loaderVersion);
		}
	}

	// TODO write to JSON
	public static String findPreviousBaseNodeVersionNameForNonLinearVersionString(McVersion mcVersion) {
		switch (mcVersion.loaderVersion) {
			// Combat
			case "1.15-rc.3.combat.4" -> {
				return "1.15-rc.3";
			}
			case "1.15.2-rc.2.combat.5" -> {
				return "1.15.2-rc.2";
			}
			case "1.16.2-beta.3.combat.6" -> {
				return "1.16.2-beta.3";
			}
			case "1.16.3-combat.7" -> {
				return "1.16.2";
			}
			case "1.16.3-combat.7.b" -> {
				return "1.16.3-combat.7";
			}
			case "1.16.3-combat.7.c" -> {
				return "1.16.3-combat.7.b";
			}
			case "1.16.3-combat.8" -> {
				return "1.16.3-combat.7.c";
			}
			case "1.16.3-combat.8.b" -> {
				return "1.16.3-combat.8";
			}
			case "1.16.3-combat.8.c" -> {
				return "1.16.3-combat.8.b";
			}
			// April
			case "1.16-alpha.20.13.inf" -> {
				return "1.16-alpha.20.13.b";
			}
			case "1.19-alpha.22.13.oneblockatatime" -> {
				return "1.19-alpha.22.13.a";
			}
			case "1.20-alpha.23.13.ab" -> {
				return "1.20-alpha.23.13.a";
			}
			default -> {
				return null;
			}
		}
	}

	public HashSet<String> repoTags = new HashSet<>();
	public TreeMap<SemanticVersion, McVersion> versions = new TreeMap<>();
	public TreeMap<SemanticVersion, McVersion> nonLinearVersions = new TreeMap<>();
	public HashMap<McVersion, McVersion> overriddenEdges = new HashMap<>();

	protected static SemanticVersion parseFromLoaderVersion(String loaderVersion) {
		try {
			return SemanticVersion.parse(loaderVersion);
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}

	public static MinecraftVersionGraph createFromMetadata(LinkedHashMap<String, McVersion> metadata) {
		MinecraftVersionGraph graph = new MinecraftVersionGraph();
		for (McVersion version : metadata.values()) {
			if (!version.hasMappings) {
				// TODO other mappings
				continue;
			}
			lookupLoaderVersion(version);
			if (MinecraftVersionGraph.isVersionNonLinearSnapshot(version)) {
				graph.nonLinearVersions.put(parseFromLoaderVersion(version.loaderVersion), version);
			} else {
				graph.versions.put(parseFromLoaderVersion(version.loaderVersion), version);
			}
		}
		graph.testGraphConnectivity();
		try {
			graph.writeSemverCache();
		} catch (IOException e) {
			MiscHelper.println("This is not a fatal error: %s", e);
		}
		return graph;
	}

	public MinecraftVersionGraph filterMojMapped() {
		return new MinecraftVersionGraph(this, (entry -> entry.getValue().hasMappings), REPO_TAG_MOJMAP);
	}

	public MinecraftVersionGraph filterMainlineVersions() {
		MinecraftVersionGraph graph = new MinecraftVersionGraph(this, (_entry -> true));
		graph.nonLinearVersions.clear();
		return graph;
	}

	public MinecraftVersionGraph filterMinVersion(McVersion version) {
		SemanticVersion subjectVersion = parseFromLoaderVersion(version.loaderVersion);
		return new MinecraftVersionGraph(this, (entry -> entry.getKey().compareTo((Version) subjectVersion) >= 0), String.format("min-%s", version.version));
	}

	public MinecraftVersionGraph filterOnlyVersion(McVersion... version) {
		List<McVersion> versionList = Arrays.asList(version);
		return new MinecraftVersionGraph(this, (entry -> versionList.contains(entry.getValue())), versionList.stream().map((mcv) -> mcv.version).collect(Collectors.joining("-")));
	}

	public McVersion getRootVersion() {
		Optional<McVersion> rootVersion = stream().findFirst();
		if (rootVersion.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return rootVersion.get();
	}

	public Optional<McVersion> getPreviousNode(McVersion version) {
		if (overriddenEdges.containsKey(version)) {
			return Optional.of(overriddenEdges.get(version));
		}
		if (!isVersionNonLinearSnapshot(version)) {
			return Optional.ofNullable(versions.lowerEntry(parseFromLoaderVersion(version.loaderVersion))).map(Map.Entry::getValue);
		}
		return Optional.ofNullable(getMinecraftVersionByLoaderName(findPreviousBaseNodeVersionNameForNonLinearVersionString(version)));
	}

	public McVersion getMinecraftMainlineVersionByName(String version_name) {
		if (version_name == null) {
			return null;
		}
		for (McVersion value : versions.values()) {
			if (value.version.equalsIgnoreCase(version_name)) {
				return value;
			}
		}
		return null;
	}

	public McVersion getMinecraftVersionByName(String version_name) {
		if (version_name == null) {
			return null;
		}
		for (McVersion value : versions.values()) {
			if (value.version.equalsIgnoreCase(version_name)) {
				return value;
			}
		}
		for (McVersion value : nonLinearVersions.values()) {
			if (value.version.equalsIgnoreCase(version_name)) {
				return value;
			}
		}
		return null;
	}

	public McVersion getMinecraftVersionByLoaderName(String loader_name) {
		if (loader_name == null) {
			return null;
		}
		for (McVersion value : versions.values()) {
			if (value.loaderVersion.equalsIgnoreCase(loader_name)) {
				return value;
			}
		}
		for (McVersion value : nonLinearVersions.values()) {
			if (value.loaderVersion.equalsIgnoreCase(loader_name)) {
				return value;
			}
		}
		return null;
	}

	public boolean containsVersion(McVersion version) {
		return version != null && this.stream().anyMatch((graphVersion) -> graphVersion.equals(version));
	}

	public String repoTagsIdentifier() {
		List<String> sortedTags = new ArrayList<>();
		if (repoTags.contains(REPO_TAG_MOJMAP)) {
			sortedTags.add(REPO_TAG_MOJMAP);
		}
		sortedTags.addAll(this.repoTags.stream().filter(tag -> !tag.equals(REPO_TAG_MOJMAP)).toList());
		return String.join("-", sortedTags);
	}

	public Stream<McVersion> stream() {
		return Stream.concat(versions.values().stream(), nonLinearVersions.values().stream());
	}

	@Override
	public Iterator<McVersion> iterator() {
		return stream().iterator();
	}

	public void writeSemverCache() throws IOException {
		Map<String, String> semverCache = new HashMap<>();
		for (McVersion version : this) {
			semverCache.put(version.version, version.loaderVersion);
		}
		SerializationHelper.writeAllToPath(GitCraft.CURRENT_WORKING_DIRECTORY.resolve("semver-cache.json"), SerializationHelper.serialize(semverCache));
	}
}
