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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import eu.fasten.core.data.ImmutableGraphAdapter.TransposeAdapter;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;

public class ImmutableGraphAdapterTest {

	private final static void assertSorted(final DirectedGraph graph) {
		for (final long v : graph.nodes()) {
			LongList sorted = graph.successors(v);
			sorted.sort(null);
			assertEquals(sorted, graph.successors(v));
			sorted = graph.predecessors(v);
			sorted.sort(null);
			assertEquals(sorted, graph.predecessors(v));
		}
	}

	private final static void assertSorted(final ImmutableGraph graph) {
		for (int v = 0; v < graph.numNodes(); v++) {
			final int[] a = graph.successorArray(v);
			for (int i = 1; i < graph.outdegree(v) - 1; i++) assertTrue(a[i] < a[i + 1]);
			final LazyIntIterator s = graph.successors(v);
			int curr = s.nextInt();
			for (;;) {
				final int next = s.nextInt();
				if (next == -1) break;
				assertTrue(next >= curr);
				curr = next;
			}
		}
	}

	private static void assertSame(final DirectedGraph g, final ImmutableGraphAdapter a) {
		final int n = g.numNodes();
		assertEquals(n, a.numNodes());
		assertEquals(g.numArcs(), a.numArcs());

		final LongOpenHashSet s = new LongOpenHashSet();

		for (int x = 0; x < n; x++) {
			final long id = a.node2Id(x);
			s.add(id);
			final LongListIterator iterator = g.successors(id).iterator();
			final LazyIntIterator successors = a.successors(x);
			while (iterator.hasNext()) assertEquals(iterator.nextLong(), a.node2Id(successors.nextInt()));
			final int[] successorArray = a.successorArray(x);
			for (int i = 0; i < g.outdegree(id); i++) assertEquals(g.successors(id).getLong(i), a.node2Id(successorArray[i]));
			assertEquals(-1, successors.nextInt());
		}

		assertEquals(g.nodes(), s);
		final TransposeAdapter t = a.transpose();
		assertTrue(t.hasCopiableIterators());
		assertTrue(t.randomAccess());

		for (int x = 0; x < n; x++) {
			final long id = a.node2Id(x);
			assertEquals(id, t.node2Id(x));
			s.add(id);
			final LongListIterator iterator = g.predecessors(id).iterator();
			final LazyIntIterator predecessors = t.successors(x);
			while (iterator.hasNext()) assertEquals(iterator.nextLong(), a.node2Id(predecessors.nextInt()));
			final int[] predecessorArray = t.successorArray(x);
			for (int i = 0; i < g.indegree(id); i++) assertEquals(g.predecessors(id).getLong(i), a.node2Id(predecessorArray[i]));
			assertEquals(-1, predecessors.nextInt());
		}

	}

	@Test
	public void testRandom() {
        final ArrayImmutableDirectedGraph.Builder builder = new ArrayImmutableDirectedGraph.Builder();
        final XoRoShiRo128PlusPlusRandomGenerator random = new XoRoShiRo128PlusPlusRandomGenerator(0);
		final int n = 200;
		final long[] node = new long[n];
		for (int i = 0; i < n; i++) builder.addInternalNode(node[i] = random.nextLong());

		for (int i = 0; i < 20 * n; i++) {
			try {
				builder.addArc(node[random.nextInt(n)], node[random.nextInt(n)]);
			} catch (final IllegalArgumentException ignoreDuplicateArcs) {
			}
		}

		ArrayImmutableDirectedGraph directedGraph = builder.build(true);
		ImmutableGraphAdapter immutableGraph = new ImmutableGraphAdapter(directedGraph, false);
		assertTrue(immutableGraph.hasCopiableIterators());
		assertTrue(immutableGraph.randomAccess());
		assertSorted(directedGraph);
		assertSorted(immutableGraph);
		assertSorted(immutableGraph.transpose());
		assertSame(directedGraph, immutableGraph);

		directedGraph = builder.build(false);
		immutableGraph = new ImmutableGraphAdapter(directedGraph, true);
		assertSorted(immutableGraph);
		assertSorted(immutableGraph.transpose());
		immutableGraph = new ImmutableGraphAdapter(directedGraph, false);
		assertSame(directedGraph, immutableGraph);
	}
}
