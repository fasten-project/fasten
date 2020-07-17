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
import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CallGraphMerger {

    private static Logger logger = LoggerFactory.getLogger(CallGraphMerger.class);

    public static CallGraphMerger resolve(final MavenCoordinate coordinate) {

        final var PDN =
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate);

        final List<List<FastenURI>> depencencyList = getDependenciesURI(PDN);

        for (List<FastenURI> fastenURIS : depencencyList) {
            final List<RevisionCallGraph> revisionCallGraphs =
                loadRevisionCallGraph(fastenURIS);
//                List<ExtendedRevisionCallGraph> resolvedCallGraphs =
//                mergeCallGraphs(revisionCallGraphs);
        }

        return null;
    }

    public static RevisionCallGraph mergeCallGraph(final RevisionCallGraph artifact,
                                                   final List<RevisionCallGraph>
                                                               dependencies,
                                                   final String algorithm) {
        if (algorithm.equals("RA")) {
            return mergeWithRA(artifact, dependencies);
        } else if (algorithm.equals("CHA")) {
            return mergeWithCHA(artifact, dependencies);
        } else {
            logger.warn("{} algorithm is not supported for merge, please inter RA or CHA.",
                algorithm);
            return null;
        }

    }


    private static RevisionCallGraph mergeWithCHA(RevisionCallGraph artifact,
                                                  List<RevisionCallGraph> dependencies) {

        final Map<Pair<Integer, FastenURI>, Map<String, String>> result = new HashMap<>();

        for (final var arc : artifact.getGraph().getExternalCalls().entrySet()) {
            boolean isResolved = false;

            //Dynamic dispatch calls
            if (Integer.parseInt(arc.getValue().getOrDefault("invokedvirtual", "0")) > 0 ||
                Integer.parseInt(arc.getValue().getOrDefault("invokedinterface", "0")) > 0) {
                final var receiverType = getTypeURI(arc.getKey().getValue());
                dependencies.add(artifact);

                //Go through all dependencies
                for (final var dep : dependencies) {

                    //Find receiver type in dependencies.
                    if (dep.getClassHierarchy().containsKey(receiverType)) {

                        //If type implements the method add an arc to result
                        if (dep.getClassHierarchy().get(receiverType).getMethods()
                            .containsValue(
                                FastenURI.create(arc.getKey().getValue().getRawPath()))) {

                            result.put(new MutablePair<>(arc.getKey().getLeft(), new FastenJavaURI(
                                    arc.getKey().getValue().toString()
                                        .replace("///", "//" + dep.product + "$" + dep.version + "/"))),
                                arc.getValue());

                            isResolved = true;
                        }
                    }

                    //Check for the subtypes as well.
                    for (final var type : dep.getClassHierarchy().entrySet()) {
                        for (final var superClass : type.getValue().getSuperClasses()) {
                            if (superClass.equals(receiverType)) {
                                if (resolve(result, arc,
                                    new ArrayList<>(type.getValue().getMethods().values()),
                                    dep.product + "$" + dep.version)) {
                                    isResolved = true;
                                }
                            }
                        }
                        for (final var superInterface : type.getValue().getSuperInterfaces()) {
                            if (superInterface.equals(receiverType)) {
                                if (resolve(result, arc,
                                    new ArrayList<>(type.getValue().getMethods().values()),
                                    dep.product + "$" + dep.version)) {
                                    isResolved = true;
                                }
                            }
                        }
                    }
                }
                //Not dynamic dispatch, search for the full signature in dependencies
            } else {
                for (final var dep : dependencies) {
                    if (dep.mapOfAllMethods()
                        .containsValue(FastenURI.create(arc.getKey().getValue().getRawPath()))) {
                        result.put(new MutablePair<>(arc.getKey().getLeft(),
                            new FastenJavaURI(arc.getKey().getValue().toString().replace("///", "//"
                                + dep.product + "$" + dep.version + "/"))), arc.getValue());
                        isResolved = true;
                    }
                }
            }
            if (!isResolved) {
                result.put(arc.getKey(), arc.getValue());
            }
        }

        return RevisionCallGraph.extendedBuilder().forge(artifact.forge)
            .cgGenerator(artifact.getCgGenerator())
            .classHierarchy(artifact.getClassHierarchy())
            .depset(artifact.depset)
            .product(artifact.product)
            .timestamp(artifact.timestamp)
            .version(artifact.version)
            .graph(new RevisionCallGraph.Graph(artifact.getGraph().getInternalCalls(),
                result))
            .build();
    }

    private static boolean resolve(Map<Pair<Integer, FastenURI>, Map<String, String>> result,
                                   Map.Entry<Pair<Integer, FastenURI>, Map<String, String>> arc,
                                   List<FastenURI> methods,
                                   String product) {
        for (final var method : methods) {
            if (method.getEntity().contains(getSignature(arc.getKey().getValue().getEntity()))) {

                result.put(new MutablePair<>(arc.getKey().getLeft(),
                    new FastenJavaURI("//" + product + method)), arc.getValue());

                return true;
            }
        }
        return false;
    }

    private static String getSignature(final String Entity) {
        return Entity.substring(Entity.indexOf(".") + 1);
    }

    public static RevisionCallGraph mergeWithRA(final RevisionCallGraph artifact,
                                                final List<RevisionCallGraph>
                                                            dependencies) {

        final Map<Pair<Integer, FastenURI>, Map<String, String>> result = new HashMap<>();

        for (final var arc : artifact.getGraph().getExternalCalls().entrySet()) {
            final var call = arc.getKey();
            final var target = call.getValue();

            //Go through all dependencies
            for (final var dep : dependencies) {
                boolean isResolved = false;

                //Check whether target's signature can be found in the dependency methods
                for (final var method : dep.mapOfAllMethods().entrySet()) {
                    if (method.getValue().getEntity()
                        .contains(getSignature(target.getEntity()))) {
                        result
                            .put(new MutablePair<>(call.getKey(), new FastenJavaURI(
                                    "//" + dep.product + "$" + dep.version + method.getValue())),
                                arc.getValue());
                        isResolved = true;
                    }
                }
                if (!isResolved) {
                    result.put(arc.getKey(), arc.getValue());
                }
            }
        }

        return RevisionCallGraph.extendedBuilder().forge(artifact.forge)
            .cgGenerator(artifact.getCgGenerator())
            .classHierarchy(artifact.getClassHierarchy())
            .depset(artifact.depset)
            .product(artifact.product)
            .timestamp(artifact.timestamp)
            .version(artifact.version)
            .graph(new RevisionCallGraph.Graph(artifact.getGraph().getInternalCalls(),
                result))
            .build();
    }


    private static FastenURI getTypeURI(final FastenURI callee) {
        return new FastenJavaURI("/" + callee.getNamespace() + "/"
            + callee.getEntity().substring(0, callee.getEntity().indexOf(".")));
    }

    private static List<RevisionCallGraph> loadRevisionCallGraph(
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


