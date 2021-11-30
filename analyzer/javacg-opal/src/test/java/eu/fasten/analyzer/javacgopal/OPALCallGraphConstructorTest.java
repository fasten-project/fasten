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

package eu.fasten.analyzer.javacgopal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static eu.fasten.core.utils.TestUtils.getTestResource;
import static org.junit.jupiter.api.Assertions.*;

import eu.fasten.analyzer.javacgopal.OPALCallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraph;
import eu.fasten.core.data.callgraph.CGAlgorithm;

class OPALCallGraphConstructorTest {

	private OPALCallGraphConstructor sut;

	@BeforeEach
	public void setup() {
		sut = new OPALCallGraphConstructor();
	}

	@Test
	void constructCHAHasCorrectConfig() {
		var file = getTestResource("SingleSourceToTarget.class");

		var cg = sut.construct(file, CGAlgorithm.CHA);

		var confValue1 = cg.project.config().getValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis");
		assertEquals("org.opalj.br.analyses.cg.LibraryEntryPointsFinder",
				confValue1.render().substring(1, confValue1.render().length() - 1));

		var confValue2 = cg.project.config().getValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis");
		assertEquals("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder",
				confValue2.render().substring(1, confValue2.render().length() - 1));
	}

	@Test
	void constructCHA() {
		var file = getTestResource("SingleSourceToTarget.class");

		var cg = sut.construct(file, CGAlgorithm.CHA);

		assertNotNull(cg.callGraph);
		assertNotNull(cg.project);
		assertTrue(cg.callGraph.numEdges() > 0);
	}

	@Test
	void constructRTA() {
		var file = getTestResource("SingleSourceToTarget.class");

		var cg = sut.construct(file, CGAlgorithm.RTA);

		assertNotNull(cg.callGraph);
		assertNotNull(cg.project);
		assertTrue(cg.callGraph.numEdges() > 0);
	}

	@Test
	void constructAllocationSiteBasedPointsTo() {
		var file = getTestResource("SingleSourceToTarget.class");

		var cg = sut.construct(file, CGAlgorithm.AllocationSiteBasedPointsTo);

		assertNotNull(cg.callGraph);
		assertNotNull(cg.project);
		assertTrue(cg.callGraph.numEdges() > 0);
	}

	@Test
	void constructTypeBasedPointsTo() {
		var file = getTestResource("SingleSourceToTarget.class");

		var cg = sut.construct(file, CGAlgorithm.TypeBasedPointsTo);

		assertNotNull(cg.callGraph);
		assertNotNull(cg.project);
		assertTrue(cg.callGraph.numEdges() > 0);
	}
}