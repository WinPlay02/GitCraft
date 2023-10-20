package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.io.IOException;
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

public class MinecraftVersionGraph implements Iterable<OrderedVersion> {


	private MinecraftVersionGraph() {
	}

	private MinecraftVersionGraph(MinecraftVersionGraph previous, Predicate<Map.Entry<SemanticVersion, OrderedVersion>> predicate, String... tags) {
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
		OrderedVersion root = this.getRootVersion();
		for (OrderedVersion version : this) {
			if (this.getPreviousNode(version).isEmpty() && !version.equals(root)) {
				OrderedVersion nearest_previous_node = previous.getPreviousNode(version).orElse(null);
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
		OrderedVersion root = this.getRootVersion();
		for (OrderedVersion version : this) {
			if (this.getPreviousNode(version).isEmpty() && !version.equals(root)) {
				MiscHelper.panic("Version %s is not connected any other version in the graph", version.launcherFriendlyVersionName());
				return false;
			}
		}
		return true;
	}

	protected static final Pattern LINEAR_SNAPSHOT_REGEX = Pattern.compile("(^\\d\\dw\\d\\d[a-z]$)|(^\\d.\\d+(.\\d+)?(-(pre|rc)\\d+|_[a-z_\\-]+snapshot-\\d+| Pre-Release \\d+)?$)");

	public static boolean isVersionNonLinearSnapshot(OrderedVersion mcVersion) {
		return mcVersion.isSnapshotOrPending() && (Objects.equals(mcVersion.launcherFriendlyVersionName(), "15w14a") || !(LINEAR_SNAPSHOT_REGEX.matcher(mcVersion.launcherFriendlyVersionName()).matches())); // mark 15w14a explicit as april fools snapshot, since this case should not be covered by the regex
	}

	// TODO write to JSON
	public static String findPreviousBaseNodeVersionNameForNonLinearVersionString(OrderedVersion mcVersion) {
		switch (mcVersion.semanticVersion()) {
			// Combat
			case "1.14.3-rc.4.combat.1" -> {
				return "1.14.3-rc.4";
			}
			case "1.14.5-combat.2" -> {
				return "1.14.4";
			}
			case "1.14.5-combat.3" -> {
				return "1.14.5-combat.2";
			}
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
			case "1.8.4-alpha.15.14.a+loveandhugs" -> {
				return "1.8.3";
			}
			case "1.9.2-rv+trendy" -> {
				return "1.9.2";
			}
			case "1.14-alpha.19.13.shareware" -> {
				return "1.14-alpha.19.13.b";
			}
			case "1.16-alpha.20.13.inf" -> {
				return "1.16-alpha.20.13.b";
			}
			case "1.19-alpha.22.13.oneblockatatime" -> {
				return "1.19-alpha.22.13.a";
			}
			case "1.20-alpha.23.13.ab" -> {
				return "1.20-alpha.23.13.a";
			}
			case "1.20-alpha.23.13.ab.original" -> {
				return "1.20-alpha.23.13.a";
			}
			default -> {
				return null;
			}
		}
	}

	public HashSet<String> repoTags = new HashSet<>();
	public TreeMap<SemanticVersion, OrderedVersion> versions = new TreeMap<>();
	public TreeMap<SemanticVersion, OrderedVersion> nonLinearVersions = new TreeMap<>();
	public HashMap<OrderedVersion, OrderedVersion> overriddenEdges = new HashMap<>();

	protected static SemanticVersion parseFromLoaderVersion(String loaderVersion) {
		try {
			return SemanticVersion.parse(loaderVersion);
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}

	public static MinecraftVersionGraph createFromMetadata(LinkedHashMap<String, OrderedVersion> metadata) {
		MinecraftVersionGraph graph = new MinecraftVersionGraph();
		for (OrderedVersion version : metadata.values()) {
			if (MinecraftVersionGraph.isVersionNonLinearSnapshot(version)) {
				graph.nonLinearVersions.put(parseFromLoaderVersion(version.semanticVersion()), version);
			} else {
				graph.versions.put(parseFromLoaderVersion(version.semanticVersion()), version);
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

	public MinecraftVersionGraph filterMapping(MappingFlavour mappingFlavour, MappingFlavour[] mappingFallback) {
		return new MinecraftVersionGraph(this, (entry -> mappingFlavour.getMappingImpl().doMappingsExist(entry.getValue()) || (mappingFallback != null && mappingFallback.length > 0 && Arrays.stream(mappingFallback).anyMatch(mapping -> mapping.getMappingImpl().doMappingsExist(entry.getValue())))));
	}

	public MinecraftVersionGraph filterMainlineVersions() {
		MinecraftVersionGraph graph = new MinecraftVersionGraph(this, (_entry -> true));
		graph.nonLinearVersions.clear();
		return graph;
	}

	public MinecraftVersionGraph filterMinVersion(OrderedVersion version) {
		SemanticVersion subjectVersion = parseFromLoaderVersion(version.semanticVersion());
		return new MinecraftVersionGraph(this, (entry -> entry.getKey().compareTo((Version) subjectVersion) >= 0), String.format("min-%s", version.launcherFriendlyVersionName()));
	}

	public MinecraftVersionGraph filterOnlyVersion(OrderedVersion... version) {
		List<OrderedVersion> versionList = Arrays.asList(version);
		return new MinecraftVersionGraph(this, (entry -> versionList.contains(entry.getValue())), versionList.stream().map(OrderedVersion::launcherFriendlyVersionName).collect(Collectors.joining("-")));
	}

	public MinecraftVersionGraph filterExcludeVersion(OrderedVersion... version) {
		List<OrderedVersion> versionList = Arrays.asList(version);
		if (versionList.isEmpty()) {
			return this;
		}
		return new MinecraftVersionGraph(this, (entry -> !versionList.contains(entry.getValue())), "exclude-" + versionList.stream().map(OrderedVersion::launcherFriendlyVersionName).collect(Collectors.joining("-")));
	}

	public MinecraftVersionGraph filterStableRelease() {
		return new MinecraftVersionGraph(this, (entry -> !entry.getValue().isSnapshotOrPending()), "stable");
	}

	public MinecraftVersionGraph filterSnapshots() {
		return new MinecraftVersionGraph(this, (entry -> entry.getValue().isSnapshotOrPending()), "snapshot");
	}

	public OrderedVersion getRootVersion() {
		Optional<OrderedVersion> rootVersion = stream().findFirst();
		if (rootVersion.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return rootVersion.get();
	}

	public Optional<OrderedVersion> getPreviousNode(OrderedVersion version) {
		if (overriddenEdges.containsKey(version)) {
			return Optional.of(overriddenEdges.get(version));
		}
		if (!isVersionNonLinearSnapshot(version)) {
			return Optional.ofNullable(versions.lowerEntry(parseFromLoaderVersion(version.semanticVersion()))).map(Map.Entry::getValue);
		}
		return Optional.ofNullable(getMinecraftVersionByLoaderName(findPreviousBaseNodeVersionNameForNonLinearVersionString(version)));
	}

	public OrderedVersion getMinecraftMainlineVersionByName(String version_name) {
		if (version_name == null) {
			return null;
		}
		for (OrderedVersion value : versions.values()) {
			if (value.launcherFriendlyVersionName().equalsIgnoreCase(version_name)) {
				return value;
			}
		}
		return null;
	}

	public OrderedVersion getMinecraftVersionByName(String version_name) {
		if (version_name == null) {
			return null;
		}
		for (OrderedVersion value : versions.values()) {
			if (value.launcherFriendlyVersionName().equalsIgnoreCase(version_name)) {
				return value;
			}
		}
		for (OrderedVersion value : nonLinearVersions.values()) {
			if (value.launcherFriendlyVersionName().equalsIgnoreCase(version_name)) {
				return value;
			}
		}
		return null;
	}

	public OrderedVersion getMinecraftVersionByLoaderName(String loader_name) {
		if (loader_name == null) {
			return null;
		}
		for (OrderedVersion value : versions.values()) {
			if (value.semanticVersion().equalsIgnoreCase(loader_name)) {
				return value;
			}
		}
		for (OrderedVersion value : nonLinearVersions.values()) {
			if (value.semanticVersion().equalsIgnoreCase(loader_name)) {
				return value;
			}
		}
		return null;
	}

	public boolean containsVersion(OrderedVersion version) {
		return version != null && this.stream().anyMatch((graphVersion) -> graphVersion.equals(version));
	}

	public String repoTagsIdentifier(MappingFlavour mappingFlavour, MappingFlavour[] mappingFallback) {
		List<String> sortedTags = new ArrayList<>();
		sortedTags.add(mappingFlavour.toString());
		if (mappingFallback != null && mappingFallback.length > 0) {
			sortedTags.add(String.format("fallback-%s", Arrays.stream(mappingFallback).map(Object::toString).collect(Collectors.joining("-"))));
		}
		sortedTags.addAll(this.repoTags.stream().filter(tag -> !tag.equals(mappingFlavour.toString())).toList());
		return String.join("-", sortedTags);
	}

	public Stream<OrderedVersion> stream() {
		return Stream.concat(versions.values().stream(), nonLinearVersions.values().stream());
	}

	@Override
	public Iterator<OrderedVersion> iterator() {
		return stream().iterator();
	}

	public void writeSemverCache() throws IOException {
		Map<String, String> semverCache = new TreeMap<>();
		for (OrderedVersion version : this) {
			semverCache.put(version.launcherFriendlyVersionName(), version.semanticVersion());
		}
		SerializationHelper.writeAllToPath(GitCraft.CURRENT_WORKING_DIRECTORY.resolve("semver-cache.json"), SerializationHelper.serialize(semverCache));
	}
}
