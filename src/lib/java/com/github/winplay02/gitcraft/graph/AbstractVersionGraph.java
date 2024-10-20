package com.github.winplay02.gitcraft.graph;

import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractVersionGraph<T extends AbstractVersion<T>> implements Iterable<T> {

	public HashSet<String> repoTags = new HashSet<>();
	/**
	 * root nodes of the graph, mapped to the path lengths to the tips of those main branches
	 */
	public HashMap<T, Integer> roots = new HashMap<>();
	public HashMap<T, TreeSet<T>> edgesBack = new HashMap<>();
	public HashMap<T, TreeSet<T>> edgesFw = new HashMap<>();

	protected AbstractVersionGraph() {

	}

	protected AbstractVersionGraph(AbstractVersionGraph<T> previous, Predicate<T> predicate, String... tags) {
		this.repoTags = new HashSet<>(previous.repoTags);
		this.edgesBack = new HashMap<>(previous.edgesBack.keySet().stream().filter(predicate).collect(Collectors.toMap(Function.identity(), key -> new TreeSet<T>())));
		this.edgesFw = new HashMap<>(previous.edgesFw.keySet().stream().filter(predicate).collect(Collectors.toMap(Function.identity(), key -> new TreeSet<T>())));
		this.repoTags.addAll(Arrays.asList(tags));
		this.reconnectGraph(previous);
		this.validateNoCycles();
	}

	protected void validateNoCycles() {
		T debugInfoMaxVersion = null;
		Map<T, Integer> visitedInformation = new HashMap<>();
		Stack<T> nodesStack = new Stack<>();
		// Roots
		for (T root : this.findRootVersions()) {
			nodesStack.push(root);
			visitedInformation.put(root, 1 /*STARTED*/);
		}
		// Depth First Search
		while (!nodesStack.isEmpty()) {
			T subject = nodesStack.peek();
			int pushed = 0;
			for (T following : this.getFollowingNodes(subject)) {
				if (!visitedInformation.containsKey(following)) {
					nodesStack.push(following);
					visitedInformation.put(following, 1 /*STARTED*/);
					++pushed;
					debugInfoMaxVersion = following;
					break;
				} else if (visitedInformation.get(following) == 1 /*STARTED*/) {
					MiscHelper.panic("Found a cycle in version graph at version: %s (%s)", subject.friendlyVersion(), subject.semanticVersion());
				} else {
					continue; // ENDED
				}
			}
			if (pushed == 0) {
				T handled = nodesStack.pop();
				visitedInformation.put(handled, 2 /*ENDED*/);
			}
		}
		if (visitedInformation.size() != this.edgesBack.size()) {
			MiscHelper.panic("During validating of the version graph, not all versions were visited. This is most likely caused by an inconsistency, e.g. a cycle in the graph. Last version checked: %s (problem may be near that version)", debugInfoMaxVersion != null ? debugInfoMaxVersion.friendlyVersion() : "<unknown>");
		}
	}

	protected void reconnectGraph(AbstractVersionGraph<T> previous) {
		TreeSet<T> allVersions = new TreeSet<>(this.edgesBack.keySet());
		for (T version : allVersions) {
			TreeSet<T> nearestPreviousNodes = new TreeSet<>(previous.getPreviousNodes(version));
			TreeSet<T> calculatedPreviousNodes = new TreeSet<>();
			//while (nearestPreviousNodes.stream().anyMatch(anyPreviousVersion -> !this.containsVersion(anyPreviousVersion))) {
			while (!nearestPreviousNodes.isEmpty()) {
				TreeSet<T> nextBestNodes = new TreeSet<>();
				for (T nearestPreviousNode : nearestPreviousNodes) {
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
			for (T prevVersion : calculatedPreviousNodes) {
				this.edgesFw.computeIfAbsent(prevVersion, value -> new TreeSet<>()).add(version);
			}
		}

		for (T version : allVersions) {
			TreeSet<T> nearestNextNodes = new TreeSet<>(previous.getFollowingNodes(version));
			TreeSet<T> calculatedNextNodes = new TreeSet<>();
			//while (nearestNextNodes.stream().anyMatch(anyNextVersion -> !this.containsVersion(anyNextVersion))) {
			while (!nearestNextNodes.isEmpty()) {
				TreeSet<T> nextBestNodes = new TreeSet<>();
				for (T nearestNextNode : nearestNextNodes) {
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

	protected void testGraphConnectivity() {
		for (T version : this.edgesBack.keySet()) {
			for (T prevVersion : this.getPreviousNodes(version)) {
				if (this.edgesFw.get(prevVersion) == null || !this.edgesFw.get(prevVersion).contains(version)) {
					MiscHelper.panic("VersionGraph is inconsistent. Version %s is not connected (forward direction) to %s", prevVersion.friendlyVersion(), version.friendlyVersion());
				}
			}
		}
		for (T version : this.edgesFw.keySet()) {
			for (T nextVersion : this.getFollowingNodes(version)) {
				if (this.edgesBack.get(nextVersion) == null || !this.edgesBack.get(nextVersion).contains(version)) {
					MiscHelper.panic("VersionGraph is inconsistent. Version %s is not connected (backward direction) to %s", nextVersion.friendlyVersion(), version.friendlyVersion());
				}
			}
		}
		long amountRootNodes = this.edgesBack.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).count();
		if (amountRootNodes < 1) {
			MiscHelper.panic("There is no root node. This either means, that the version graph is empty, or that it contains a cycle.");
		}
	}

	protected Set<T> findRootVersions() {
		return this.edgesBack.entrySet()
			.stream()
			.filter(entry -> entry.getValue().isEmpty())
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	public Set<T> getRootVersions() {
		if (this.roots.isEmpty()) {
			MiscHelper.panic("MinecraftVersionGraph does not contain a root version node");
		}
		return this.roots.keySet();
	}

	public NavigableSet<T> getPreviousNodes(T version) {
		return Collections.unmodifiableNavigableSet(this.edgesBack.containsKey(version) ? this.edgesBack.get(version) : Collections.emptyNavigableSet());
	}

	public NavigableSet<T> getFollowingNodes(T version) {
		return Collections.unmodifiableNavigableSet(this.edgesFw.containsKey(version) ? this.edgesFw.get(version) : Collections.emptyNavigableSet());
	}

	public boolean containsVersion(T version) {
		return version != null && this.edgesBack.containsKey(version);
	}

	public Stream<T> stream() {
		HashSet<T> nextVersions = new HashSet<>(findRootVersions());
		HashSet<T> emittedVersions = new HashSet<>();
		HashSet<T> temporarySet = new HashSet<>(findRootVersions());
		Stream.Builder<T> builder = Stream.builder();
		while (!nextVersions.isEmpty()) {
			for (T version : nextVersions) {
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
	public Iterator<T> iterator() {
		return stream().iterator();
	}
}
