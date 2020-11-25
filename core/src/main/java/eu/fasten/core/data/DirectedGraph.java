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
import java.util.Set;
import java.util.function.Supplier;

import org.jgrapht.GraphType;
import org.jgrapht.graph.DefaultGraphType;

import it.unimi.dsi.fastutil.longs.LongList;
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
 *
 * <p>
 * This class implements the read-only methods of JGraphT's {@link Graph} interface. Arcs are
 * represented as two-elements arrays of longs containing the source and the target of the arc.
 * Mutation methods will throw an {@link UnsupportedOperationException}.
 */

public interface DirectedGraph extends org.jgrapht.Graph<Long, long[]> {
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
	 * The list of predecessors of a given node.
	 *
	 * @param node a node in the graph.
	 * @return its successors.
	 * @throws IllegalArgumentException if <code>node</code> is not a node of the graph.
	 */
	public LongList predecessors(final long node);

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

	@Override
	default Set<long[]> getAllEdges(final Long sourceVertex, final Long targetVertex) {
		final long[] edge = getEdge(sourceVertex, targetVertex);
		return edge == null ? Collections.emptySet() : ObjectSets.singleton(edge);
	}

	@Override
	default long[] getEdge(final Long sourceVertex, final Long targetVertex) {
		return successors(sourceVertex).contains(targetVertex.longValue()) ? new long[] { sourceVertex,
				targetVertex } : null;
	}

	@Override
	default Supplier<Long> getVertexSupplier() {
		return null;
	}

	@Override
	default Supplier<long[]> getEdgeSupplier() {
		return null;
	}

	@Override
	default long[] addEdge(final Long sourceVertex, final Long targetVertex) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean addEdge(final Long sourceVertex, final Long targetVertex, final long[] e) {
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
	default boolean containsEdge(final long[] e) {
		return successors(e[0]).contains(e[1]);
	}

	@Override
	default boolean containsVertex(final Long v) {
		return nodes().contains(v.longValue());
	}

	@Override
	default Set<long[]> edgeSet() {
		final ObjectOpenHashSet<long[]> s = new ObjectOpenHashSet<>();
		for (final Long x : vertexSet()) s.addAll(outgoingEdgesOf(x));
		return s;
	}

	@Override
	default int degreeOf(final Long vertex) {
		return inDegreeOf(vertex) + outDegreeOf(vertex);
	}

	@Override
	default Set<long[]> edgesOf(final Long vertex) {
		final Set<long[]> s = outgoingEdgesOf(vertex);
		for (final long[] e : incomingEdgesOf(vertex)) if (e[0] != e[1]) s.add(e);
		return s;
	}

	@Override
	default int inDegreeOf(final Long vertex) {
		return predecessors(vertex).size();
	}

	@Override
	default Set<long[]> incomingEdgesOf(final Long vertex) {
		final long y = vertex;
		final ObjectOpenHashSet<long[]> s = new ObjectOpenHashSet<>();
		for (final long x : predecessors(y)) s.add(new long[] { x, y });
		return s;
	}

	@Override
	default int outDegreeOf(final Long vertex) {
		return successors(vertex).size();
	}

	@Override
	default Set<long[]> outgoingEdgesOf(final Long vertex) {
		final long x = vertex;
		final ObjectOpenHashSet<long[]> s = new ObjectOpenHashSet<>();
		for (final long y : successors(x)) s.add(new long[] { x, y });
		return s;
	}

	@Override
	default boolean removeAllEdges(final Collection<? extends long[]> edges) {
		throw new UnsupportedOperationException();
	}

	@Override
	default Set<long[]> removeAllEdges(final Long sourceVertex, final Long targetVertex) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean removeAllVertices(final Collection<? extends Long> vertices) {
		throw new UnsupportedOperationException();
	}

	@Override
	default long[] removeEdge(final Long sourceVertex, final Long targetVertex) {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean removeEdge(final long[] e) {
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
	default Long getEdgeSource(final long[] e) {
		return e[0];
	}

	@Override
	default Long getEdgeTarget(final long[] e) {
		return e[1];
	}

	@Override
	default GraphType getType() {
		return new DefaultGraphType.Builder().directed().weighted(false).modifiable(false).allowMultipleEdges(false).allowSelfLoops(true).build();

	}

	@Override
	default double getEdgeWeight(final long[] e) {
		return DEFAULT_EDGE_WEIGHT;
	}

	@Override
	default void setEdgeWeight(final long[] e, final double weight) {
		if (weight == 1) return;
		throw new UnsupportedOperationException();
	}
}
