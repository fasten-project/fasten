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

package eu.fasten.core.merge;

import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.Graph;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CallGraphMerger {

    private static final Logger logger = LoggerFactory.getLogger(CallGraphMerger.class);

    public static ExtendedRevisionJavaCallGraph mergeCallGraph(final ExtendedRevisionJavaCallGraph artifact,
                                                           final List<ExtendedRevisionJavaCallGraph>
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
        final Map<FastenURI, JavaType> CHA;
        int nodeCount;

        public CGHA(final Map<List<Integer>, Map<Object, Object>> graph,
                    final Map<FastenURI, JavaType> CHA,
                    final int nodeCount) {
            this.graph = graph;
            this.CHA = CHA;
            this.nodeCount = nodeCount;
        }
    }

    public static class Call {
        List<Integer> indices;
        Map<Object, Object> metadata;
        JavaNode target;

        public Call(List<Integer> indices, Map<Object, Object> metadata,
                    JavaNode target) {
            this.indices = indices;
            this.metadata = metadata;
            this.target = target;
        }

        public boolean isConstructor() {
            return indices.get(0).equals(indices.get(1));
        }
    }

    private static ExtendedRevisionJavaCallGraph mergeWithCHA(final ExtendedRevisionJavaCallGraph artifact,
                                                          final List<ExtendedRevisionJavaCallGraph> dependencies) {
        final var result = new CGHA(artifact.getGraph().getResolvedCalls(),
                artifact.getClassHierarchy().getOrDefault(JavaScope.resolvedTypes, new HashMap<>()),
                artifact.getNodeCount());
        final var methods = artifact.mapOfAllMethods();
        final var universalCHA = createUniversalCHA(dependencies, artifact);
        for (final var arc : artifact.getGraph().getExternalCalls().entrySet()) {

            for (final var dep : dependencies) {
                final var product = dep.product + "$" + dep.version;
                for (final var depTypeEntry : dep.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
                    final var depType = depTypeEntry.getValue();
                    final var depTypeUri = depTypeEntry.getKey();
                    final var call = new Call(arc.getKey(), arc.getValue(), methods.get(arc.getKey().get(1)));

                    if (call.isConstructor()) {
                        resolveClassInits(result, call, depTypeEntry, product, universalCHA);
                    } else {
                        for (final var cs : arc.getValue().entrySet()) {
                            final var callSite = (HashMap<String, Object>) cs.getValue();
                            final var receiverTypeUri = FastenURI.create((String) callSite.get("receiver"));

                            if (depTypeUri.equals(receiverTypeUri)) {
                                resolveIfDefined(result, call, depType, product);
                            }
                            if (callSite.get("type").equals("invokevirtual")
                                    || callSite.get("type").equals("invokeinterface")
                                    || callSite.get("type").equals("invokedynamic")) {

                                if (firstTypeExtendsSecond(depTypeUri, receiverTypeUri, universalCHA)) {
                                    resolveIfDefined(result, call, depType, product);
                                }
                            }
                        }
                    }
                }
            }
        }
        return buildRCG(artifact, result);
    }

    private static void resolveClassInits(final CGHA result, final Call call, final Map.Entry<FastenURI, JavaType> depType,
                                          final String product,
                                          final org.jgrapht.Graph<FastenURI, DefaultEdge> cha) {
        final var constructorType = getTypeURI(call.target.getUri());
        if (depType.getKey().equals(constructorType)
                || firstTypeExtendsSecond(constructorType, depType.getKey(), cha)) {
            final var callToSuper = new Call(Arrays.asList(call.indices.get(1), result.nodeCount), call.metadata,
                    new JavaNode(call.target.changeName(getTypeName(depType.getKey()), "%3Cinit%3E"),
                            call.target.getMetadata()));

            resolveIfDefined(result, callToSuper, depType.getValue(), product);
        }
    }

    private static String getTypeName(final FastenURI type) {
        return type.toString().substring(type.toString().lastIndexOf("/") + 1);
    }

    private static org.jgrapht.Graph<FastenURI, DefaultEdge> createUniversalCHA(
            final List<ExtendedRevisionJavaCallGraph> dependencies, final ExtendedRevisionJavaCallGraph artifact) {
        final var allPackages = new ArrayList<>(dependencies);
        allPackages.add(artifact);

        final var result = new DefaultDirectedGraph<FastenURI, DefaultEdge>(DefaultEdge.class);
        for (final var aPackage : allPackages) {
            for (final var type : aPackage.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
                if (!result.containsVertex(type.getKey())) {
                    result.addVertex(type.getKey());
                }
                addSuperTypes(result, type.getKey(), type.getValue().getSuperClasses());
                addSuperTypes(result, type.getKey(), type.getValue().getSuperInterfaces());
            }
        }
        return result;
    }

    private static void addSuperTypes(final DefaultDirectedGraph<FastenURI, DefaultEdge> result,
                                      final FastenURI sourceTypes,
                                      final List<FastenURI> targetTypes) {
        for (final var superClass : targetTypes) {
            if (!result.containsVertex(superClass)) {
                result.addVertex(superClass);
            }
            if (!result.containsEdge(sourceTypes, superClass)) {
                result.addEdge(superClass, sourceTypes);
            }
        }
    }

    private static void resolveIfDefined(final CGHA cgha, final Call call, final JavaType type, final String product) {
        type.getDefined(getSignature(call.target.getUri().getEntity()))
                .ifPresent(node -> resolve(cgha, new Call(call.indices, call.metadata, node.getValue()), product));
    }

    private static boolean firstTypeExtendsSecond(final FastenURI firstType, final FastenURI secondType,
                                                  final org.jgrapht.Graph<FastenURI, DefaultEdge> cha) {

        for (final var fastenURI : Graphs.predecessorListOf(cha, firstType)) {
            if (fastenURI.equals(secondType)) {
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

    private static int addToCHA(final Map<FastenURI, JavaType> cha, final JavaNode target, final int nodeCount,
                                String product) {
        final var keyType = new FastenJavaURI(getTypeURI(target.getUri()).toString()
                .replaceFirst("/", java.util.regex.Matcher.quoteReplacement("//" + product + "/")));
        final var type = cha.getOrDefault(keyType, new JavaType("notFound"));
        final var index = type.addMethod(new JavaNode(target.getUri(), target.getMetadata()), nodeCount);
        cha.put(keyType, type);
        return index;
    }

    private static String getSignature(final String Entity) {
        return Entity.substring(Entity.indexOf(".") + 1);
    }

    public static ExtendedRevisionJavaCallGraph mergeWithRA(final ExtendedRevisionJavaCallGraph artifact,
                                                        final List<ExtendedRevisionJavaCallGraph>
                                                                dependencies) {

        final var result = new CGHA(artifact.getGraph().getResolvedCalls(),
                artifact.getClassHierarchy().getOrDefault(JavaScope.resolvedTypes, new HashMap<>()),
                artifact.getNodeCount());
        final var methods = artifact.mapOfAllMethods();

        for (final var arc : artifact.getGraph().getExternalCalls().entrySet()) {
            final var call = new Call(arc.getKey(), arc.getValue(), methods.get(arc.getKey().get(1)));

            for (final var dep : dependencies) {
                final var product = dep.product + "$" + dep.version;
                for (final var typeEntry : dep.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
                    resolveIfDefined(result, call, typeEntry.getValue(), product);
                }
            }
        }
        return buildRCG(artifact, result);
    }

    private static ExtendedRevisionJavaCallGraph buildRCG(final ExtendedRevisionJavaCallGraph artifact,
                                                      final CGHA result) {
        final var cha = new HashMap<>(artifact.getClassHierarchy());
        cha.put(JavaScope.resolvedTypes, result.CHA);
        return ExtendedRevisionJavaCallGraph.extendedBuilder().forge(artifact.forge)
                .cgGenerator(artifact.getCgGenerator())
                .classHierarchy(cha)
                .product(artifact.product)
                .timestamp(artifact.timestamp)
                .version(artifact.version)
                .graph(new Graph(artifact.getGraph().getInternalCalls(), artifact.getGraph().getExternalCalls(),
                        result.graph))
                .nodeCount(result.nodeCount)
                .build();
    }


    private static FastenURI getTypeURI(final FastenURI callee) {
        return new FastenJavaURI("/" + callee.getRawNamespace() + "/"
                + callee.getRawEntity().substring(0, callee.getRawEntity().indexOf(".")));
    }


}


