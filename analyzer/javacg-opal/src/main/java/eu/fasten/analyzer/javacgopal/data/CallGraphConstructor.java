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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import eu.fasten.core.data.opal.exceptions.OPALException;
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

public class CallGraphConstructor {

    private final Project<URL> project;
    private final CallGraph callGraph;

    /**
     * Constructs a call graph given file, algorithm and a main class in case of application.
     *
     * @param file      file of the package to analyze
     * @param mainClass main class of the package in case of application
     * @param algorithm algorithm for generating call graph
     */
    public CallGraphConstructor(final File file, final String mainClass, final String algorithm)
            throws OPALException {
        try {
            OPALLogger.updateLogger(GlobalLogContext$.MODULE$,
                    new ConsoleOPALLogger(false, Fatal$.MODULE$));

            if (mainClass == null || mainClass.isEmpty()) {
                this.project = Project.apply(file);

            } else {
                final var log = Project.apply(file);
                OPALLogger.updateLogger(log.logContext(),
                        new ConsoleOPALLogger(false, Fatal$.MODULE$));

                this.project = Project
                        .apply(file, log.logContext().successor(), createConfig(mainClass));
            }

            OPALLogger.updateLogger(project.logContext(),
                    new ConsoleOPALLogger(false, Fatal$.MODULE$));
            this.callGraph = generateCallGraph(project, algorithm);
        } catch (Exception e) {
            var opalException = new OPALException(
                    "Original error type: " + e.getClass().getSimpleName()
                            + "; Original message: " + e.getMessage());
            opalException.setStackTrace(e.getStackTrace());
            throw opalException;
        }
    }

    public Project<URL> getProject() {
        return project;
    }

    public CallGraph getCallGraph() {
        return callGraph;
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
        var initialEntryPoints = Stream.of(ConfigValueFactory.fromMap(
                Map.of("declaringClass", mainClass.replace('.', '/'),
                        "name", "main"))).collect(Collectors.toList());

        return ConfigFactory.load()
                .withValue("org.opalj.br.reader.ClassFileReader.Invokedynamic.rewrite",
                        ConfigValueFactory.fromAnyRef(true))
                .withValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
                        ConfigValueFactory.fromAnyRef(entryPointFinder))
                .withValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints",
                        ConfigValueFactory.fromIterable(initialEntryPoints))
                .withValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
                        ConfigValueFactory.fromAnyRef(instantiatedTypeFinder));
    }

    /**
     * Generates a call graph for a given project using given algorithm.
     *
     * @param project {@link Project} the project to generate call graph for
     * @return {@link CallGraph} resulting call graph
     */
    private static CallGraph generateCallGraph(final Project<?> project, final String algorithm) {
        final CallGraph result;
        switch (algorithm) {
            case "RTA":
                result = project.get(RTACallGraphKey$.MODULE$);
                break;
            case "CHA":
                result = project.get(CHACallGraphKey$.MODULE$);
                break;
            case "AllocationSiteBasedPointsTo":
                result = project.get(AllocationSiteBasedPointsToCallGraphKey$.MODULE$);
                break;
            case "TypeBasedPointsTo":
                result = project.get(TypeBasedPointsToCallGraphKey$.MODULE$);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + algorithm);
        }
        return result;
    }
}
