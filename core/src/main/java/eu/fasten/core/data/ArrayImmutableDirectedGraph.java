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

import java.io.Serializable;
import java.util.Arrays;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * A basic implementation of {@link DirectedGraph} based on explicit adjacency lists. The
 * representation is oriented to small graphs, as the number of nodes plus twice the number of arcs
 * cannot exceed two billions.
 *
 * <p>
 * Instances of this class are created using a {@link ArrayImmutableDirectedGraph.Builder Builder}:
 * the builder makes it possible to add nodes
 * ({@linkplain ArrayImmutableDirectedGraph.Builder#addInternalNode(long) internal} or
 * {@linkplain ArrayImmutableDirectedGraph.Builder#addExternalNode(long) external}) and
 * {@linkplain ArrayImmutableDirectedGraph.Builder#addArc(long, long) arcs}, and finally an
 * immutable instance can be built using the {@link ArrayImmutableDirectedGraph.Builder#build()
 * Builder.build()} method.
 *
 * <p>
 * The representation is extremely compact and easy on the garbage collector, using a single array
 * of longs to maintain indegrees, outdegrees, successors and predecessors. A
 * {@link Long2IntOpenHashMap} keeps track of the correspondence between node identifiers and
 * offsets in the array.
 */

public class ArrayImmutableDirectedGraph implements DirectedGraph, Serializable {
	private static final long serialVersionUID = 0L;

	public static class Builder {
		private final Long2ObjectOpenHashMap<LongOpenHashSet> graph = new Long2ObjectOpenHashMap<>();
		private final LongOpenHashSet externalNodes = new LongOpenHashSet();
		private int numArcs;

		public void addInternalNode(final long node) {
			if (graph.containsKey(node)) throw new IllegalArgumentException("Node " + node + " is already in the node set");
			graph.put(node, new LongOpenHashSet());
		}

		public void addExternalNode(final long node) {
			if (graph.containsKey(node)) throw new IllegalArgumentException("Node " + node + " is already in the node set");
			graph.put(node, new LongOpenHashSet());
			externalNodes.add(node);
		}

		public void addArc(final long x, final long y) {
			if (!graph.containsKey(x)) throw new IllegalArgumentException("Node " + x + " is not in the node set");
			if (!graph.containsKey(y)) throw new IllegalArgumentException("Node " + y + " is not in the node set");
			final LongOpenHashSet successors = graph.get(x);
			if (!successors.add(y)) throw new IllegalArgumentException("Duplicate arc " + x + " -> " + y);
			if (numArcs * 2 + graph.size() >= Integer.MAX_VALUE - 8) throw new IllegalStateException("Graph is too large");
			numArcs++;
		}

		/**
		 * Builds an {@link ArrayImmutableDirectedGraph} with sorted predecessor and successor lists.
		 *
		 * @return an {@link ArrayImmutableDirectedGraph} with sorted predecessor and successor lists.
		 */
		public ArrayImmutableDirectedGraph build() {
			return build(true);
		}

		/**
		 * Builds an {@link ArrayImmutableDirectedGraph} with possibly sorted predecessor and successor
		 * lists.
		 *
		 * <p>
		 * Sorted predecessor and successor lists takes an additional computational effort at construction
		 * time, but they make it possible to obtain faster {@linkplain ImmutableGraphAdapter adapters}.
		 *
		 * @param sorted whether successor and predecessor lists should be sorted.
		 * @return an {@link ArrayImmutableDirectedGraph}; predecessor and successor lists will be sorted
		 *         depending on the value of {@code sorted}.
		 */
		public ArrayImmutableDirectedGraph build(final boolean sorted) {
			final Long2ObjectArrayMap<LongArrayList> transpose = new Long2ObjectArrayMap<>();
			for (final Entry<LongOpenHashSet> e : graph.long2ObjectEntrySet()) {
				final long x = e.getLongKey();
				for (final long y : e.getValue()) {
					LongArrayList l = transpose.get(y);
					if (l == null) transpose.put(y, l = new LongArrayList());
					l.add(x);
				}
			}

			final long succpred[] = new long[graph.size() + numArcs * 2];
			final Long2IntOpenHashMap GID2Offset = new Long2IntOpenHashMap();
			GID2Offset.defaultReturnValue(-1);
			int i = 0;
			for (final Entry<LongOpenHashSet> e : graph.long2ObjectEntrySet()) {
				final long x = e.getLongKey();
				GID2Offset.put(x, i);
				final int outdegree = e.getValue().size();
				final int offset = i;
				succpred[i++] = outdegree;
				final LongIterator s = e.getValue().iterator();
				for (int j = 0; j < outdegree; j++) succpred[i++] = s.nextLong();
				if (sorted) Arrays.sort(succpred, offset + 1, i);

				final LongArrayList pred = transpose.get(x);
				final int indegree = pred == null ? 0 : pred.size();
				succpred[offset] |= (long)indegree << 32;
				if (indegree != 0) {
					final LongIterator p = pred.iterator();
					for (int j = 0; j < indegree; j++) succpred[i++] = p.nextLong();
				}
				if (sorted) Arrays.sort(succpred, offset + 1 + outdegree, i);
			}

			return new ArrayImmutableDirectedGraph(GID2Offset, succpred, externalNodes);
		}

		public boolean contains(Long source) {
			return graph.containsKey(source.longValue());
		}
	}

	// Constructor needed for kryo serialization
	protected ArrayImmutableDirectedGraph() {
		GID2Offset = null;
		succpred = null;
		externalNodes = null;
	}

	/** A map from node identifiers to offsets into {@link #succpred}. */
	private final Long2IntOpenHashMap GID2Offset;
	/**
	 * A concatenation of representations of successors and predecessors: for each node, the first long
	 * contains the outdegree in the lower 32 bits, and the indegree in the upper 32 bits. Then,
	 * successors and predecessors follow.
	 */
	private final long[] succpred;
	/** The set of external nodes. */
	private final LongOpenHashSet externalNodes;

	protected ArrayImmutableDirectedGraph(final Long2IntOpenHashMap GID2Offset, final long[] succpred, final LongOpenHashSet externalNodes) {
		this.GID2Offset = GID2Offset;
		this.succpred = succpred;
		this.externalNodes = externalNodes;
	}

	@Override
	public int numNodes() {
		return GID2Offset.size();
	}

	@Override
	public long numArcs() {
		return (succpred.length - GID2Offset.size()) / 2;
	}

	@Override
	public LongList successors(final long node) {
		final int offset = GID2Offset.get(node);
		if (offset == -1) throw new IllegalArgumentException("No such node: " + node);
		final int outdegree = (int)succpred[offset];
		return LongArrayList.wrap(Arrays.copyOfRange(succpred, offset + 1, offset + 1 + outdegree));
	}

	@Override
	public LongList predecessors(final long node) {
		int offset = GID2Offset.get(node);
		if (offset == -1) throw new IllegalArgumentException("No such node: " + node);
		final int outdegree = (int)succpred[offset];
		final int indegree = (int)(succpred[offset] >>> 32);
		offset += 1 + outdegree;
		return LongArrayList.wrap(Arrays.copyOfRange(succpred, offset, offset + indegree));
	}

	@Override
	public LongSet nodes() {
		return GID2Offset.keySet();
	}

	@Override
	public LongIterator iterator() {
		// This is expensive, but this class is oriented towards small graphs.
		final long[] a = GID2Offset.keySet().toLongArray();
		LongArrays.parallelQuickSort(a);
		return LongIterators.wrap(a);
	}

	@Override
	public LongSet externalNodes() {
		return externalNodes;
	}

	@Override
	public boolean isInternal(final long node) {
		return !externalNodes.contains(node);
	}

	@Override
	public boolean isExternal(final long node) {
		return externalNodes.contains(node);
	}
}
