package com.github.winplay02.gitcraft.graph;

import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class AbstractVersionGraph<T extends AbstractVersion<T>> extends Graph<T> {

	public HashSet<String> repoTags = new HashSet<>();
	/**
	 * root nodes of the graph, mapped to the path lengths to the tips of those main branches
	 */
	public HashMap<T, Integer> roots = new HashMap<>();

	protected AbstractVersionGraph() {
		super();
	}

	protected AbstractVersionGraph(AbstractVersionGraph<T> previous, Predicate<T> predicate, String... tags) {
		super(new HashMap<>(previous.edgesBack.keySet().stream().filter(predicate).collect(Collectors.toMap(Function.identity(), key -> new TreeSet<T>()))), new HashMap<>(previous.edgesFw.keySet().stream().filter(predicate).collect(Collectors.toMap(Function.identity(), key -> new TreeSet<T>()))));
		this.repoTags = new HashSet<>(previous.repoTags);
		this.repoTags.addAll(Arrays.asList(tags));
		this.reconnectGraph(previous);
		this.validateNoCycles();
	}

	protected void reconnectGraph(AbstractVersionGraph<T> previous) {
		TreeSet<T> allVersions = new TreeSet<>(this.edgesBack.keySet());
		for (T version : allVersions) {
			TreeSet<T> nearestPreviousNodes = new TreeSet<>(previous.getPreviousVertices(version));
			TreeSet<T> calculatedPreviousNodes = new TreeSet<>();
			//while (nearestPreviousNodes.stream().anyMatch(anyPreviousVersion -> !this.containsVersion(anyPreviousVersion))) {
			while (!nearestPreviousNodes.isEmpty()) {
				TreeSet<T> nextBestNodes = new TreeSet<>();
				for (T nearestPreviousNode : nearestPreviousNodes) {
					if (allVersions.contains(nearestPreviousNode)) {
						calculatedPreviousNodes.add(nearestPreviousNode);
					} else {
						nextBestNodes.addAll(previous.getPreviousVertices(nearestPreviousNode));
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
			TreeSet<T> nearestNextNodes = new TreeSet<>(previous.getFollowingVertices(version));
			TreeSet<T> calculatedNextNodes = new TreeSet<>();
			//while (nearestNextNodes.stream().anyMatch(anyNextVersion -> !this.containsVersion(anyNextVersion))) {
			while (!nearestNextNodes.isEmpty()) {
				TreeSet<T> nextBestNodes = new TreeSet<>();
				for (T nearestNextNode : nearestNextNodes) {
					if (allVersions.contains(nearestNextNode)) {
						calculatedNextNodes.add(nearestNextNode);
					} else {
						nextBestNodes.addAll(previous.getFollowingVertices(nearestNextNode));
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
		this.testGraphConnectivity();
	}

	public Set<T> getRootVersions() {
		if (this.roots.isEmpty()) {
			MiscHelper.panic("Graph does not contain a root node");
		}
		return this.roots.keySet();
	}

	public boolean containsVersion(T version) {
		return version != null && this.edgesBack.containsKey(version);
	}
}
