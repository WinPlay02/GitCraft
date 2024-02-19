package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.manifest.ManifestProvider;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MinecraftVersionGraph implements Iterable<OrderedVersion> {


	private MinecraftVersionGraph() {
	}

	private MinecraftVersionGraph(MinecraftVersionGraph previous, Predicate<OrderedVersion> predicate, String... tags) {
		this.repoTags = new HashSet<>(previous.repoTags);
		this.edgesBack = new HashMap<>(previous.edgesBack.keySet().stream().filter(predicate).collect(Collectors.toMap(Function.identity(), key -> new TreeSet<OrderedVersion>())));
		this.edgesFw = new HashMap<>(previous.edgesFw.keySet().stream().filter(predicate).collect(Collectors.toMap(Function.identity(), key -> new TreeSet<OrderedVersion>())));
		this.repoTags.addAll(Arrays.asList(tags));
		this.reconnectGraph(previous);
	}

	private void reconnectGraph(MinecraftVersionGraph previous) {
		TreeSet<OrderedVersion> allVersions = new TreeSet<>(this.edgesBack.keySet());
		for (OrderedVersion version : allVersions) {
			TreeSet<OrderedVersion> nearestPreviousNodes = new TreeSet<>(previous.getPreviousNodes(version));
			TreeSet<OrderedVersion> calculatedPreviousNodes = new TreeSet<>();
			//while (nearestPreviousNodes.stream().anyMatch(anyPreviousVersion -> !this.containsVersion(anyPreviousVersion))) {
			while (!nearestPreviousNodes.isEmpty()) {
				TreeSet<OrderedVersion> nextBestNodes = new TreeSet<>();
				for (OrderedVersion nearestPreviousNode : nearestPreviousNodes) {
					if (allVersions.contains(nearestPreviousNode)) {
						calculatedPreviousNodes.add(nearestPreviousNode);
					} else {
						nextBestNodes.addAll(previous.getPreviousNodes(nearestPreviousNode));
					}
				}
				nextBestNodes.removeIf(calculatedPreviousNodes::contains);
				nearestPreviousNodes = nextBestNodes;
			}
			// ADD
			this.edgesBack.computeIfAbsent(version, value -> new TreeSet<>()).addAll(calculatedPreviousNodes);
			for (OrderedVersion prevVersion : calculatedPreviousNodes) {
				this.edgesFw.computeIfAbsent(prevVersion, value -> new TreeSet<>()).add(version);
			}
		}

		for (OrderedVersion version : allVersions) {
			TreeSet<OrderedVersion> nearestNextNodes = new TreeSet<>(previous.getFollowingNodes(version));
			TreeSet<OrderedVersion> calculatedNextNodes = new TreeSet<>();
			//while (nearestNextNodes.stream().anyMatch(anyNextVersion -> !this.containsVersion(anyNextVersion))) {
			while (!nearestNextNodes.isEmpty()) {
				TreeSet<OrderedVersion> nextBestNodes = new TreeSet<>();
				for (OrderedVersion nearestNextNode : nearestNextNodes) {
					if (allVersions.contains(nearestNextNode)) {
						calculatedNextNodes.add(nearestNextNode);
					} else {
						nextBestNodes.addAll(previous.getFollowingNodes(nearestNextNode));
					}
				}
				nextBestNodes.removeIf(calculatedNextNodes::contains);
				nearestNextNodes = nextBestNodes;
			}
			// ADD
			//this.edgesFw.computeIfAbsent(version, value -> new TreeSet<>()).addAll(calculatedNextNodes);
			//for (OrderedVersion nextVersion : calculatedNextNodes) {
			//this.edgesBack.computeIfAbsent(nextVersion, value -> new TreeSet<>()).add(version);
			//}
		}
		testGraphConnectivity();
	}

	private void testGraphConnectivity() {
		for (OrderedVersion version : this.edgesBack.keySet()) {
			for (OrderedVersion prevVersion : this.getPreviousNodes(version)) {
				if (this.edgesFw.get(prevVersion) == null || !this.edgesFw.get(prevVersion).contains(version)) {
					MiscHelper.panic("VersionGraph is inconsistent. Version %s is not connected (forward direction) to %s", prevVersion.launcherFriendlyVersionName(), version.launcherFriendlyVersionName());
				}
			}
		}
		for (OrderedVersion version : this.edgesFw.keySet()) {
			for (OrderedVersion nextVersion : this.getFollowingNodes(version)) {
				if (this.edgesBack.get(nextVersion) == null || !this.edgesBack.get(nextVersion).contains(version)) {
					MiscHelper.panic("VersionGraph is inconsistent. Version %s is not connected (backward direction) to %s", nextVersion.launcherFriendlyVersionName(), version.launcherFriendlyVersionName());
				}
			}
		}
		long amountRootNodes = this.edgesBack.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).count();
		if (amountRootNodes != 1) {
			MiscHelper.panic(amountRootNodes < 1 ? "There is no root node. This either means, that the version graph is empty, or that it contains a cycle." : "There are multiple root nodes. A connected git history would not be guaranteed");
		}
	}

	protected static final Pattern LINEAR_SNAPSHOT_REGEX = Pattern.compile("(^\\d\\dw\\d\\d[a-z]$)|(^\\d.\\d+(.\\d+)?(-(pre|rc)\\d+|_[a-z_\\-]+snapshot-\\d+| Pre-Release \\d+)?$)");

	public static boolean isVersionNonLinearSnapshot(OrderedVersion mcVersion) {
		// remove all pending "snapshots" from mainline and mark as non-linear
		return mcVersion.isPending() || mcVersion.isSnapshotOrPending() && (Objects.equals(mcVersion.launcherFriendlyVersionName(), "15w14a") || !(LINEAR_SNAPSHOT_REGEX.matcher(mcVersion.launcherFriendlyVersionName()).matches())); // mark 15w14a explicit as april fools snapshot, since this case should not be covered by the regex
	}

	public HashSet<String> repoTags = new HashSet<>();
	public HashMap<OrderedVersion, TreeSet<OrderedVersion>> edgesBack = new HashMap<>();
	public HashMap<OrderedVersion, TreeSet<OrderedVersion>> edgesFw = new HashMap<>();

	public static MinecraftVersionGraph createFromMetadata(ManifestSource manifestSource, ManifestProvider<?, ?> provider) throws IOException {
		MinecraftVersionGraph graph = new MinecraftVersionGraph();
		if(manifestSource != ManifestSource.MOJANG_MINECRAFT_LAUNCHER) {
			graph.repoTags.add(String.format("manifest_%s", manifestSource.toString()));
		}
		TreeSet<OrderedVersion> metaVersions = new TreeSet<>(provider.getVersionMeta().values());
		TreeSet<OrderedVersion> metaVersionsMainline = new TreeSet<>(metaVersions.stream().filter(value -> !MinecraftVersionGraph.isVersionNonLinearSnapshot(value)).toList());
		Map<String, OrderedVersion> semverMetaVersions = metaVersions.stream().collect(Collectors.toMap(OrderedVersion::semanticVersion, Function.identity()));
		for (OrderedVersion version : metaVersions) {
			graph.edgesFw.computeIfAbsent(version, value -> new TreeSet<>());
			List<String> previousVersion = provider.getParentVersion(version);
			if (previousVersion == null) {
				OrderedVersion prevLinearVersion = metaVersionsMainline.lower(version);
				previousVersion = prevLinearVersion != null ? List.of(prevLinearVersion.semanticVersion()) : Collections.emptyList();
			}
			if (previousVersion.isEmpty()) {
				graph.edgesBack.computeIfAbsent(version, value -> new TreeSet<>());
				continue;
			}
			for (String parentVersionSemver : previousVersion) {
				OrderedVersion parentVersion = semverMetaVersions.get(parentVersionSemver);
				graph.edgesBack.computeIfAbsent(version, value -> new TreeSet<>()).add(parentVersion);
				graph.edgesFw.computeIfAbsent(parentVersion, value -> new TreeSet<>()).add(version);
			}
		}
		graph.testGraphConnectivity();
		return graph;
	}

	public MinecraftVersionGraph filterMapping(MappingFlavour mappingFlavour, MappingFlavour[] mappingFallback) {
		return new MinecraftVersionGraph(this, (entry -> mappingFlavour.getMappingImpl().doMappingsExist(entry) || (mappingFallback != null && mappingFallback.length > 0 && Arrays.stream(mappingFallback).anyMatch(mapping -> mapping.getMappingImpl().doMappingsExist(entry)))));
	}

	public MinecraftVersionGraph filterMainlineVersions() {
		return new MinecraftVersionGraph(this, (entry -> !MinecraftVersionGraph.isVersionNonLinearSnapshot(entry)));
	}

	public MinecraftVersionGraph filterMinVersion(OrderedVersion version) {
		return new MinecraftVersionGraph(this, (entry -> entry.compareTo(version) >= 0), String.format("min-%s", version.launcherFriendlyVersionName()));
	}

	public MinecraftVersionGraph filterMaxVersion(OrderedVersion version) {
		return new MinecraftVersionGraph(this, (entry -> entry.compareTo(version) <= 0), String.format("max-%s", version.launcherFriendlyVersionName()));
	}

	public MinecraftVersionGraph filterOnlyVersion(OrderedVersion... version) {
		TreeSet<OrderedVersion> versionList = new TreeSet<>(Arrays.asList(version));
		return new MinecraftVersionGraph(this, versionList::contains, versionList.stream().map(OrderedVersion::launcherFriendlyVersionName).collect(Collectors.joining("-")));
	}

	public MinecraftVersionGraph filterExcludeVersion(OrderedVersion... version) {
		TreeSet<OrderedVersion> versionList = new TreeSet<>(Arrays.asList(version));
		if (versionList.isEmpty()) {
			return this;
		}
		return new MinecraftVersionGraph(this, (entry -> !versionList.contains(entry)), "exclude-" + versionList.stream().map(OrderedVersion::launcherFriendlyVersionName).collect(Collectors.joining("-")));
	}

	public MinecraftVersionGraph filterStableRelease() {
		return new MinecraftVersionGraph(this, (entry -> !entry.isSnapshotOrPending()), "stable");
	}

	public MinecraftVersionGraph filterSnapshots() {
		return new MinecraftVersionGraph(this, OrderedVersion::isSnapshotOrPending, "snapshot");
	}

	public OrderedVersion getRootVersion() {
		Optional<OrderedVersion> rootVersion = stream().findFirst();
		if (rootVersion.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return rootVersion.get();
	}

	public NavigableSet<OrderedVersion> getPreviousNodes(OrderedVersion version) {
		return Collections.unmodifiableNavigableSet(this.edgesBack.get(version));
	}

	public NavigableSet<OrderedVersion> getFollowingNodes(OrderedVersion version) {
		return Collections.unmodifiableNavigableSet(this.edgesFw.get(version));
	}

	public OrderedVersion getMinecraftVersionByName(String versionName) {
		if (versionName == null) {
			return null;
		}
		return this.edgesBack.keySet().stream().filter(value -> value.launcherFriendlyVersionName().equalsIgnoreCase(versionName)).findFirst().orElse(null);
	}

	public OrderedVersion getMinecraftVersionBySemanticVersion(String semanticVersion) {
		if (semanticVersion == null) {
			return null;
		}
		return this.edgesBack.keySet().stream().filter(value -> value.semanticVersion().equalsIgnoreCase(semanticVersion)).findFirst().orElse(null);
	}

	public boolean containsVersion(OrderedVersion version) {
		return version != null && this.edgesBack.containsKey(version);
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
		HashSet<OrderedVersion> nextVersions = new HashSet<>(this.edgesBack.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).map(Map.Entry::getKey).toList());
		HashSet<OrderedVersion> emittedVersions = new HashSet<>();
		HashSet<OrderedVersion> temporarySet = new HashSet<>(this.edgesBack.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).map(Map.Entry::getKey).toList());
		Stream.Builder<OrderedVersion> builder = Stream.builder();
		while (!nextVersions.isEmpty()) {
			for (OrderedVersion version : nextVersions) {
				if (this.getPreviousNodes(version).stream().anyMatch(versionPrev -> !emittedVersions.contains(versionPrev))) {
					temporarySet.add(version);
					continue;
				}
				builder.accept(version);
				emittedVersions.add(version);
				temporarySet.addAll(this.edgesFw.get(version));
			}
			nextVersions.clear();
			nextVersions.addAll(temporarySet.stream().filter(version -> !emittedVersions.contains(version)).toList());
			temporarySet.clear();
		}
		return builder.build();
	}

	@Override
	public Iterator<OrderedVersion> iterator() {
		return stream().iterator();
	}
}
