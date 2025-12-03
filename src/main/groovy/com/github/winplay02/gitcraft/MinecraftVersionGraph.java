package com.github.winplay02.gitcraft;

import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.MetadataProvider;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.unpick.UnpickFlavour;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MinecraftVersionGraph extends AbstractVersionGraph<OrderedVersion> {


	private MinecraftVersionGraph() {
		super();
	}

	private MinecraftVersionGraph(MinecraftVersionGraph previous, Predicate<OrderedVersion> predicate, String... tags) {
		super(previous, predicate, tags);
		this.findBranchStructure();
	}

	private void findBranchStructure() {
		this.roots = this.findRootVertices();

		this.pathsToRoot = new HashMap<>();
		this.pathsToTip = new HashMap<>();

		Map<OrderedVersion, Integer> pathsToRoot = new HashMap<>();
		Map<OrderedVersion, Integer> pathsToTip = new HashMap<>();
		Set<OrderedVersion> branchPoints = new HashSet<>();

		Set<OrderedVersion> tips = this.findPathLengths(this.roots, pathsToRoot, branchPoints, this::getFollowingVertices);
		Set<OrderedVersion> roots = this.findPathLengths(tips, pathsToTip, branchPoints, this::getPreviousVertices);

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
			Set<OrderedVersion> prevVersions = this.getPreviousVertices(version);
			Set<OrderedVersion> nextVersions = this.getFollowingVertices(version);

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
					Set<OrderedVersion> nextVersions = this.getFollowingVertices(version);

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
		return GitCraft.getApplicationConfiguration().manifestSource().getMetadataProvider().shouldExcludeFromMainBranch(mcVersion);
	}

	public HashMap<OrderedVersion, Integer> pathsToTip = new HashMap<>();
	public HashMap<OrderedVersion, Integer> pathsToRoot = new HashMap<>();

	public static MinecraftVersionGraph createFromMetadata(Executor executor, MetadataProvider<OrderedVersion> provider) throws IOException {
		MinecraftVersionGraph graph = new MinecraftVersionGraph();
		// Compatibility with existing repositories
		if (provider.getSource() != ManifestSource.MOJANG) {
			graph.repoTags.add(String.format("manifest_%s", provider.getInternalName()));
		}
		TreeSet<OrderedVersion> metaVersions = new TreeSet<>(provider.getVersions(executor).values());
		TreeSet<OrderedVersion> metaVersionsMainline = new TreeSet<>(provider.getVersions(executor).values().stream().filter(value -> !provider.shouldExcludeFromMainBranch(value)).toList());
		for (OrderedVersion version : metaVersions) {
			graph.edgesBack.computeIfAbsent(version, _ -> new TreeSet<>());
			graph.edgesFw.computeIfAbsent(version, _ -> new TreeSet<>());
			for (OrderedVersion previousVersion : findPreviousVersions(provider, metaVersionsMainline, version)) {
				graph.edgesBack.computeIfAbsent(version, _ -> new TreeSet<>()).add(previousVersion);
				graph.edgesFw.computeIfAbsent(previousVersion, _ -> new TreeSet<>()).add(version);
			}
		}
		graph.testGraphConnectivity();
		graph.validateNoCycles();
		graph.findBranchStructure();
		return graph;
	}

	private static List<OrderedVersion> findPreviousVersions(MetadataProvider<OrderedVersion> metadata, NavigableSet<OrderedVersion> versions, OrderedVersion version) {
		return findPreviousVersions(metadata, versions, version, version);
	}

	private static List<OrderedVersion> findPreviousVersions(MetadataProvider<OrderedVersion> metadata, NavigableSet<OrderedVersion> versions, OrderedVersion version, OrderedVersion target) {
		List<OrderedVersion> previousVersions = new ArrayList<>();

		// some manifest providers have built-in ordering for all or certain versions
		List<OrderedVersion> parentVersions = metadata.getParentVersions(version);

		// if that is not the case, use the ordering from the semantic versioning
		if (parentVersions == null) {
			OrderedVersion parentVersion = versions.lower(version);

			parentVersions = (parentVersion == null)
				? Collections.emptyList()
				: List.of(parentVersion);
		}

		// if a parent version cannot form a valid edge with the target,
		// find valid edges in that version's parent versions, recursively
		for (OrderedVersion parentVersion : parentVersions) {
			if (isValidEdge(parentVersion, target)) {
				previousVersions.add(parentVersion);
			} else {
				previousVersions.addAll(findPreviousVersions(metadata, versions, parentVersion, version));
			}
		}

		return previousVersions;
	}

	private static boolean isValidEdge(OrderedVersion v1, OrderedVersion v2) {
		// TODO: allow disabling this check through the config/run args?
		return v1.hasSideInCommon(v2);
	}

	public MinecraftVersionGraph filterMapping(MappingFlavour mappingFlavour, MappingFlavour[] mappingFallback) {
		return new MinecraftVersionGraph(this, (entry -> mappingFlavour.exists(entry) || (mappingFallback != null && mappingFallback.length > 0 && Arrays.stream(mappingFallback).anyMatch(mapping -> mapping.exists(entry)))));
	}

	public MinecraftVersionGraph filterUnpick(UnpickFlavour unpickFlavour, UnpickFlavour[] unpickFallback) {
		return new MinecraftVersionGraph(this, (entry -> unpickFlavour.exists(entry) || (unpickFallback != null && unpickFallback.length > 0 && Arrays.stream(unpickFallback).anyMatch(unpick -> unpick.exists(entry)))));
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

	public OrderedVersion getMainRootVersion() {
		if (this.roots.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return this.roots.stream().max((v1, v2) -> this.pathsToTip.get(v1) - this.pathsToTip.get(v2)).get();
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

		Set<OrderedVersion> branches = this.getPreviousVertices(mcVersion);

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

	public String repoTagsIdentifier(MappingFlavour mappingFlavour, MappingFlavour[] mappingFallback, boolean patchLvt, SignaturesFlavour signaturesFlavour, NestsFlavour nestsFlavour, ExceptionsFlavour exceptionsFlavour, boolean preening) {
		List<String> sortedTags = new ArrayList<>();
		sortedTags.add(mappingFlavour.toString());
		if (mappingFallback != null && mappingFallback.length > 0) {
			sortedTags.add(String.format("fallback-%s", Arrays.stream(mappingFallback).map(Object::toString).collect(Collectors.joining("-"))));
		}
		sortedTags.addAll(this.repoTags.stream().filter(tag -> !tag.equals(mappingFlavour.toString())).toList());
		if (patchLvt) {
			sortedTags.add("lvt");
		}
		if (signaturesFlavour != null && signaturesFlavour != SignaturesFlavour.NONE) {
			sortedTags.add(String.format("sig_%s", signaturesFlavour));
		}
		if (nestsFlavour != null && nestsFlavour != NestsFlavour.NONE) {
			sortedTags.add(String.format("nests_%s", nestsFlavour));
		}
		if (exceptionsFlavour != null && exceptionsFlavour != ExceptionsFlavour.NONE) {
			sortedTags.add(String.format("exc_%s", exceptionsFlavour));
		}
		if (preening) {
			sortedTags.add("preened");
		}
		return String.join("-", sortedTags);
	}
}
