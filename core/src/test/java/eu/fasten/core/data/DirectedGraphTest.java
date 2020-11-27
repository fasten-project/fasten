package eu.fasten.core.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

public class DirectedGraphTest {
	@Test
	public void testSmall() {
		final ArrayImmutableDirectedGraph.Builder builder = new ArrayImmutableDirectedGraph.Builder();
		builder.addInternalNode(12);
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			builder.addInternalNode(12);
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			builder.addExternalNode(12);
		});
		builder.addInternalNode(34);
		builder.addExternalNode(56);
		builder.addExternalNode(78);
		builder.addArc(12, 34);
		builder.addArc(12, 56);
		builder.addArc(56, 12);
		builder.addArc(56, 78);
		builder.addArc(56, 34);
		builder.addArc(56, 56);
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			builder.addArc(56, 78);
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			builder.addArc(56, 1);
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			builder.addArc(1, 78);
		});
		final ArrayImmutableDirectedGraph graph = builder.build();
		assertEquals(4, graph.numNodes());
		assertEquals(6, graph.numArcs());
		assertEquals(new LongOpenHashSet(new long[] { 34, 56 }), new LongOpenHashSet(graph.successors(12)));
		assertEquals(new LongOpenHashSet(new long[] { 12, 34, 78, 56 }), new LongOpenHashSet(graph.successors(56)));
		assertEquals(new LongOpenHashSet(new long[] { 56, 78 }), graph.externalNodes());
		assertEquals(new LongOpenHashSet(new long[] { 56 }), new LongOpenHashSet(graph.predecessors(12)));
		assertEquals(new LongOpenHashSet(new long[] { 12, 56 }), new LongOpenHashSet(graph.predecessors(34)));
		assertEquals(new LongOpenHashSet(new long[] { 12, 56 }), new LongOpenHashSet(graph.predecessors(56)));
		assertEquals(new LongOpenHashSet(new long[] { 56 }), new LongOpenHashSet(graph.predecessors(78)));

		assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { 12, 34 }, { 12,
				56 } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.outgoingEdgesOf(12L), LongArrays.HASH_STRATEGY));
		assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { 56, 12 }, { 56, 34 }, { 56, 56 }, { 56,
				78 } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.outgoingEdgesOf(56L), LongArrays.HASH_STRATEGY));
		assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { 56,
				12 } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.incomingEdgesOf(12L), LongArrays.HASH_STRATEGY));
		assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { 12, 34 }, { 56,
				34 } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.incomingEdgesOf(34L), LongArrays.HASH_STRATEGY));
		assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { 12, 56 }, { 56,
				56 } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.incomingEdgesOf(56L), LongArrays.HASH_STRATEGY));
		assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { 56, 12 }, { 56, 34 }, { 56, 78 }, { 12, 56 }, { 56,
				56 } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.edgesOf(56L), LongArrays.HASH_STRATEGY));
		assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { 56,
				78 } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.incomingEdgesOf(78L), LongArrays.HASH_STRATEGY));

		assertEquals(2, graph.outDegreeOf(12L));
		assertEquals(4, graph.outDegreeOf(56L));
		assertEquals(2, graph.inDegreeOf(56L));
		assertEquals(6, graph.degreeOf(56L));

		for (final long x : graph.vertexSet()) for (final long y : graph.successors(x)) {
			assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { x,
					y } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.getAllEdges(x, y), LongArrays.HASH_STRATEGY));
			assertArrayEquals(new long[] { x, y }, graph.getEdge(x, y));
			assertTrue(graph.containsEdge(x, y));
			assertTrue(graph.containsEdge(new long[] { x, y }));
		}

		assertNull(graph.getEdge(12L, 78L));
		assertEquals(Collections.emptySet(), graph.getAllEdges(12L, 78L));
		assertFalse(graph.containsEdge(12L, 78L));
		assertFalse(graph.containsEdge(new long[] { 12L, 78L }));
		assertEquals(12, graph.getEdgeSource(new long[] { 12L, 78L }));
		assertEquals(78, graph.getEdgeTarget(new long[] { 12L, 78L }));

		for (final long y : graph.vertexSet()) for (final long x : graph.predecessors(y)) {
			assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { x,
					y } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.getAllEdges(x, y), LongArrays.HASH_STRATEGY));
			assertArrayEquals(new long[] { x, y }, graph.getEdge(x, y));
			assertTrue(graph.containsEdge(x, y));
			assertTrue(graph.containsEdge(new long[] { x, y }));
		}

		assertEquals(new LongOpenHashSet(new long[] { 12, 34, 78, 56 }), new LongOpenHashSet(graph.successors(56)));
		assertEquals(new LongOpenHashSet(new long[] { 56, 78 }), graph.externalNodes());
		assertEquals(new LongOpenHashSet(new long[] { 56 }), new LongOpenHashSet(graph.predecessors(12)));
		assertEquals(new LongOpenHashSet(new long[] { 12, 56 }), new LongOpenHashSet(graph.predecessors(34)));
		assertEquals(new LongOpenHashSet(new long[] { 12, 56 }), new LongOpenHashSet(graph.predecessors(56)));
		assertEquals(new LongOpenHashSet(new long[] { 56 }), new LongOpenHashSet(graph.predecessors(78)));

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			graph.successors(1);
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			graph.predecessors(1);
		});

		assertEquals(new LongOpenHashSet(new long[] { 12, 34, 56, 78 }), graph.nodes());
		assertTrue(graph.isInternal(12));
		assertTrue(graph.isInternal(34));
		assertTrue(graph.isExternal(56));
		assertTrue(graph.isExternal(78));
		assertFalse(graph.isExternal(12));
		assertFalse(graph.isExternal(34));
		assertFalse(graph.isInternal(56));
		assertFalse(graph.isInternal(78));

		assertEquals(new ObjectOpenCustomHashSet<>(new long[][] { { 12, 34 }, { 12, 56 }, { 56, 12 }, { 56, 78 },
				{ 56, 34 },
				{ 56, 56 } }, LongArrays.HASH_STRATEGY), new ObjectOpenCustomHashSet<>(graph.edgeSet(), LongArrays.HASH_STRATEGY));
	}
}
