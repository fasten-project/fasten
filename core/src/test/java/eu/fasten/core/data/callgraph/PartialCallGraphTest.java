/*
 * Copyright 2021 Delft University of Technology
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.data.callgraph;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.JavaGraph;
import eu.fasten.core.data.JavaScope;
import it.unimi.dsi.fastutil.ints.IntIntPair;

public class PartialCallGraphTest {
	
	private PartialCallGraph sut;

	@BeforeEach
	public void setup() {
		sut = new PartialCallGraph();
	}
	
	@Test
	public void defaults() {
		assertNotNull(sut.graph);
		assertNotNull(sut.classHierarchy);
		assertEquals(-1, sut.nodeCount);
	}
	
	@Test
	public void equalityDefault() {
		var a = new PartialCallGraph();
		var b = new PartialCallGraph();
		
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}
	
	@Test
	public void equalityDiffGraph() {
		var cs = new HashMap<IntIntPair, Map<Object, Object>>();
		cs.put(IntIntPair.of(0, 1), null);
		
		var a = new PartialCallGraph();
		a.graph.append(new JavaGraph(cs));
		var b = new PartialCallGraph();
		
		assertNotEquals(a, b);
		assertNotEquals(a.hashCode(), b.hashCode());
	}
	
	@Test
	public void equalityDiffClassHierarchy() {
		var a = new PartialCallGraph();
		a.classHierarchy.put(JavaScope.internalTypes, null);
		var b = new PartialCallGraph();
		
		assertNotEquals(a, b);
		assertNotEquals(a.hashCode(), b.hashCode());
	}
	
	@Test
	public void equalityDiffNodeCount() {
		var a = new PartialCallGraph();
		a.nodeCount = 123;
		var b = new PartialCallGraph();
		
		assertNotEquals(a, b);
		assertNotEquals(a.hashCode(), b.hashCode());
	}
	
	@Test
	public void toStringImplemented() {
		var actual = new PartialCallGraph().toString();
		
		// contains field info
		assertTrue(actual.contains("classHierarchy"));
		// prints multi-line
		assertTrue(actual.contains("\n"));
	}
}