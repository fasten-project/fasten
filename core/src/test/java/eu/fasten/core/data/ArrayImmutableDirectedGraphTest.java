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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public class ArrayImmutableDirectedGraphTest {

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
        builder.addArc(56, 78);
        builder.addArc(12, 34);
        builder.addArc(56, 12);
        builder.addArc(12, 56);
        builder.addArc(56, 34);
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
        assertEquals(5, graph.numArcs());
		final LongIterator iterator = graph.iterator();
		assertEquals(12, iterator.nextLong());
		assertEquals(34, iterator.nextLong());
		assertEquals(56, iterator.nextLong());
		assertEquals(78, iterator.nextLong());
        assertEquals(new LongOpenHashSet(new long[]{34, 56}), new LongOpenHashSet(graph.successors(12)));
        assertEquals(new LongOpenHashSet(new long[]{12, 34, 78}), new LongOpenHashSet(graph.successors(56)));
        assertEquals(new LongOpenHashSet(new long[]{56, 78}), graph.externalNodes());
        assertEquals(new LongOpenHashSet(new long[]{56}), new LongOpenHashSet(graph.predecessors(12)));
        assertEquals(new LongOpenHashSet(new long[]{12, 56}), new LongOpenHashSet(graph.predecessors(34)));
        assertEquals(new LongOpenHashSet(new long[]{12}), new LongOpenHashSet(graph.predecessors(56)));
        assertEquals(new LongOpenHashSet(new long[]{56}), new LongOpenHashSet(graph.predecessors(78)));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            graph.successors(1);
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            graph.predecessors(1);
        });

        assertEquals(new LongOpenHashSet(new long[]{12, 34, 56, 78}), graph.nodes());
        assertTrue(graph.isInternal(12));
        assertTrue(graph.isInternal(34));
        assertTrue(graph.isExternal(56));
        assertTrue(graph.isExternal(78));
        assertFalse(graph.isExternal(12));
        assertFalse(graph.isExternal(34));
        assertFalse(graph.isInternal(56));
        assertFalse(graph.isInternal(78));

    }

	@Test
	public void testNoPred() {
		final ArrayImmutableDirectedGraph.Builder builder = new ArrayImmutableDirectedGraph.Builder();
		builder.addInternalNode(12);
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			builder.addInternalNode(12);
		});
		builder.addInternalNode(34);
		final ArrayImmutableDirectedGraph graph = builder.build();
		assertEquals(2, graph.numNodes());
	}
}
