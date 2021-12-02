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
package eu.fasten.analyzer.javacgopal;

import static eu.fasten.core.data.callgraph.CGAlgorithm.CHA;
import static eu.fasten.core.data.callgraph.CallPreservationStrategy.ONLY_STATIC_CALLSITES;
import static eu.fasten.core.maven.utils.MavenUtilities.MAVEN_CENTRAL_REPO;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;

public class ExtendedRevisionCallGraphConstructorTest {

	private ExtendedRevisionCallGraphConstructor sut;

	@BeforeEach
	public void setup() {
		sut = new ExtendedRevisionCallGraphConstructor();
	}

	@Test
	void createExtendedRevisionJavaCallGraph() throws MissingArtifactException, OPALException {
		var coordinate = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29", "jar");
		var cg = sut.create(coordinate, CHA, 1574072773, MAVEN_CENTRAL_REPO, ONLY_STATIC_CALLSITES);
		assertNotNull(cg);
		Assertions.assertEquals(Constants.mvnForge, cg.forge);
		Assertions.assertEquals("1.7.29", cg.version);
		Assertions.assertEquals(1574072773, cg.timestamp);
		Assertions.assertEquals(new FastenJavaURI("fasten://mvn!org.slf4j:slf4j-api$1.7.29"), cg.uri);
		Assertions.assertEquals(new FastenJavaURI("fasten://org.slf4j:slf4j-api$1.7.29"), cg.forgelessUri);
		Assertions.assertEquals("org.slf4j:slf4j-api", cg.product);
	}

}