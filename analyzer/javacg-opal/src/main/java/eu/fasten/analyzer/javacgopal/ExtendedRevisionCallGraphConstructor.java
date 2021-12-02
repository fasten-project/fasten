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

import java.io.File;

import org.apache.commons.io.FileUtils;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.callgraph.CGAlgorithm;
import eu.fasten.core.data.callgraph.CallPreservationStrategy;
import eu.fasten.core.data.opal.MavenArtifactDownloader;
import eu.fasten.core.data.opal.MavenCoordinate;

public class ExtendedRevisionCallGraphConstructor {

	private OPALCallGraphConstructor ocgc = new OPALCallGraphConstructor();
	private PartialCallGraphConstructor pcg = new PartialCallGraphConstructor();

	public ExtendedRevisionJavaCallGraph create(MavenCoordinate coordinate,
			CGAlgorithm algorithm, long timestamp, String artifactRepo, CallPreservationStrategy cps) {

		File file = null;
		try {

			file = new MavenArtifactDownloader(coordinate).downloadArtifact(artifactRepo);
			var opalCG = ocgc.construct(file, algorithm);
			var partialCallGraph = pcg.construct(opalCG, cps);
			// TODO We are supporting multiple Maven Repos... shouldn't this be reflected in the forge?
			return new ExtendedRevisionJavaCallGraph(Constants.mvnForge, coordinate, timestamp, partialCallGraph,
					Constants.opalGenerator);
		} finally {
			FileUtils.deleteQuietly(file);
		}
	}
}