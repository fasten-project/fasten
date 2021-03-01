/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.data;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;

import org.jgrapht.GraphType;
import org.jgrapht.graph.DefaultGraphType;

import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/**
 * A directed graph with internal and external nodes, providing both successors and predecessors
 * lists.
 *
 * <p>
 * Nodes in the graph are given by 64-bit (long) arbitrary identifiers. The set of nodes can be
 * recovered with {@link #nodes()}, and the set of external nodes with {@link #externalNodes()}.
 * Implementations must guarantee that predecessors and successors are enumerated always in the same
 * order.
 *
 * <p>
 * This class implements the read-only methods of JGraphT's {@link Graph} interface. Arcs are
 * represented as two-elements arrays of longs containing the source and the target of the arc.
 * Mutation methods will throw an {@link UnsupportedOperationException}.
 *
 * <p>
 * The iterator returned by {@link #nodes()} provides no guarantee about returning the nodes in a
 * fixed order. However, this class implements {@link LongIterable} and implementations must
 * guarantee that the associated {@link #iterator() LongIterator} enumerates the nodes in a fixed
 * order. This feature can be used to enumerate all arcs in a fixed order.
 */

public interface DirectedGraph extends org.jgrapht.Graph<Long, LongLongPair>, LongIterable {
	
	/** A class representing an arc with some associated data.
	 * 
	 *  @param T the type of associated data.
	 */
	public static final class Arc<T> {
		/** Build an arc with given source, target and data.
		 * 
		 * @param source the source of the arc.
		 * @param target the target of the arc.
		 * @param data the associated data.
		 */
		public Arc(long source, long target, T data) {
			this.source = source;
			this.target = target;
			this.data = data;
		}
		
		/** Source and target of the arc. */
		public long source, target;
		/** The data associated with the arc. */
		public T data;
	}

	/** A class representing a node with some associated data.
	 * 
	 *  @param T the type of associated data.
	 */
	public static final class Node<T> {
		/** Build a node with associated data.
		 * 
		 * @param node the node.
		 * @param data the associated data.
		 */
		public Node(long node, T data) {
			this.node = node;
			this.data = data;
		}
		
		/** The node. */
		public long node;
		/** The data associated with the node. */
		public T data;
	}

	/**
	 * The number of nodes in the graph.
	 *
	 * @return the number of nodes in the graph.
	 */
	public int numNodes();

	/**
	 * The number of arcs in the graph
	 *
	 * @return the number of arcs in the graph.
	 */
	public long numArcs();

	/**
	 * The list of successors of a given node.
	 *
	 * @param node a node in the graph.
	 * @return its successors.
	 * @throws IllegalArgumentException if <code>node</code> is not a node of the graph.
	 */
	public LongList successors(final long node);

	/**
	 * The number of successors of a given node.
	 *
	 * @param node a node in the graph.
	 * @return the number of its successors.
	 * @throws IllegalArgumentException if <code>node</code> is not a node of the graph.
	 */
	public default int outdegree(final long node) {
		return successors(node).size();
	}

	/**
	 * The list of predecessors of a given node.
	 *
	 * @param node a node in the graph.
	 * @return its successors.
	 * @throws IllegalArgumentException if <code>node</code> is not a node of the graph.
	 */
	public LongList predecessors(final long node);

	/**
	 * The number of predecessors of a given node.
	 *
	 * @param node a node in the graph.
	 * @return the number of its predecessors.
	 * @throws IllegalArgumentException if <code>node</code> is not a node of the graph.
	 */
	public default int indegree(final long node) {
		return predecessors(node).size();
	}

	/**
	 * The set of nodes of the graph.
	 *
	 * @return the set of nodes of the graph. // TODO this should be a LongSet.
	 */
	public LongSet nodes();

	/**
	 * The set of external nodes of the graph.
	 *
	 * @return the set of external nodes of the graph.
	 */
	public LongSet externalNodes();

	/**
	 * Returns whether a node is internal.
	 *
	 * @param node a node of the graph.
	 * @return whether <code>node</code> is internal.
	 */
	public boolean isInternal(final long node);

	/**
	 * Returns whether a node is external.
	 *
	 * @param node a node of the graph.
	 * @return whether <code>node</code> is external.
	 */
	public boolean isExternal(final long node);

	/**
	 * Returns a transposed view of this directed graph.
	 *
	 * @return a transposed view of this directed graph.
	 */
	public default DirectedGraph transpose() {
		return new DirectedGraph() {

			@Override
			public DirectedGraph transpose() {
				return DirectedGraph.this;
			}

			@Override
			public LongList successors(final long node) {
				return DirectedGraph.this.predecessors(node);
			}

			@Override
			public LongList predecessors(final long node) {
				return DirectedGraph.this.successors(node);
			}

			@Override
			public int numNodes() {
				return DirectedGraph.this.numNodes();
			}

			@Override
			public long numArcs() {
				return DirectedGraph.this.numArcs();
			}

			@Override
			public LongSet nodes() {
				return DirectedGraph.this.nodes();
			}

			@Override
			public LongIterator iterator() {
				return DirectedGraph.this.iterator();
			}

			@Override
			public boolean isInternal(final long node) {
				return DirectedGraph.this.isInternal(node);
			}

			@Override
			public boolean isExternal(final long node) {
				return DirectedGraph.this.isExternal(node);
			}

			@Override
			public LongSet externalNodes() {
				return DirectedGraph.this.externalNodes();
			}
		};
	}
	@Override
	default Set<LongLongPair> getAllEdges(final Long sourceVertex, final Long targetVertex) {
		final LongLongPair edge = getEdge(sourceVertex, targetVertex);
		return edge == null ? Collections.emptySet() : ObjectSets.singleton(edge);
	}

	@Override
	default LongLongPair getEdge(final Long sourceVertex, final Long targetVertex) {
		return successors(sourceVertex).contains(targetVertex.longValue()) ? LongLongPair.of(sourceVertex, targetVertex) : null;
	}

	@Override
	default Supplier<Long> getVertexSupplier() {
		return null;
	}

	@Override
	default Supplier<LongLongPair> getEdgeSupplier() {
		return null;
	}

	@Override
	default LongLongPair addEdge(final Long sourceVertex, final Long targetVertex) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean addEdge(final Long sourceVertex, final Long targetVertex, final LongLongPair e) {
		throw new UnsupportedOperationException();
	}

	@Override
	default Long addVertex() {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean addVertex(final Long v) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean containsEdge(final Long sourceVertex, final Long targetVertex) {
		return successors(sourceVertex).contains(targetVertex.longValue());
	}

	@Override
	default boolean containsEdge(final LongLongPair e) {
		return successors(e.leftLong()).contains(e.rightLong());
	}

	@Override
	default boolean containsVertex(final Long v) {
		return nodes().contains(v.longValue());
	}

	@Override
	default Set<LongLongPair> edgeSet() {
		final ObjectOpenHashSet<LongLongPair> s = new ObjectOpenHashSet<>();
		for (final Long x : vertexSet()) s.addAll(outgoingEdgesOf(x));
		return s;
	}

	@Override
	default int degreeOf(final Long vertex) {
		return inDegreeOf(vertex) + outDegreeOf(vertex);
	}

	@Override
	default Set<LongLongPair> edgesOf(final Long vertex) {
		final Set<LongLongPair> s = outgoingEdgesOf(vertex);
		for (final LongLongPair e : incomingEdgesOf(vertex)) if (e.leftLong() != e.rightLong()) s.add(e);
		return s;
	}

	@Override
	default int inDegreeOf(final Long vertex) {
		return predecessors(vertex).size();
	}

	@Override
	default Set<LongLongPair> incomingEdgesOf(final Long vertex) {
		final long y = vertex;
		final ObjectOpenHashSet<LongLongPair> s = new ObjectOpenHashSet<>();
		for (final long x : predecessors(y)) s.add(LongLongPair.of(x, y));
		return s;
	}

	@Override
	default int outDegreeOf(final Long vertex) {
		return successors(vertex).size();
	}

	@Override
	default Set<LongLongPair> outgoingEdgesOf(final Long vertex) {
		final long x = vertex;
		final ObjectOpenHashSet<LongLongPair> s = new ObjectOpenHashSet<>();
		for (final long y : successors(x)) s.add(LongLongPair.of(x, y));
		return s;
	}

	@Override
	default boolean removeAllEdges(final Collection<? extends LongLongPair> edges) {
		throw new UnsupportedOperationException();
	}

	@Override
	default Set<LongLongPair> removeAllEdges(final Long sourceVertex, final Long targetVertex) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean removeAllVertices(final Collection<? extends Long> vertices) {
		throw new UnsupportedOperationException();
	}

	@Override
	default LongLongPair removeEdge(final Long sourceVertex, final Long targetVertex) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean removeEdge(final LongLongPair e) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean removeVertex(final Long v) {
		throw new UnsupportedOperationException();
	}

	@Override
	default Set<Long> vertexSet() {
		return nodes();
	}

	@Override
	default Long getEdgeSource(final LongLongPair e) {
		return e.leftLong();
	}

	@Override
	default Long getEdgeTarget(final LongLongPair e) {
		return e.rightLong();
	}

	@Override
	default GraphType getType() {
		return new DefaultGraphType.Builder().directed().weighted(false).modifiable(false).allowMultipleEdges(false).allowSelfLoops(true).build();

	}

	@Override
	default double getEdgeWeight(final LongLongPair e) {
		return DEFAULT_EDGE_WEIGHT;
	}

	@Override
	default void setEdgeWeight(final LongLongPair e, final double weight) {
		if (weight == 1) return;
		throw new UnsupportedOperationException();
	}
	
	/** A method that returns the arcs (sources have the order that this {@link #iterator()} would give,
	 *  targets the same order as in {@link #successors()}), each associated with the data provided by the 
	 *  given <code>dataIterator</code>.
	 * 
	 * @param <T> the type of the data associated with each arc.
	 * @param dataIterator the iterator providing arc data.
	 * @return an iterator providing arcs with data.
	 */
	default <T> Iterator<Arc<T>> getArcsWithData(final Iterator<T> dataIterator) {
		LongIterator nodeIterator = iterator();
		
		return new Iterator<Arc<T>>() {
			LongIterator successors = LongIterators.EMPTY_ITERATOR;
			long currentNode = -1;
			
			@Override
			public boolean hasNext() {
				while (!successors.hasNext()) {
					if (!nodeIterator.hasNext()) return false;
					currentNode = nodeIterator.nextLong();
					successors = successors(currentNode).iterator();
				}			
				return true;
			}

			@Override
			public Arc<T> next() {
				if (!hasNext()) throw new NoSuchElementException();
				return new Arc<T>(currentNode, successors.nextLong(), dataIterator.next());
			}
			
		};
	}

	/** A method that returns the nodes (in the same order as with {@link #iterator()}), each associated with the data provided by the 
	 *  given <code>dataIterator</code>.
	 * 
	 * @param <T> the type of the data associated with each node.
	 * @param dataIterator the iterator providing node data.
	 * @return an iterator providing nodes with data.
	 */
	default <T> Iterator<Node<T>> getNodesWithData(final Iterator<T> dataIterator) {
		LongIterator nodeIterator = iterator();
		
		return new Iterator<Node<T>>() {
			
			@Override
			public boolean hasNext() {
				return nodeIterator.hasNext();
			}

			@Override
			public Node<T> next() {
				if (!hasNext()) throw new NoSuchElementException();
				return new Node<T>(nodeIterator.nextLong(), dataIterator.next());
			}
			
		};
	}
}
