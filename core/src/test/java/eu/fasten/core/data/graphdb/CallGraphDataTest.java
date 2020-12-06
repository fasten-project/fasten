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

package eu.fasten.core.data.graphdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.util.Arrays;

import eu.fasten.core.data.GOV3LongFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterators;
import it.unimi.dsi.webgraph.Transform;
import it.unimi.dsi.webgraph.examples.ErdosRenyiGraph;

public class CallGraphDataTest {

	@Test
	public void test() throws IOException {
		final ImmutableGraph graph = new ArrayListMutableGraph(new ErdosRenyiGraph(100, .1)).immutableView();
		final ImmutableGraph transpose = Transform.transpose(graph);

		final long[] LID2GID = new long[graph.numNodes()];
		final XoRoShiRo128PlusPlusRandomGenerator g = new XoRoShiRo128PlusPlusRandomGenerator(0);
		for(int i = 0; i< LID2GID.length; i++) LID2GID[i] = g.nextLong();
		final GOV3LongFunction GID2LID = new GOV3LongFunction.Builder().keys(LongArrayList.wrap(LID2GID)).build();
		final CallGraphData callGraphData = new CallGraphData(graph, transpose, null, null, LID2GID, GID2LID, 50, 100);
		for(final long node : callGraphData.nodes()) {
			final LongList s = callGraphData.successors(node);
			assertEquals(new LongOpenHashSet(s), IntArrayList.wrap(LazyIntIterators.unwrap(graph.successors((int)GID2LID.getLong(node)))).stream().map(x -> LID2GID[x]).collect(Collectors.toSet()));
			final LongList p = callGraphData.predecessors(node);
			assertEquals(new LongOpenHashSet(p), IntArrayList.wrap(LazyIntIterators.unwrap(transpose.successors((int)GID2LID.getLong(node)))).stream().map(x -> LID2GID[x]).collect(Collectors.toSet()));
		}

		assertTrue(callGraphData.isInternal(LID2GID[0]));
		assertTrue(callGraphData.isInternal(LID2GID[49]));
		assertFalse(callGraphData.isExternal(LID2GID[0]));
		assertFalse(callGraphData.isExternal(LID2GID[49]));

		assertSame(graph, callGraphData.rawGraph());
		assertSame(transpose, callGraphData.rawTranspose());

		assertTrue(callGraphData.isExternal(LID2GID[50]));
		assertTrue(callGraphData.isExternal(LID2GID[51]));
		assertFalse(callGraphData.isInternal(LID2GID[50]));
		assertFalse(callGraphData.isInternal(LID2GID[51]));

		assertEquals(callGraphData.externalNodes(), new LongOpenHashSet(Arrays.copyOfRange(LID2GID, 50, 100)));

		final CallGraphData copy = new CallGraphData(graph.copy(), transpose.copy(), null, null, LID2GID, GID2LID, 50, 100);
		assertEquals(callGraphData, copy);
		assertEquals(callGraphData.toString(), copy.toString());
		assertTrue(callGraphData.equals(callGraphData));
		assertFalse(callGraphData.equals(Long.valueOf(0)));
		assertFalse(callGraphData.equals(new CallGraphData(transpose.copy(), graph.copy(), null, null, LID2GID, GID2LID, 50, 100)));
		assertEquals(callGraphData.hashCode(), copy.hashCode());
 	}
}
