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
import java.net.URL;

import org.opalj.br.analyses.Project;
import org.opalj.log.GlobalLogContext$;
import org.opalj.log.LogContext;
import org.opalj.log.LogMessage;
import org.opalj.log.OPALLogger;
import org.opalj.tac.cg.AllocationSiteBasedPointsToCallGraphKey$;
import org.opalj.tac.cg.CHACallGraphKey$;
import org.opalj.tac.cg.CallGraph;
import org.opalj.tac.cg.RTACallGraphKey$;
import org.opalj.tac.cg.TypeBasedPointsToCallGraphKey$;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import eu.fasten.analyzer.javacgopal.data.OPALCallGraph;
import eu.fasten.core.data.callgraph.CGAlgorithm;
import eu.fasten.core.data.opal.exceptions.OPALException;

public class OPALCallGraphConstructor {

	/**
	 * Constructs a call graph given a single class/jar and an algorithm.
	 *
	 * @param au        analysis unit (class/jar) to be analyzed
	 * @param algorithm algorithm for generating call graph
	 */
	public OPALCallGraph construct(final File au, CGAlgorithm algorithm) {
		return construct(new File[] { au }, new File[0], algorithm);
	}

	/**
	 * Constructs a call graph given an array of "project" classes/jars, "library"
	 * classes/jars and an algorithm. The difference between pkgs and deps is only
	 * relevant for OPAL, where several details get only preserved for pkgs.
	 *
	 * @param aus       array of analysis units (classes/jars) to be analyzed
	 * @param deps      array of all classes/jars that are used for dependencies
	 * @param algorithm algorithm for generating call graph
	 */
	public OPALCallGraph construct(File[] aus, File[] deps, CGAlgorithm algorithm) {
		assertDependencies(aus, deps);
		try {
			// for debugging, one can use: new ConsoleOPALLogger(false, Fatal$.MODULE$)
			OPALLogger.updateLogger(GlobalLogContext$.MODULE$, new NoOutputLogger());
			var project = Project.apply(aus, deps, GlobalLogContext$.MODULE$, createConfig());
			var callGraph = generateCallGraph(algorithm, project);
			return new OPALCallGraph(algorithm, project, callGraph);
		} catch (Exception e) {
			throw new OPALException(e);
		}
	}

	private CallGraph generateCallGraph(CGAlgorithm algorithm, Project<URL> project) {
		switch (algorithm) {
		case RTA:
			return project.get(RTACallGraphKey$.MODULE$);
		case CHA:
			return project.get(CHACallGraphKey$.MODULE$);
		case AllocationSiteBasedPointsTo:
			return project.get(AllocationSiteBasedPointsToCallGraphKey$.MODULE$);
		case TypeBasedPointsTo:
			return project.get(TypeBasedPointsToCallGraphKey$.MODULE$);
		default:
			throw new IllegalStateException("Unexpected value: " + algorithm);
		}
	}

	private static void assertDependencies(File[] pkgs, File[] deps) {
		for (File c : pkgs) {
			if (!c.exists() || !c.isFile()) {
				throw new IllegalArgumentException("analysis unit does not exist or is not a file: " + c);
			}
		}
		for (File dep : deps) {
			if (!dep.exists() || !dep.isFile()) {
				throw new IllegalArgumentException("dependency does not exist or is not a file: " + dep);
			}
		}
	}

	private static Config createConfig() {

		var entryPointFinder = "org.opalj.br.analyses.cg.LibraryEntryPointsFinder";
		var instantiatedTypeFinder = "org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder";

		Config cfg = ConfigFactory.load()
				.withValue("org.opalj.br.reader.ClassFileReader.Invokedynamic.rewrite",
						ConfigValueFactory.fromAnyRef(true))
				.withValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
						ConfigValueFactory.fromAnyRef(entryPointFinder))
				.withValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
						ConfigValueFactory.fromAnyRef(instantiatedTypeFinder));

		return cfg;
	}

	private class NoOutputLogger implements OPALLogger {
		@Override
		public void log(LogMessage message, LogContext ctx) {
			// ignore all logging
		}
	}
}