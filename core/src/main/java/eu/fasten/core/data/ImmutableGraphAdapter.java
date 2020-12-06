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

import java.util.Arrays;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.webgraph.AbstractLazyIntIterator;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.LazyIntIterators;

/**
 * An adapter exposing a {@link DirectedGraph} as a WebGraph {@link ImmutableGraph}. The
 * {@linkplain #transpose() transpose} is exposed, too.
 *
 * <p>
 * If the underlying {@link DirectedGraph} has sorted predecessor and successors lists, the
 * resulting adapter will have sorted predecessor and successors lists, too. Order can be forced
 * using a {@linkplain #ImmutableGraphAdapter(DirectedGraph, boolean) construction-time option}, but
 * it is quite expensive as lists have to be sorted at each call to {@link #successors(int)}.
 */

public class ImmutableGraphAdapter extends ImmutableGraph {
	/** The underlying graph. */
	private final DirectedGraph graph;
	/** Whether successors and predecessor lists should be sorted. */
	private final boolean sorted;
	/** For each WebGraph node, the corresponding ID in {@link #graph}. */
	private final long[] node2Id;
	/** For each ID in {{@link #graph}, the corresponding WebGraph node. */
	private final Long2IntOpenHashMap id2Node;
	/** An adapted view on the transpose of {{@link #graph}. */
	private final TransposeAdapter transpose;

	/** An adapter on the transpose. */
	public final class TransposeAdapter extends ImmutableGraph {
		public int id2Node(final long id) {
			assert id2Node.containsKey(id) : id;
			return id2Node.get(id);
		}

		public long node2Id(final int node) {
			return node2Id[node];
		}

		@Override
		public int numNodes() {
			return ImmutableGraphAdapter.this.numNodes();
		}

		@Override
		public long numArcs() {
			return ImmutableGraphAdapter.this.numArcs();
		}

		@Override
		public boolean randomAccess() {
			return true;
		}

		@Override
		public boolean hasCopiableIterators() {
			return true;
		}

		@Override
		public int outdegree(final int x) {
			return graph.predecessors(node2Id[x]).size();
		}

		@Override
		public LazyIntIterator successors(final int x) {
			if (sorted) return LazyIntIterators.wrap(successorArray(x));

			// Lazy scanning when sorted order not required
			final LongList predecessors = graph.predecessors(node2Id[x]);
			final int d = predecessors.size();

			return new AbstractLazyIntIterator() {
				int i = 0;

				@Override
				public int nextInt() {
					if (i == d) return -1;
					return id2Node.get(predecessors.getLong(i++));
				}
			};
		}

		@Override
		public int[] successorArray(final int x) {
			final long v = node2Id[x];
			final LongList predecessors = graph.predecessors(v);
			final int d = predecessors.size();
			final int[] a = new int[d];
			for (int i = 0; i < d; i++) a[i] = id2Node.get(predecessors.getLong(i));
			if (sorted) Arrays.sort(a);
			return a;
		}

		@Override
		public ImmutableGraph copy() {
			return this;
		}
	}

	public ImmutableGraphAdapter(final DirectedGraph graph) {
		this(graph, false);
	}

	public ImmutableGraphAdapter(final DirectedGraph graph, final boolean sorted) {
		this.graph = graph;
		this.sorted = sorted;
		node2Id = graph.nodes().toLongArray();
		Arrays.sort(node2Id);
		id2Node = new Long2IntOpenHashMap(node2Id.length, Hash.FAST_LOAD_FACTOR);
		int i = 0;
		for (final long id : node2Id) id2Node.put(id, i++);
		transpose = new TransposeAdapter();
	}

	/**
	 * Given an id of the underlying {@link DirectedGraph}, returns the corresponding WebGraph node.
	 *
	 * @param id an id of the underlying {@link DirectedGraph}.
	 * @return the WebGraph node corresponding to {@code id} in this adapter.
	 */
	public int id2Node(final long id) {
		assert id2Node.containsKey(id) : id;
		return id2Node.get(id);
	}

	/**
	 * Given a node of this adapter, returns the corresponding id of the underlying
	 * {@link DirectedGraph}.
	 *
	 * @param node a node of this adapter.
	 * @return the id of the underlying {@link DirectedGraph} corresponding to {@code node} in this
	 *         adapter.
	 */
	public long node2Id(final int node) {
		return node2Id[node];
	}

	@Override
	public int numNodes() {
		return graph.numNodes();
	}

	@Override
	public long numArcs() {
		return graph.numArcs();
	}

	@Override
	public boolean randomAccess() {
		return true;
	}

	@Override
	public boolean hasCopiableIterators() {
		return true;
	}

	@Override
	public int outdegree(final int x) {
		return graph.successors(node2Id[x]).size();
	}

	@Override
	public LazyIntIterator successors(final int x) {
		if (sorted) return LazyIntIterators.wrap(successorArray(x));
		// Lazy scanning when sorted order not required
		final LongList successors = graph.successors(node2Id[x]);
		final int d = successors.size();

		return new AbstractLazyIntIterator() {
			int i = 0;
			@Override
			public int nextInt() {
				if (i == d) return -1;
				return id2Node.get(successors.getLong(i++));
			}
		};
	}

	@Override
	public int[] successorArray(final int x) {
		final long v = node2Id[x];
		final LongList successors = graph.successors(v);
		final int d = successors.size();
		final int[] a = new int[d];
		for (int i = 0; i < d; i++) a[i] = id2Node.get(successors.getLong(i));
		if (sorted) Arrays.sort(a);
		return a;
	}

	@Override
	public ImmutableGraph copy() {
		return this;
	}

	public TransposeAdapter transpose() {
		return transpose;
	}
}
