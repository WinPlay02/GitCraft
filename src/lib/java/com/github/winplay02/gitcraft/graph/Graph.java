package com.github.winplay02.gitcraft.graph;

import com.github.winplay02.gitcraft.util.MiscHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Directed graph, optionally acyclic
 *
 * @param <T> Vertex Type
 */
public class Graph<T extends Vertex<T>> implements Iterable<T> {
	protected final HashMap<T, TreeSet<T>> edgesBack;
	protected final HashMap<T, TreeSet<T>> edgesFw;

	protected Graph(HashMap<T, TreeSet<T>> edgesBack, HashMap<T, TreeSet<T>> edgesFw) {
		this.edgesBack = edgesBack;
		this.edgesFw = edgesFw;
	}

	protected Graph() {
		this.edgesBack = new HashMap<>();
		this.edgesFw = new HashMap<>();
	}

	/**
	 * Test whether this graph contains at least one cycle
	 */
	protected void validateNoCycles() {
		T debugInfoMaxVertex = null;
		Map<T, Integer> visitedInformation = new HashMap<>();
		Stack<T> nodesStack = new Stack<>();
		// Roots
		for (T root : this.findRootVertices()) {
			nodesStack.push(root);
			visitedInformation.put(root, 1 /*STARTED*/);
		}
		// Depth First Search
		while (!nodesStack.isEmpty()) {
			T subject = nodesStack.peek();
			int pushed = 0;
			for (T following : this.getFollowingVertices(subject)) {
				if (!visitedInformation.containsKey(following)) {
					nodesStack.push(following);
					visitedInformation.put(following, 1 /*STARTED*/);
					++pushed;
					debugInfoMaxVertex = following;
					break;
				} else if (visitedInformation.get(following) == 1 /*STARTED*/) {
					MiscHelper.panic("Found a cycle in graph at vertex: %s", subject.description());
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
			MiscHelper.panic("During validating of the graph, not all vertices were visited. This is most likely caused by an inconsistency, e.g. a cycle in the graph. Last vertex checked: %s (problem may be near that vertex)", debugInfoMaxVertex != null ? debugInfoMaxVertex.description() : "<unknown>");
		}
	}

	/**
	 * Find all vertices with in-degree of zero
	 * @return Set containing root vertices
	 */
	protected Set<T> findRootVertices() {
		return this.edgesBack.entrySet()
			.stream()
			.filter(entry -> entry.getValue().isEmpty())
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	/**
	 * @param vertex Subject vertex
	 * @return Set of vertices containing all vertices u where the edge (u, v) is contained within this graph, for provided vertex v
	 */
	public NavigableSet<T> getPreviousVertices(T vertex) {
		return Collections.unmodifiableNavigableSet(this.edgesBack.containsKey(vertex) ? this.edgesBack.get(vertex) : Collections.emptyNavigableSet());
	}

	/**
	 *
	 * @param vertex Subject vertex
	 * @return Set of vertices containing all vertices u where the edge (v, u) is contained within this graph, for provided vertex v
	 */
	protected NavigableSet<T> getFollowingVertices(T vertex) {
		return Collections.unmodifiableNavigableSet(this.edgesFw.containsKey(vertex) ? this.edgesFw.get(vertex) : Collections.emptyNavigableSet());
	}

	/**
	 * @param vertex Subject vertex
	 * @return In-degree of provided vertex
	 */
	protected int getVertexInDegree(T vertex) {
		return this.getPreviousVertices(vertex).size();
	}

	/**
	 * Test whether the edges contained in this graph are valid and whether there is at least one root vertex (vertices with in-degree of zero)
	 */
	protected void testGraphConnectivity() {
		for (T vertex : this.edgesBack.keySet()) {
			for (T prevVertex : this.getPreviousVertices(vertex)) {
				if (this.edgesFw.get(prevVertex) == null || !this.edgesFw.get(prevVertex).contains(vertex)) {
					MiscHelper.panic("Graph is inconsistent. Vertex %s is not connected (forward direction) to %s", prevVertex.description(), vertex.description());
				}
			}
		}
		for (T vertex : this.edgesFw.keySet()) {
			for (T nextVertex : this.getFollowingVertices(vertex)) {
				if (this.edgesBack.get(nextVertex) == null || !this.edgesBack.get(nextVertex).contains(vertex)) {
					MiscHelper.panic("Graph is inconsistent. Vertex %s is not connected (backward direction) to %s", nextVertex.description(), vertex.description());
				}
			}
		}
		long amountRootNodes = this.edgesBack.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).count();
		if (amountRootNodes < 1) {
			MiscHelper.panic("There is no root node. This either means, that the graph is empty, or that it contains a cycle.");
		}
	}

	/**
	 * @return Stream containing all vertices of this graph in any topological order
	 */
	public Stream<T> stream() {
		// Topological order, only valid in directed acyclic graph
		HashSet<T> nextVertices = new HashSet<>(findRootVertices());
		HashSet<T> emittedVertices = new HashSet<>(this.edgesBack.size());
		HashSet<T> temporarySet = new HashSet<>(findRootVertices());
		Stream.Builder<T> builder = Stream.builder();
		while (!nextVertices.isEmpty()) {
			for (T vertex : nextVertices) {
				if (this.getPreviousVertices(vertex).stream().anyMatch(vertexPrev -> !emittedVertices.contains(vertexPrev))) {
					temporarySet.add(vertex);
					continue;
				}
				builder.accept(vertex);
				emittedVertices.add(vertex);
				temporarySet.addAll(this.edgesFw.get(vertex));
			}
			nextVertices.clear();
			nextVertices.addAll(temporarySet.stream().filter(vertex -> !emittedVertices.contains(vertex)).toList());
			temporarySet.clear();
		}
		if (emittedVertices.size() < this.edgesBack.size()) {
			MiscHelper.panic("Topological order is incomplete, this graph contains a cycle");
		}
		return builder.build();
	}

	@Override
	public Iterator<T> iterator() {
		return stream().iterator();
	}

	// protected final void addVertex(T vertex) {}

	// protected final void removeVertex(T vertex) {}

	// protected final void removeVertexBridgeEdge(T vertex) {}
}
