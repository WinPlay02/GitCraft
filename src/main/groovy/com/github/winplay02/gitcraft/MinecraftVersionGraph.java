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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.Stack;
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
    this.validateNoCycles()
		this.findBranchStructure();
	}

	private void validateNoCycles() {
		OrderedVersion debugInfoMaxVersion = null;
		Map<OrderedVersion, Integer> visitedInformation = new HashMap<>();
		Stack<OrderedVersion> nodesStack = new Stack<>();
		// Roots
		for (OrderedVersion root : this.findRootVersions()) {
			nodesStack.push(root);
			visitedInformation.put(root, 1 /*STARTED*/);
		}
		// Depth First Search
		while (!nodesStack.isEmpty()) {
			OrderedVersion subject = nodesStack.peek();
			int pushed = 0;
			for (OrderedVersion following : this.getFollowingNodes(subject)) {
				if (!visitedInformation.containsKey(following)) {
					nodesStack.push(following);
					visitedInformation.put(following, 1 /*STARTED*/);
					++pushed;
					debugInfoMaxVersion = following;
					break;
				} else if (visitedInformation.get(following) == 1 /*STARTED*/) {
					MiscHelper.panic("Found a cycle in version graph at version: %s (%s)", subject.versionInfo().id(), subject.semanticVersion());
				} else {
					continue; // ENDED
				}
			}
			if (pushed == 0) {
				OrderedVersion handled = nodesStack.pop();
				visitedInformation.put(handled, 2 /*ENDED*/);
			}
		}
		if (visitedInformation.size() != this.edgesBack.size()) {
			MiscHelper.panic("During validating of the version graph, not all versions were visited. This is most likely caused by an inconsistency, e.g. a cycle in the graph. Last version checked: %s (problem may be near that version)", debugInfoMaxVersion != null ? debugInfoMaxVersion.versionInfo().id() : "<unknown>");
		}
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

	private void findBranchStructure() {
		this.roots = this.findRootVersions();

		this.pathsToRoot = new HashMap<>();
		this.pathsToTip = new HashMap<>();

		Map<OrderedVersion, Integer> pathsToRoot = new HashMap<>();
		Map<OrderedVersion, Integer> pathsToTip = new HashMap<>();
		Set<OrderedVersion> branchPoints = new HashSet<>();

		Set<OrderedVersion> tips = this.findPathLengths(this.roots, pathsToRoot, branchPoints, this::getFollowingNodes);
		Set<OrderedVersion> roots = this.findPathLengths(tips, pathsToTip, branchPoints, this::getPreviousNodes);

		if (!roots.equals(this.roots)) {
			MiscHelper.panic("Minecraft version graph is inconsistently structured! Tree roots: %s. Walk roots: %s.", this.roots, roots);
		}

		this.markBranchPoints(pathsToRoot, pathsToTip, branchPoints);
		this.markBranches(pathsToRoot, pathsToTip);
	}

	private Set<OrderedVersion> findPathLengths(Set<OrderedVersion> startingLayer, Map<OrderedVersion, Integer> pathLengths, Set<OrderedVersion> branchPoints, Function<OrderedVersion, Set<OrderedVersion>> nextVersionsGetter) {
		// do a breadth first walk away from starting layer and
		// set the path length for each version
		// we end up with a map storing the longest path to the
		// starting layer for each version

		Set<OrderedVersion> currentLayer = new LinkedHashSet<>(startingLayer);
		Set<OrderedVersion> nextLayer = new LinkedHashSet<>();
		Set<OrderedVersion> ends = new LinkedHashSet<>();

		int pathLength = 0;

		while (!currentLayer.isEmpty()) {
			nextLayer.clear();

			for (OrderedVersion version : currentLayer) {
				Set<OrderedVersion> nextVersions = nextVersionsGetter.apply(version);

				if (nextVersions.isEmpty()) {
					ends.add(version);
				} else {
					nextLayer.addAll(nextVersions);

					if (nextVersions.size() > 1) {
						branchPoints.add(version);
					}
				}

				pathLengths.put(version, pathLength);
			}

			currentLayer.clear();
			currentLayer.addAll(nextLayer);

			pathLength++;
		}

		return ends;
	}

	private void markBranchPoints(Map<OrderedVersion, Integer> allPathsToRoot, Map<OrderedVersion, Integer> allPathsToTip, Set<OrderedVersion> branchPoints) {
		for (OrderedVersion version : branchPoints) {
			Set<OrderedVersion> prevVersions = this.getPreviousNodes(version);
			Set<OrderedVersion> nextVersions = this.getFollowingNodes(version);

			if (prevVersions.size() > 1) {
				for (OrderedVersion prevVersion : prevVersions) {
					this.pathsToRoot.put(prevVersion, allPathsToRoot.get(prevVersion));
				}
			}
			if (nextVersions.size() > 1) {
				for (OrderedVersion nextVersion : nextVersions) {
					// path length to root is used as tie breaker for these branch points
					this.pathsToRoot.put(nextVersion, allPathsToRoot.get(nextVersion));
					this.pathsToTip.put(nextVersion, allPathsToTip.get(nextVersion));
				}
			}
		}

		// used to sort roots
		for (OrderedVersion root : this.roots) {
			this.pathsToTip.put(root, allPathsToTip.get(root));
		}
	}

	private void markBranches(Map<OrderedVersion, Integer> allPathsToRoot, Map<OrderedVersion, Integer> allPathsToTip) {
		Set<OrderedVersion> currentBranches = new TreeSet<>((v1, v2) -> {
			int c = this.pathsToTip.getOrDefault(v1, 0) - this.pathsToTip.getOrDefault(v2, 0);
			if (c == 0) {
				c = this.pathsToRoot.getOrDefault(v1, 0) - this.pathsToRoot.getOrDefault(v2, 0);
			}
			return c;
		});
		Set<OrderedVersion> nextBranches = new LinkedHashSet<>();
		Set<OrderedVersion> visited = new HashSet<>();

		currentBranches.addAll(this.roots);

		for (boolean mainBranch = true; !currentBranches.isEmpty(); mainBranch = false) {
			nextBranches.clear();

			for (OrderedVersion version : currentBranches) {
				while (version != null && visited.add(version)) {
					Set<OrderedVersion> nextVersions = this.getFollowingNodes(version);

					version = null;
					int longestPathToRoot = -1;
					int longestPathToTip = -1;

					for (OrderedVersion nextVersion : nextVersions) {
						if (this.shouldExcludeFromMainBranch(nextVersion) && (nextVersions.size() > 1 || !this.pathsToTip.containsKey(nextVersion))) {
							this.pathsToTip.put(nextVersion, allPathsToTip.get(nextVersion));

							if (mainBranch) {
								continue;
							} else {
								this.pathsToTip.remove(nextVersion);
							}
						}

						// if path length is not present, then this version
						// was already marked as lying on a branch
						int pathToRoot = this.pathsToRoot.getOrDefault(nextVersion, Integer.MAX_VALUE);
						int pathToTip = this.pathsToTip.getOrDefault(nextVersion, Integer.MAX_VALUE);

						if (pathToTip == longestPathToTip ? pathToRoot > longestPathToRoot : pathToTip > longestPathToTip) {
							version = nextVersion;
							longestPathToRoot = pathToRoot;
							longestPathToTip = pathToTip;
						}
					}

					if (version != null && (nextVersions.size() > 1 || !this.shouldExcludeFromMainBranch(version))) {
						this.pathsToTip.remove(version);
					}

					for (OrderedVersion nextVersion : nextVersions) {
						if (nextVersion != version) {
							nextBranches.add(nextVersion);
						}
					}
				}
			}

			currentBranches.clear();
			currentBranches.addAll(nextBranches);
		}
	}

	private boolean shouldExcludeFromMainBranch(OrderedVersion mcVersion) {
		return GitCraft.config.manifestSource.getMetadataProvider().shouldExcludeFromMainBranch(mcVersion);
	}

	public HashSet<String> repoTags = new HashSet<>();
	public Set<OrderedVersion> roots;
	public HashMap<OrderedVersion, Integer> pathsToTip = new HashMap<>();
	public HashMap<OrderedVersion, Integer> pathsToRoot = new HashMap<>();
	public HashMap<OrderedVersion, TreeSet<OrderedVersion>> edgesBack = new HashMap<>();
	public HashMap<OrderedVersion, TreeSet<OrderedVersion>> edgesFw = new HashMap<>();

	public static MinecraftVersionGraph createFromMetadata(MetadataProvider provider) throws IOException {
		MinecraftVersionGraph graph = new MinecraftVersionGraph();
		// Compatibility with existing repositories
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
		graph.validateNoCycles();
		graph.findBranchStructure();
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

	private Set<OrderedVersion> findRootVersions() {
		return this.edgesBack.entrySet()
			.stream()
			.filter(entry -> entry.getValue().isEmpty())
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	public Set<OrderedVersion> getRootVersions() {
		if (this.roots.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return this.roots;
	}

	public OrderedVersion getMainRootVersion() {
		if (this.roots.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return this.roots.stream().max((v1, v2) -> this.pathsToTip.get(v1) - this.pathsToTip.get(v2)).get();
	}

	public NavigableSet<OrderedVersion> getPreviousNodes(OrderedVersion version) {
		return Collections.unmodifiableNavigableSet(this.edgesBack.containsKey(version) ? this.edgesBack.get(version) : Collections.emptyNavigableSet());
	}

	public NavigableSet<OrderedVersion> getFollowingNodes(OrderedVersion version) {
		return Collections.unmodifiableNavigableSet(this.edgesFw.containsKey(version) ? this.edgesFw.get(version) : Collections.emptyNavigableSet());
	}

	public boolean isOnMainBranch(OrderedVersion mcVersion) {
		return this.roots.contains(this.walkBackToBranchPoint(mcVersion));
	}

	public OrderedVersion walkBackToRoot(OrderedVersion mcVersion) {
		return this.walkBackToBranchPoint(mcVersion, true);
	}

	public OrderedVersion walkBackToBranchPoint(OrderedVersion mcVersion) {
		return this.walkBackToBranchPoint(mcVersion, false);
	}

	private OrderedVersion walkBackToBranchPoint(OrderedVersion mcVersion, boolean root) {
		// path lengths are only stored for branch points and roots
		if (!root && this.pathsToTip.containsKey(mcVersion)) {
			return mcVersion;
		}

		Set<OrderedVersion> branches = this.getPreviousNodes(mcVersion);

		if (branches.size() == 1) {
			return this.walkBackToBranchPoint(branches.iterator().next());
		}

		// version is not a branch point or root, and number of prev versions
		// is not 1, so then there must be multiple
		// pick the version that lies on a branch if there is one, and walk
		// further along that path, otherwise pick the version with the longest
		// path to a tip

		OrderedVersion longestBranch = null;
		int longestPathToRoot = -1;
		int longestPathToTip = -1;

		for (OrderedVersion branch : branches) {
			// if path length to tip is not present, then this version
			// was already marked as lying on a branch
			if (!this.pathsToTip.containsKey(branch)) {
				return this.walkBackToBranchPoint(branch);
			}

			int pathToRoot = this.pathsToRoot.get(branch);
			int pathToTip = this.pathsToTip.get(branch);

			if (pathToTip == longestPathToTip ? pathToRoot > longestPathToRoot : pathToTip > longestPathToTip) {
				longestBranch = branch;
				longestPathToRoot = pathToRoot;
				longestPathToTip = pathToTip;
			}
		}

		return longestBranch == null ? mcVersion : longestBranch;
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
		HashSet<OrderedVersion> nextVersions = new HashSet<>(findRootVersions());
		HashSet<OrderedVersion> emittedVersions = new HashSet<>();
		HashSet<OrderedVersion> temporarySet = new HashSet<>(findRootVersions());
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
