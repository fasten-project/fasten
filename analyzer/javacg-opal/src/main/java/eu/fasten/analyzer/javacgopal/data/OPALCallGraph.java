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
package eu.fasten.analyzer.javacgopal.data;

import java.net.URL;

import org.opalj.br.analyses.Project;
import org.opalj.tac.cg.CallGraph;

import eu.fasten.core.data.callgraph.CGAlgorithm;

public class OPALCallGraph {

	public final CGAlgorithm algorithm;
	public final Project<URL> project;
	public final CallGraph callGraph;

	public OPALCallGraph(CGAlgorithm algorithm, Project<URL> project, CallGraph callGraph) {
		this.algorithm = algorithm;
		this.project = project;
		this.callGraph = callGraph;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((algorithm == null) ? 0 : algorithm.hashCode());
		result = prime * result + ((callGraph == null) ? 0 : callGraph.hashCode());
		result = prime * result + ((project == null) ? 0 : project.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OPALCallGraph other = (OPALCallGraph) obj;
		if (algorithm != other.algorithm)
			return false;
		if (callGraph == null) {
			if (other.callGraph != null)
				return false;
		} else if (!callGraph.equals(other.callGraph))
			return false;
		if (project == null) {
			if (other.project != null)
				return false;
		} else if (!project.equals(other.project))
			return false;
		return true;
	}
}