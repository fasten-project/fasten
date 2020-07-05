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

package eu.fasten.analyzer.javacgopal.version3.merge;

import eu.fasten.analyzer.javacgopal.version3.ExtendedRevisionCallGraphV3;
import eu.fasten.analyzer.javacgopal.version3.ExtendedRevisionCallGraphV3.Graph;
import eu.fasten.analyzer.javacgopal.version3.ExtendedRevisionCallGraphV3.Node;
import eu.fasten.analyzer.javacgopal.version3.ExtendedRevisionCallGraphV3.Scope;
import eu.fasten.analyzer.javacgopal.version3.ExtendedRevisionCallGraphV3.Type;
import eu.fasten.analyzer.javacgopal.version3.data.OPALCallSite;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;

import java.util.*;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CallGraphMerger {

    private static Logger logger = LoggerFactory.getLogger(CallGraphMerger.class);

    public static ExtendedRevisionCallGraphV3 mergeCallGraph(final ExtendedRevisionCallGraphV3 artifact,
                                                             final List<ExtendedRevisionCallGraphV3>
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

    public static class CGHA {
        final Map<List<Integer>, Map<Object, Object>> graph;
        final Map<FastenURI, Type> CHA;
        int nodeCount;

        public CGHA(final Map<List<Integer>, Map<Object, Object>> graph,
                    final Map<FastenURI, Type> CHA,
                    final int nodeCount) {
            this.graph = graph;
            this.CHA = CHA;
            this.nodeCount = nodeCount;
        }
    }

    public static class Call {
        List<Integer> indices;
        Map<Object, Object> metadata;
        Node target;

        public Call(List<Integer> indices, Map<Object, Object> metadata,
                    Node target) {
            this.indices = indices;
            this.metadata = metadata;
            this.target = target;
        }
    }

    private static ExtendedRevisionCallGraphV3 mergeWithCHA(final ExtendedRevisionCallGraphV3 artifact,
                                                            final List<ExtendedRevisionCallGraphV3> dependencies) {
        final var result = new CGHA(artifact.getGraphV3().getResolvedCalls(),
                artifact.getClassHierarchyV3().getOrDefault(Scope.resolvedTypes, new HashMap<>()),
                artifact.getNodeCount());
        final var methods = artifact.mapOfAllMethodsV3();
        dependencies.add(artifact);
        final var universalCHA = createUniversalCHA(dependencies);
        for (final var arc : artifact.getGraphV3().getExternalCalls().entrySet()) {
            final var call = new Call(arc.getKey(), arc.getValue(), methods.get(arc.getKey().get(1)));

            for (final var cs : arc.getValue().entrySet()) {
                final var callSite = (OPALCallSite) cs.getValue();
                final var receiverType = FastenURI.create(callSite.getReceiver());

                for (final var dep : dependencies) {
                    final var product = dep.product + "$" + dep.version;
                    for (final var typeEntry : dep.getClassHierarchyV3().get(Scope.internalTypes).entrySet()) {
                        final var depType = typeEntry.getValue();

                        if (typeEntry.getKey().equals(receiverType)) {
                            resolveIfDefined(result, call, depType, product);
                        }
                        if (callSite.is("invokevirtual", "invokeinterface", "invokedynamic")) {

                            if (checkSuperTypes(receiverType, universalCHA, typeEntry.getKey())) {
                                resolveIfDefined(result, call, depType, product);
                            }
                        }
                    }
                }
            }
        }
        return buildRCG(artifact, result);
    }

    private static org.jgrapht.Graph<FastenURI, DefaultEdge> createUniversalCHA(
            final List<ExtendedRevisionCallGraphV3> dependencies) {

        final var result = new DefaultDirectedGraph<FastenURI, DefaultEdge>(DefaultEdge.class);
        for (final var dependency : dependencies) {
            for (final var type : dependency.getClassHierarchyV3().get(Scope.internalTypes).entrySet()) {
                if (!result.containsVertex(type.getKey())) {
                    result.addVertex(type.getKey());
                }
                addSuperTypes(result, type.getKey(), type.getValue().getSuperClasses());
                addSuperTypes(result, type.getKey(), type.getValue().getSuperInterfaces());
            }
        }
        return result;
    }

    private static void addSuperTypes(DefaultDirectedGraph<FastenURI, DefaultEdge> result,
                                      FastenURI sourceTypes,
                                      List<FastenURI> targetTypes) {
        for (final var superClass : targetTypes) {
            if (!result.containsVertex(superClass)) {
                result.addVertex(superClass);
            }
            if (!result.containsEdge(sourceTypes, superClass)) {
                result.addEdge(superClass, sourceTypes);
            }
        }
    }

    private static void resolveIfDefined(final CGHA cgha, final Call call, final Type type, final String product) {
        type.getDefined(getSignature(call.target.getUri().getEntity()))
                .ifPresent(node -> resolve(cgha, new Call(call.indices, call.metadata, node.getValue()), product));
    }

    private static boolean checkSuperTypes(final FastenURI receiverType,
                                           final org.jgrapht.Graph<FastenURI, DefaultEdge> cha, FastenURI depType) {

        for (final var fastenURI : Graphs.predecessorListOf(cha, depType)) {
            if (fastenURI.equals(receiverType)) {
                return true;
            }
        }
        return false;
    }

    private static void resolve(final CGHA cgha, final Call call,
                                final String product) {

        final int targetKey = addToCHA(cgha.CHA, call.target, cgha.nodeCount, product);
        if (targetKey == cgha.nodeCount) {
            cgha.nodeCount++;
        }
        cgha.graph.put(Arrays.asList(call.indices.get(0), targetKey), call.metadata);
    }

    private static int addToCHA(final Map<FastenURI, Type> cha, final Node target, final int nodeCount,
                                String product) {
        final var keyType = new FastenJavaURI(getTypeURI(target.getUri()).toString()
                .replaceFirst("/", java.util.regex.Matcher.quoteReplacement("//" + product + "/")));
        final var type = cha.getOrDefault(keyType, new Type("notFound"));
        final var index = type.addMethod(new Node(target.getUri(), target.getMetadata()), nodeCount);
        cha.put(keyType, type);
        return index;
    }

    private static String getSignature(final String Entity) {
        return Entity.substring(Entity.indexOf(".") + 1);
    }

    public static ExtendedRevisionCallGraphV3 mergeWithRA(final ExtendedRevisionCallGraphV3 artifact,
                                                          final List<ExtendedRevisionCallGraphV3>
                                                                  dependencies) {

        final var result = new CGHA(artifact.getGraphV3().getResolvedCalls(),
                artifact.getClassHierarchyV3().getOrDefault(Scope.resolvedTypes, new HashMap<>()),
                artifact.getNodeCount());
        final var methods = artifact.mapOfAllMethodsV3();

        for (final var arc : artifact.getGraphV3().getExternalCalls().entrySet()) {
            final var call = new Call(arc.getKey(), arc.getValue(), methods.get(arc.getKey().get(1)));

            for (final var dep : dependencies) {
                final var product = dep.product + "$" + dep.version;
                for (final var typeEntry : dep.getClassHierarchyV3().get(Scope.internalTypes).entrySet()) {
                    resolveIfDefined(result, call, typeEntry.getValue(), product);
                }
            }
        }
        return buildRCG(artifact, result);
    }

    private static ExtendedRevisionCallGraphV3 buildRCG(final ExtendedRevisionCallGraphV3 artifact,
                                                        CGHA result) {
        final var cha = new HashMap<>(artifact.getClassHierarchyV3());
        cha.put(Scope.resolvedTypes, result.CHA);
        return ExtendedRevisionCallGraphV3.extendedBuilderV3().forge(artifact.forge)
                .cgGenerator(artifact.getCgGenerator())
                .classHierarchy(cha)
                .depset(artifact.depset)
                .product(artifact.product)
                .timestamp(artifact.timestamp)
                .version(artifact.version)
                .graph(new Graph(artifact.getGraphV3().getInternalCalls(), artifact.getGraphV3().getExternalCalls(),
                        result.graph))
                .nodeCount(result.nodeCount)
                .build();
    }


    private static FastenURI getTypeURI(final FastenURI callee) {
        return new FastenJavaURI("/" + callee.getNamespace() + "/"
                + callee.getEntity().substring(0, callee.getEntity().indexOf(".")));
    }


}


