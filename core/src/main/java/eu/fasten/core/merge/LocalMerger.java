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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LocalMerger {

    private static final Logger logger = LoggerFactory.getLogger(LocalMerger.class);

    public ExtendedRevisionCallGraph mergeCallGraph(final ExtendedRevisionCallGraph artifact,
                                                    final List<ExtendedRevisionCallGraph>
                                                            dependencies,
                                                    final String algorithm) {
        if (algorithm.equals("CHA")) {
            return mergeWithCHA(artifact, dependencies);
        } else {
            logger.warn("{} algorithm is not supported for merge, please enter RA or CHA.",
                    algorithm);
            return null;
        }

    }

    private ExtendedRevisionCallGraph mergeWithCHA(ExtendedRevisionCallGraph artifact,
                                                   List<ExtendedRevisionCallGraph> dependencies) {
        dependencies.add(artifact);
        return mergeWithCHA(artifact, createUniversalCHA(dependencies),
                createTypeDictionary(dependencies));
    }

    public static Map<String, List<ExtendedRevisionCallGraph>> createTypeDictionary(
            final List<ExtendedRevisionCallGraph> rcgs) {

        Map<String, List<ExtendedRevisionCallGraph>> result = new HashMap<>();

        for (final var rcg : rcgs) {
            for (final var type : rcg
                    .getClassHierarchy().get(ExtendedRevisionCallGraph.Scope.internalTypes)
                    .entrySet()) {
                result.merge(type.getKey().toString(), new ArrayList<>(Collections.singletonList(rcg)),
                        (old, nieuw) -> {
                            old.addAll(nieuw);
                            return old;
                        });
            }
        }
        return result;
    }


    public static class CGHA {
        final Map<List<Integer>, Map<Object, Object>> graph;
        final BiMap<String, ExtendedRevisionCallGraph.Type> CHA;
        int nodeCount;

        public CGHA(final Map<List<Integer>, Map<Object, Object>> graph,
                    final BiMap<FastenURI, ExtendedRevisionCallGraph.Type> CHA,
                    final int nodeCount) {
            this.graph = graph;
            this.CHA = HashBiMap.create();
            CHA.forEach((key, value) -> this.CHA.put(key.toString(), value));
            this.nodeCount = nodeCount;
        }

        public CGHA(final ExtendedRevisionCallGraph toResolve) {
            this.graph = toResolve.getGraph().getResolvedCalls();
            var classHierarchy = HashBiMap.create(toResolve.getClassHierarchy()
                    .getOrDefault(ExtendedRevisionCallGraph.Scope.resolvedTypes, HashBiMap.create()));
            this.CHA = HashBiMap.create();
            classHierarchy.forEach((key, value) -> this.CHA.put(key.toString(), value));
            this.nodeCount = toResolve.getNodeCount();
        }

        private HashMap<FastenURI, ExtendedRevisionCallGraph.Type> toHashMap() {
            var result = new HashMap<FastenURI, ExtendedRevisionCallGraph.Type>();
            this.CHA.forEach((key, value) -> result.put(FastenURI.create(key), value));
            return result;
        }
    }

    public static class Call {
        final List<Integer> indices;
        final Map<Object, Object> metadata;
        final ExtendedRevisionCallGraph.Node target;

        public Call(final List<Integer> indices, Map<Object, Object> metadata,
                    final ExtendedRevisionCallGraph.Node target) {
            this.indices = indices;
            this.metadata = metadata;
            this.target = target;
        }

        public Call(final Map.Entry<List<Integer>, Map<Object, Object>> arc,
                    final ExtendedRevisionCallGraph.Node target) {
            this.indices = arc.getKey();
            this.metadata = arc.getValue();
            this.target = target;
        }

        public Call(final Call call, final ExtendedRevisionCallGraph.Node node) {
            this.indices = call.indices;
            this.metadata = call.metadata;
            this.target = node;
        }

        public boolean isConstructor() {
            return getSignature(target.getEntity()).startsWith("<init>")
                    || getSignature(target.getEntity()).startsWith("%3Cinit%3E");
        }

    }

    public ExtendedRevisionCallGraph mergeWithCHA(
            final ExtendedRevisionCallGraph toResolve,
            final org.jgrapht.Graph<String, DefaultEdge> universalCHA,
            final Map<String, List<ExtendedRevisionCallGraph>> typeDictionary) {

        final var result = new CGHA(toResolve);
        final var classHierarchy = toResolve.getClassHierarchy();

        final var externalNodeIdToTypeMap = new HashMap<Integer, ExtendedRevisionCallGraph.Type>();
        final var internalNodeIdToTypeMap = new HashMap<Integer, ExtendedRevisionCallGraph.Type>();

        classHierarchy.get(ExtendedRevisionCallGraph.Scope.externalTypes).values().parallelStream().forEach(type -> {
            for (final var key : type.getMethods().keySet()) {
                synchronized (externalNodeIdToTypeMap) {
                    externalNodeIdToTypeMap.put(key, type);
                }
            }
        });
        classHierarchy.get(ExtendedRevisionCallGraph.Scope.internalTypes).values().parallelStream().forEach(type -> {
            for (final var key : type.getMethods().keySet()) {
                synchronized (internalNodeIdToTypeMap) {
                    internalNodeIdToTypeMap.put(key, type);
                }
            }
        });

        final var externalTypeToId = HashBiMap.create(toResolve.getClassHierarchy().get(ExtendedRevisionCallGraph.Scope.externalTypes));
        final var internalTypeToId = HashBiMap.create(toResolve.getClassHierarchy().get(ExtendedRevisionCallGraph.Scope.internalTypes));


        toResolve.getGraph().getExternalCalls().entrySet().forEach(arc -> {
            final var targetKey = arc.getKey().get(1);
            final var sourceKey = arc.getKey().get(0);

            if (externalNodeIdToTypeMap.containsKey(targetKey)) {
                final var type = externalNodeIdToTypeMap.get(targetKey);
                resolve(universalCHA, typeDictionary, result, arc, targetKey, type,
                        externalTypeToId.inverse().get(type).toString(), false);

            } else if (externalNodeIdToTypeMap.containsKey(sourceKey)) {
                final var type = externalNodeIdToTypeMap.get(sourceKey);
                resolve(universalCHA, typeDictionary, result, arc, sourceKey, type,
                        externalTypeToId.inverse().get(type).toString(), true);
            } else {
                final var type = internalNodeIdToTypeMap.get(sourceKey);
                final var typeUri = internalTypeToId.inverse().get(type).toString();
                final var call = new Call(arc, type.getMethods().get(sourceKey));
                resolveClassInits(result, call, typeDictionary, universalCHA, typeUri, false);
            }
        });

        return buildRCG(toResolve, result);
    }


    private void resolve(final Graph<String, DefaultEdge> universalCHA,
                         final Map<String, List<ExtendedRevisionCallGraph>> typeDictionary,
                         final CGHA result,
                         final Map.Entry<List<Integer>, Map<Object, Object>> arc,
                         final Integer nodeKey,
                         final ExtendedRevisionCallGraph.Type type,
                         final String typeUri,
                         final boolean isCallback) {

        var call = new Call(arc, type.getMethods().get(nodeKey));
        if (call.isConstructor()) {
            resolveClassInits(result, call, typeDictionary, universalCHA, typeUri, isCallback);
        }

        for (final var entry : arc.getValue().entrySet()) {
            final var callSite = (HashMap<String, Object>) entry.getValue();
            final var receiverTypeUri = (String) callSite.get("receiver");
            if (callSite.get("type").equals("invokevirtual")
                    || callSite.get("type").equals("invokeinterface")) {
                final var types =
                        new ArrayList<>(Collections.singletonList(receiverTypeUri));
                if (universalCHA.containsVertex(receiverTypeUri)) {
                    types.addAll(Graphs.successorListOf(universalCHA, receiverTypeUri));
                }

                for (final var depTypeUri : types) {
                    for (final var dep : typeDictionary
                            .getOrDefault(depTypeUri, new ArrayList<>())) {

                        resolveToDynamics(result, call, dep.getClassHierarchy()
                                .get(ExtendedRevisionCallGraph.Scope.internalTypes)
                                .get(FastenURI.create(depTypeUri)), dep.product + "$" + dep.version, depTypeUri, isCallback);
                    }
                }
            } else if (callSite.get("type").equals("invokespecial")) {

                resolveClassInits(result, call, typeDictionary, universalCHA, typeUri, isCallback);

            } else if (callSite.get("type").equals("invokedynamic")) {
                logger.warn("OPAL didn're rewrite the invokedynamic");
            } else {
                for (final var dep : typeDictionary
                        .getOrDefault(receiverTypeUri, new ArrayList<>())) {
                    resolveIfDefined(result, call, dep.getClassHierarchy()
                                    .get(ExtendedRevisionCallGraph.Scope.internalTypes)
                                    .get(FastenURI.create(receiverTypeUri)), dep.product + "$" + dep.version,
                            receiverTypeUri, isCallback);
                }
            }
        }
    }

    private void resolveToDynamics(final CGHA cgha, final Call call,
                                   final ExtendedRevisionCallGraph.Type type,
                                   final String product, final String depTypeUri,
                                   boolean isCallback) {
        final var node = type.getDefined(getSignature(call.target.getEntity()));
        node.ifPresent(integerNodeEntry -> addEdge(cgha, new Call(call, integerNodeEntry.getValue()), product, type, depTypeUri, isCallback));
    }


    private void resolveClassInits(final CGHA result, final Call call,
                                   final Map<String, List<ExtendedRevisionCallGraph>> typeFinder,
                                   final Graph<String, DefaultEdge> universalCHA,
                                   final String constructorType, boolean isCallback) {
        //The <init> methods are called only when a new instance is created. At least one <init> method will be invoked for each class along the inheritance path of the newly created object, and multiple <init> methods could be invoked for any one class along that path.
        // This is how multiple <init> methods get invoked when an object is instantiated. The virtual machine invokes an <init> method declared in the object's class. That <init> method first invokes either another <init> method in the same class, or an <init> method in its superclass. This process continues all the way up to Object.
        final var typeList = new ArrayList<>(Collections.singletonList(constructorType));
        if (universalCHA.containsVertex(constructorType)) {
            typeList.addAll(Graphs.predecessorListOf(universalCHA, constructorType));
        }
        for (final var superTypeUri : typeList) {
            for (final var dep : typeFinder.getOrDefault(superTypeUri, new ArrayList<>())) {
                final var callToSuper =
                        new Call(Arrays.asList(call.indices.get(0), result.nodeCount), call.metadata,
                                new ExtendedRevisionCallGraph.Node(
                                        call.target.changeName(call.target.getClassName(), "<clinit>"),
                                        call.target.getMetadata()));

                resolveIfDefined(result, callToSuper, dep.getClassHierarchy()
                        .get(ExtendedRevisionCallGraph.Scope.internalTypes)
                        .get(FastenURI.create(superTypeUri)), dep.product + "$" + dep.version, superTypeUri, isCallback);
            }
        }
    }

    public static org.jgrapht.Graph<String, DefaultEdge> createUniversalCHA(
            final List<ExtendedRevisionCallGraph> dependencies) {
        final var allPackages = new ArrayList<>(dependencies);

        final var result = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
        for (final var aPackage : allPackages) {
            for (final var type : aPackage.getClassHierarchy()
                    .get(ExtendedRevisionCallGraph.Scope.internalTypes).entrySet()) {
                if (!result.containsVertex(type.getKey().toString())) {
                    result.addVertex(type.getKey().toString());
                }
                addSuperTypes(result, type.getKey().toString(), type.getValue().getSuperClasses());
                addSuperTypes(result, type.getKey().toString(), type.getValue().getSuperInterfaces());
            }
        }
        return result;
    }

    private static void addSuperTypes(final DefaultDirectedGraph<String, DefaultEdge> result,
                                      final String sourceTypes,
                                      final List<FastenURI> targetTypes) {
        for (final var superClass : targetTypes) {
            if (!result.containsVertex(superClass.toString())) {
                result.addVertex(superClass.toString());
            }
            if (!result.containsEdge(sourceTypes, superClass.toString())) {
                result.addEdge(superClass.toString(), sourceTypes);
            }
        }
    }

    private void resolveIfDefined(final CGHA cgha, final Call call,
                                  final ExtendedRevisionCallGraph.Type type,
                                  final String product, final String depTypeUri, boolean isCallback) {
        final var node = type.getDefined(getSignature(call.target.getEntity()));
        node.ifPresent(integerNodeEntry -> addEdge(cgha, new Call(call, integerNodeEntry.getValue()), product, type, depTypeUri, isCallback));
    }

    private synchronized void addEdge(final CGHA cgha, final Call call,
                                      final String product,
                                      final ExtendedRevisionCallGraph.Type depType,
                                      final String depTypeUri, boolean isCallback) {

        final int addedKey = addToCHA(cgha, call.target, product, depType, depTypeUri);
        if (addedKey == cgha.nodeCount) {
            cgha.nodeCount++;
        }
        if (isCallback) {
            cgha.graph.put(Arrays.asList(addedKey, call.indices.get(1)), call.metadata);
        } else {
            cgha.graph.put(Arrays.asList(call.indices.get(0), addedKey), call.metadata);
        }
    }

    private static int addToCHA(final CGHA cgha,
                                final ExtendedRevisionCallGraph.Node target,
                                final String product,
                                final ExtendedRevisionCallGraph.Type depType,
                                final String depTypeUri) {
        final var keyType = getVisionedUri(depTypeUri, product);
        final var type = cgha.CHA.getOrDefault(keyType,
                new ExtendedRevisionCallGraph.Type(depType.getSourceFileName(), HashBiMap.create(),
                        depType.getSuperClasses(), depType.getSuperInterfaces(), depType.getAccess(),
                        depType.isFinal()));
        final var index = type.addMethod(new ExtendedRevisionCallGraph.Node(target.getUri(),
                target.getMetadata()), cgha.nodeCount);
        cgha.CHA.put(keyType, type);
        return index;
    }

    private static String getVisionedUri(final String uri,
                                         final String product) {
        return uri.replaceFirst("/",
                java.util.regex.Matcher.quoteReplacement("//" + product + "/"));
    }

    private static String getSignature(final String Entity) {
        return Entity.substring(Entity.indexOf(".") + 1);
    }

    private static ExtendedRevisionCallGraph buildRCG(final ExtendedRevisionCallGraph artifact,
                                                      final CGHA result) {
        final var cha = new HashMap<>(artifact.getClassHierarchy());
        cha.put(ExtendedRevisionCallGraph.Scope.resolvedTypes, result.toHashMap());
        return ExtendedRevisionCallGraph.extendedBuilder().forge(artifact.forge)
                .cgGenerator(artifact.getCgGenerator())
                .classHierarchy(cha)
                .product(artifact.product)
                .timestamp(artifact.timestamp)
                .version(artifact.version)
                .graph(new ExtendedRevisionCallGraph.Graph(artifact.getGraph().getInternalCalls(),
                        artifact.getGraph().getExternalCalls(),
                        result.graph))
                .nodeCount(result.nodeCount)
                .build();
    }
}