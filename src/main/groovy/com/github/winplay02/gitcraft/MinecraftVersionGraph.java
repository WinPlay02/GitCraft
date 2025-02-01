package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.MetadataProvider;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
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
		this.findMainBranch();
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
		if (amountRootNodes < 1) {
			MiscHelper.panic("There is no root node. This either means, that the version graph is empty, or that it contains a cycle.");
		}
	}

	private void findMainBranch() {
		this.roots.clear();
		this.branchPoints.clear();

		Set<OrderedVersion> roots = this.edgesBack.entrySet()
			.stream()
			.filter(entry -> entry.getValue().isEmpty())
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());

		for (OrderedVersion root : roots) {
			int length = this.findBranchPoints(root);
			this.roots.put(root, length);
		}
		for (OrderedVersion root : roots) {
			this.markMainBranch(root);
		}
	}

	private int findBranchPoints(OrderedVersion mcVersion) {
		int branchLength = this.branchPoints.getOrDefault(mcVersion, 0);

		// check if this branch point was already identified earlier
		if (branchLength > 0) {
			return branchLength;
		}

		Predicate<OrderedVersion> mainBranchPredicate = v -> !GitCraft.config.manifestSource.getMetadataProvider().shouldExcludeFromMainBranch(v);

		NavigableSet<OrderedVersion> nextBranches = this.getFollowingNodes(mcVersion);
		Set<OrderedVersion> potentialMainBranches = nextBranches.stream().filter(mainBranchPredicate).collect(Collectors.toSet());

		// find branch points in all following paths
		for (OrderedVersion nextBranch : nextBranches) {
			// find more branch points down this path
			// and find the length to the farthest tip
			int nextBranchLength = this.findBranchPoints(nextBranch);
			// some versions are *definitely* side branches
			// we only want to mark potential main branches
			// if there are more than one, since side branches
			// are not sanitized later
			if (mainBranchPredicate.test(nextBranch) || potentialMainBranches.size() > 1) {
				// this branch is marked as a branch point right now,
				// but the map will be sanitized later to remove versions
				// from the main branch
				this.branchPoints.put(nextBranch, nextBranchLength);
			}
		}

		return branchLength + 1;
	}

	private void markMainBranch(OrderedVersion mcVersion) {
		// after the branch points have been identified,  search for the longest path
		// from each of the roots to a tip, and mark that path as a main branch - that
		// is to say, remove any branch points that lay on these paths from the map,
		// as it should only contain side branches

		NavigableSet<OrderedVersion> nextBranches = this.getFollowingNodes(mcVersion);

		OrderedVersion mainBranch = null;
		int mainBranchLength = -1;

		// walk the main branch and continue along the longest path
		for (OrderedVersion branch : nextBranches) {
			// ignore versions that are *definitely* side branches
			if (GitCraft.config.manifestSource.getMetadataProvider().shouldExcludeFromMainBranch(branch)) {
				continue;
			}

			int branchLength = this.branchPoints.getOrDefault(branch, 0);

			if (branchLength > mainBranchLength) {
				mainBranch = branch;
				mainBranchLength = branchLength;
			}
		}

		if (mainBranch != null) {
			if (mainBranchLength > 0) {
				this.branchPoints.remove(mainBranch);
			}

			this.markMainBranch(mainBranch);
		}
	}

	public HashSet<String> repoTags = new HashSet<>();
	/** root nodes of the graph, mapped to the path lengths to the tips of those main branches */
	public HashMap<OrderedVersion, Integer> roots = new HashMap<>();
	public HashMap<OrderedVersion, TreeSet<OrderedVersion>> edgesBack = new HashMap<>();
	public HashMap<OrderedVersion, TreeSet<OrderedVersion>> edgesFw = new HashMap<>();
	/** nodes that mark new side branches, mapped to the path lengths to the tips of those side branches */
	public HashMap<OrderedVersion, Integer> branchPoints = new HashMap<>();

	public static MinecraftVersionGraph createFromMetadata(MetadataProvider provider) throws IOException {
		MinecraftVersionGraph graph = new MinecraftVersionGraph();
		if (provider.getSource() != ManifestSource.MOJANG) {
			graph.repoTags.add(String.format("manifest_%s", provider.getInternalName()));
		}
		TreeSet<OrderedVersion> metaVersions = new TreeSet<>(provider.getVersions().values());
		TreeSet<OrderedVersion> metaVersionsMainline = new TreeSet<>(provider.getVersions().values().stream().filter(value -> !provider.shouldExcludeFromMainBranch(value)).toList());
		Map<String, OrderedVersion> semverMetaVersions = provider.getVersions().values().stream().collect(Collectors.toMap(OrderedVersion::semanticVersion, Function.identity()));
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
		graph.findMainBranch();
		return graph;
	}

	public MinecraftVersionGraph filterMapping(MappingFlavour mappingFlavour, MappingFlavour[] mappingFallback) {
		return new MinecraftVersionGraph(this, (entry -> mappingFlavour.exists(entry) || (mappingFallback != null && mappingFallback.length > 0 && Arrays.stream(mappingFallback).anyMatch(mapping -> mapping.exists(entry)))));
	}

	public MinecraftVersionGraph filterMainlineVersions() {
		return new MinecraftVersionGraph(this, this::isOnMainBranch);
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

	public Set<OrderedVersion> getRootVersions() {
		if (this.roots.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return this.roots.keySet();
	}

	public OrderedVersion getDeepestRootVersion() {
		if (this.roots.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return this.roots.entrySet().stream().max((e1, e2) -> e1.getValue() - e2.getValue()).get().getKey();
	}

	public NavigableSet<OrderedVersion> getPreviousNodes(OrderedVersion version) {
		return Collections.unmodifiableNavigableSet(this.edgesBack.containsKey(version) ? this.edgesBack.get(version) : Collections.emptyNavigableSet());
	}

	public NavigableSet<OrderedVersion> getFollowingNodes(OrderedVersion version) {
		return Collections.unmodifiableNavigableSet(this.edgesFw.containsKey(version) ? this.edgesFw.get(version) : Collections.emptyNavigableSet());
	}

	public boolean isOnMainBranch(OrderedVersion mcVersion) {
		return this.walkToPreviousBranchPoint(mcVersion) == null;
	}

	public OrderedVersion walkToPreviousBranchPoint(OrderedVersion mcVersion) {
		if (this.branchPoints.containsKey(mcVersion)) {
			return mcVersion;
		}

		OrderedVersion longestBranch = null;
		int longestBranchLength = 0;

		for (OrderedVersion previousVersion : this.getPreviousNodes(mcVersion)) {
			OrderedVersion branchPoint = this.walkToPreviousBranchPoint(previousVersion);

			if (branchPoint == null) {
				// this version is part of the main branch
				return null;
			} else {
				int branchLength = this.branchPoints.get(branchPoint);

				if (branchLength > longestBranchLength) {
					longestBranch = branchPoint;
					longestBranchLength = branchLength;
				}
			}
		}

		return longestBranch;
	}

	public OrderedVersion walkToRoot(OrderedVersion mcVersion) {
		if (this.roots.containsKey(mcVersion)) {
			return mcVersion;
		}

		OrderedVersion longestBranch = null;
		int longestBranchLength = 0;

		for (OrderedVersion previousVersion : this.getPreviousNodes(mcVersion)) {
			OrderedVersion branchPoint = this.walkToRoot(previousVersion);

			if (branchPoint == null) {
				// this version is part of the main branch
				return null;
			} else {
				int branchLength = this.roots.get(branchPoint);

				if (branchLength > longestBranchLength) {
					longestBranch = branchPoint;
					longestBranchLength = branchLength;
				}
			}
		}

		return longestBranch;
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
