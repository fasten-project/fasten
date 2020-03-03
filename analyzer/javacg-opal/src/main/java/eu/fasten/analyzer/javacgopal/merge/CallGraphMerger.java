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

package eu.fasten.analyzer.javacgopal.merge;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class CallGraphMerger {

    public static CallGraphMerger resolve(final MavenCoordinate coordinate) {

        final var PDN =
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate());

        final List<List<FastenURI>> depencencyList = getDependenciesURI(PDN);

        for (List<FastenURI> fastenURIS : depencencyList) {
            final List<ExtendedRevisionCallGraph> revisionCallGraphs =
                loadRevisionCallGraph(fastenURIS);
//                List<ExtendedRevisionCallGraph> resolvedCallGraphs =
//                mergeCallGraphs(revisionCallGraphs);
        }

        return null;
    }

    public static ExtendedRevisionCallGraph mergeCallGraph(final ExtendedRevisionCallGraph artifact,
                                                           final List<ExtendedRevisionCallGraph>
                                                               dependencies) {

        final var mapOfAllMethods = artifact.mapOfAllMethods();

        final Map<Pair<Integer, FastenURI>, Map<String, String>> unresolvedCalls = new HashMap<>();

        for (final var entry : artifact.getGraph()
            .getUnresolvedCalls().entrySet()) {
            Pair<Integer, FastenURI> call = entry.getKey();
            Map<String, String> metadata = entry.getValue();
            final var source = mapOfAllMethods.get(call.getKey());
            final var target = call.getValue();
            final var isSuperClassMethod =
                artifact.getClassHierarchy().get(getTypeURI(source)).getSuperClasses()
                    .contains(getTypeURI(target));
            nextCall:

            //Foreach unresolved call
            if (target.toString().startsWith("///")) {

                //Go through all dependencies
                for (ExtendedRevisionCallGraph dependency : dependencies) {
                    var resolvedMethod = target.toString();

                    nextDependency:
                    //Check whether this method is inside the dependency
                    if (dependency.getClassHierarchy().containsKey(getTypeURI(target))) {
                        if (dependency.getClassHierarchy().get(getTypeURI(target)).getMethods()
                            .containsValue(FastenURI.create(target.getRawPath()))) {
                            resolvedMethod =
                                target.toString().replace("///", "//" +
                                    dependency.product + "/");
                            //Check if this call is related to a super class
                            if (isSuperClassMethod) {
                                //Find that super class. in case there are two, pick the first one
                                // since the order of instantiation matters
                                for (FastenURI superClass : artifact.getClassHierarchy()
                                    .get(getTypeURI(source)).getSuperClasses()) {
                                    //Check if this dependency contains the super class that we want
                                    if (dependency.getClassHierarchy().containsKey(superClass)) {
                                        unresolvedCalls.put(new MutablePair<>(call.getKey(), new FastenJavaURI(resolvedMethod)), metadata);
                                        break nextCall;
                                    } else {
                                        unresolvedCalls.put(new MutablePair<>(call.getKey(), target), metadata);
                                        break nextDependency;
                                    }
                                }
                            } else {
                                unresolvedCalls.put(new MutablePair<>(call.getKey(), new FastenJavaURI(resolvedMethod)), metadata);
                            }
                        }
                    }
                }
            }
        }

        return ExtendedRevisionCallGraph.extendedBuilder().forge(artifact.forge)
            .cgGenerator(artifact.getCgGenerator())
            .classHierarchy(artifact.getClassHierarchy())
            .depset(artifact.depset)
            .product(artifact.product)
            .timestamp(artifact.timestamp)
            .version(artifact.version)
            .graph(new ExtendedRevisionCallGraph.Graph(artifact.getGraph().getResolvedCalls(),
                unresolvedCalls))
            .build();
    }


    private static FastenURI getTypeURI(final FastenURI callee) {
        return new FastenJavaURI("/" + callee.getNamespace() + "/" +
            callee.getEntity().substring(0, callee.getEntity().indexOf(".")));
    }

    private static List<ExtendedRevisionCallGraph> loadRevisionCallGraph(
        final List<FastenURI> uri) {

        //TODO load RevisionCallGraphs
        return null;
    }

    private static List<List<FastenURI>> getDependenciesURI(
        final List<List<RevisionCallGraph.Dependency>> PDN) {

        final List<List<FastenURI>> allProfilesDependenices = null;

        for (List<RevisionCallGraph.Dependency> dependencies : PDN) {

            final List<FastenURI> oneProfileDependencies = null;
            for (RevisionCallGraph.Dependency dependency : dependencies) {
                oneProfileDependencies.add(FastenURI.create(
                    "fasten://mvn" + "!" + dependency.product + "$" + dependency.constraints));
            }

            allProfilesDependenices.add(oneProfileDependencies);
        }

        return allProfilesDependenices;
    }

    public static CallGraphMerger resolve(
        final List<List<RevisionCallGraph.Dependency>> packageDependencyNetwork) {
        return null;
    }
}
