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

package eu.fasten.analyzer.javacgopal.data;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.opalj.br.analyses.Project;
import org.opalj.log.ConsoleOPALLogger;
import org.opalj.log.Fatal$;
import org.opalj.log.GlobalLogContext$;
import org.opalj.log.OPALLogger;
import org.opalj.tac.cg.AllocationSiteBasedPointsToCallGraphKey$;
import org.opalj.tac.cg.CHACallGraphKey$;
import org.opalj.tac.cg.CallGraph;
import org.opalj.tac.cg.RTACallGraphKey$;
import org.opalj.tac.cg.TypeBasedPointsToCallGraphKey$;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import eu.fasten.core.data.opal.exceptions.OPALException;

public class CallGraphConstructor {

	private final Project<URL> project;
	private final CallGraph callGraph;
	private final CGAlgorithm algorithm;

	/**
	 * Constructs a call graph given file, algorithm and a main class in case of
	 * application.
	 *
	 * @param pkg       package to be analyzed
	 * @param mainClass main class of the package in case of application
	 * @param algorithm algorithm for generating call graph
	 */
	public CallGraphConstructor(final File pkg, final String mainClass, CGAlgorithm algorithm) {
		this(new File[] { pkg }, new File[0],mainClass, algorithm);
	}

	/**
	 * Constructs a call graph given file, algorithm and a main class in case of
	 * application.
	 *
	 * @param pkg       package to be analyzed
	 * @param deps      array of all dependencies that should be considered as well
	 * @param mainClass main class of the package in case of application
	 * @param algorithm algorithm for generating call graph
	 */
	public CallGraphConstructor(File[] classFiles, File[] deps, final String mainClass, CGAlgorithm algorithm) {
		assertDependencies(classFiles, deps);
		this.algorithm = algorithm;
		try {
			project = Project.apply(classFiles, deps, GlobalLogContext$.MODULE$, createConfig(mainClass));

			OPALLogger.updateLogger(GlobalLogContext$.MODULE$, new ConsoleOPALLogger(false, Fatal$.MODULE$));
			OPALLogger.updateLogger(project.logContext(), new ConsoleOPALLogger(false, Fatal$.MODULE$));

			callGraph = generateCallGraph();
		} catch (Exception e) {
			throw new OPALException(e);
		}
	}

	private static void assertDependencies(File[] classFiles, File[] deps) {
		for (File c : classFiles) {
			if (!c.exists() || !c.isFile()) {
				throw new IllegalArgumentException("class file does not exist or is not a file: " + c);
			}
			if(!c.getName().endsWith(".class")) {
				throw new IllegalArgumentException("provide file does not look like a class file: " + c);
			}
		}
		for (File dep : deps) {
			if (!dep.exists() || !dep.isFile()) {
				throw new IllegalArgumentException("dependency does not exist or is not a file: " + dep);
			}
		}
	}

	/**
	 * Get configuration for call graph generator.
	 *
	 * @param mainClass main class in case the project is an application
	 * @return configuration for running call graph generator
	 */
	private Config createConfig(String mainClass) {
		var entryPointFinder = "org.opalj.br.analyses.cg.ConfigurationEntryPointsFinder";
		var instantiatedTypeFinder = "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder";

		Config cfg = ConfigFactory.load()
				.withValue("org.opalj.br.reader.ClassFileReader.Invokedynamic.rewrite",
						ConfigValueFactory.fromAnyRef(true))
				.withValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
						ConfigValueFactory.fromAnyRef(entryPointFinder))
				.withValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
						ConfigValueFactory.fromAnyRef(instantiatedTypeFinder));

		boolean hasMainClass = mainClass != null && !mainClass.isEmpty();
		if (hasMainClass) {
			var initialEntryPoints = Stream
					.of(ConfigValueFactory
							.fromMap(Map.of("declaringClass", mainClass.replace('.', '/'), "name", "main")))
					.collect(Collectors.toList());

			cfg = cfg.withValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints",
					ConfigValueFactory.fromIterable(initialEntryPoints));
		}

		System.out.println(cfg.toString());
		
		return cfg;
	}

	/**
	 * Generates a call graph for a given project using given algorithm.
	 *
	 * @param project {@link Project} the project to generate call graph for
	 * @return {@link CallGraph} resulting call graph
	 */
	private CallGraph generateCallGraph() {
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

	public Project<URL> getProject() {
		return project;
	}

	public CallGraph getCallGraph() {
		return callGraph;
	}
}