package eu.fasten.core.data;

import static it.unimi.dsi.fastutil.longs.LongLongPair.of;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

import java.util.Collections;
import java.util.Iterator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.DirectedGraph.Arc;
import eu.fasten.core.data.DirectedGraph.Node;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

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
		assertEquals(LongOpenHashSet.of(34, 56), new LongOpenHashSet(graph.successors(12)));
		assertEquals(LongOpenHashSet.of(12, 34, 78, 56), new LongOpenHashSet(graph.successors(56)));
		assertEquals(LongOpenHashSet.of(56, 78), graph.externalNodes());
		assertEquals(LongOpenHashSet.of(56), new LongOpenHashSet(graph.predecessors(12)));
		assertEquals(LongOpenHashSet.of(12, 56), new LongOpenHashSet(graph.predecessors(34)));
		assertEquals(LongOpenHashSet.of(12, 56), new LongOpenHashSet(graph.predecessors(56)));
		assertEquals(LongOpenHashSet.of(56), new LongOpenHashSet(graph.predecessors(78)));

		assertEquals(ObjectOpenHashSet.of(of(12, 34), of(12, 56)), new ObjectOpenHashSet<>(graph.outgoingEdgesOf(12L)));

		assertEquals(ObjectOpenHashSet.of(of(56, 12), of(56, 34), of(56, 56), of(56, 78)), graph.outgoingEdgesOf(56L));
		assertEquals(ObjectOpenHashSet.of(of(56, 12)), graph.incomingEdgesOf(12L));
		assertEquals(ObjectOpenHashSet.of(of(12, 34), of(56, 34)), graph.incomingEdgesOf(34L));
		assertEquals(ObjectOpenHashSet.of(of(12, 56), of(56, 56)), graph.incomingEdgesOf(56L));
		assertEquals(ObjectOpenHashSet.of(of(56, 12), of(56, 34), of(56, 78), of(12, 56), of(56, 56)), graph.edgesOf(56L));

		assertEquals(ObjectOpenHashSet.of(of(56, 78)), graph.incomingEdgesOf(78L));

		assertEquals(2, graph.outDegreeOf(12L));
		assertEquals(4, graph.outDegreeOf(56L));
		assertEquals(2, graph.inDegreeOf(56L));
		assertEquals(6, graph.degreeOf(56L));

		for (final long x : graph.vertexSet()) for (final long y : graph.successors(x)) {
			assertEquals(ObjectOpenHashSet.of(of(x, y)), graph.getAllEdges(x, y));
			assertEquals(of(x, y), graph.getEdge(x, y));
			assertTrue(graph.containsEdge(x, y));
			assertTrue(graph.containsEdge(of(x, y)));
		}

		assertNull(graph.getEdge(12L, 78L));
		assertEquals(Collections.emptySet(), graph.getAllEdges(12L, 78L));
		assertFalse(graph.containsEdge(12L, 78L));
		assertFalse(graph.containsEdge(of(12L, 78L)));
		assertEquals(12, graph.getEdgeSource(of(12L, 78L)));
		assertEquals(78, graph.getEdgeTarget(of(12L, 78L)));

		for (final long y : graph.vertexSet()) for (final long x : graph.predecessors(y)) {
			assertEquals(ObjectOpenHashSet.of(of(x, y)), new ObjectOpenHashSet<>(graph.getAllEdges(x, y)));
			assertEquals(of(x, y), graph.getEdge(x, y));
			assertTrue(graph.containsEdge(x, y));
			assertTrue(graph.containsEdge(of(x, y)));
		}

		assertEquals(LongOpenHashSet.of(12, 34, 78, 56), new LongOpenHashSet(graph.successors(56)));
		assertEquals(LongOpenHashSet.of(56, 78), graph.externalNodes());
		assertEquals(LongOpenHashSet.of(56), new LongOpenHashSet(graph.predecessors(12)));
		assertEquals(LongOpenHashSet.of(12, 56), new LongOpenHashSet(graph.predecessors(34)));
		assertEquals(LongOpenHashSet.of(12, 56), new LongOpenHashSet(graph.predecessors(56)));
		assertEquals(LongOpenHashSet.of(56), new LongOpenHashSet(graph.predecessors(78)));

		assertEquals(graph.getEdgeWeight(of(12, 34)), 1);

		Assertions.assertThrows(UnsupportedOperationException.class, () -> {
			graph.setEdgeWeight(of(12, 34), 2);
		});

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			graph.successors(1);
		});

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			graph.predecessors(1);
		});

		assertEquals(LongOpenHashSet.of(12, 34, 56, 78), graph.nodes());
		assertTrue(graph.isInternal(12));
		assertTrue(graph.isInternal(34));
		assertTrue(graph.isExternal(56));
		assertTrue(graph.isExternal(78));
		assertFalse(graph.isExternal(12));
		assertFalse(graph.isExternal(34));
		assertFalse(graph.isInternal(56));
		assertFalse(graph.isInternal(78));

		assertEquals(ObjectOpenHashSet.of(of(12, 34), of(12, 56), of(56, 12), of(56, 78), of(56, 34), of(56, 56)), graph.edgeSet());

		final DirectedGraph transpose = graph.transpose();
		assertEquals(graph.numNodes(), transpose.numNodes());
		assertEquals(graph.numArcs(), transpose.numArcs());
		assertEquals(graph.nodes(), transpose.nodes());
		for (final long x : graph.nodes()) {
			assertEquals(new LongOpenHashSet(graph.successors(x)), new LongOpenHashSet(transpose.predecessors(x)));
			assertEquals(new LongOpenHashSet(graph.predecessors(x)), new LongOpenHashSet(transpose.successors(x)));
			assertTrue(graph.isInternal(x) == transpose.isInternal(x));
			assertTrue(graph.isExternal(x) == transpose.isExternal(x));
		}

		final DirectedGraph transposeTranspose = transpose.transpose();
		assertEquals(transposeTranspose.numNodes(), transpose.numNodes());
		assertEquals(transposeTranspose.numArcs(), transpose.numArcs());
		assertEquals(transposeTranspose.nodes(), transpose.nodes());
		for (final long x : transposeTranspose.nodes()) {
			assertEquals(new LongOpenHashSet(transposeTranspose.successors(x)), new LongOpenHashSet(transpose.predecessors(x)));
			assertEquals(new LongOpenHashSet(transposeTranspose.predecessors(x)), new LongOpenHashSet(transpose.successors(x)));
			assertTrue(transposeTranspose.isInternal(x) == transpose.isInternal(x));
			assertTrue(transposeTranspose.isExternal(x) == transpose.isExternal(x));
		}

		assertEquals(transposeTranspose.externalNodes(), transpose.externalNodes());

		final Iterator<Node<Long>> nodesWithData = graph.getNodesWithData(graph.iterator());
		nodesWithData.forEachRemaining(n -> assertEquals(n.node, n.data));

		final LongList sums = new LongArrayList();
		for(final LongIterator nodes = graph.iterator(); nodes.hasNext();) {
			final long x = nodes.nextLong();
			graph.successors(x).forEach(e -> sums.add(x + e));
		}

		final Iterator<Arc<Long>> arcsWithData = graph.getArcsWithData(sums.iterator());
		arcsWithData.forEachRemaining(a -> assertEquals(a.data, a.source + a.target));
	}
}
