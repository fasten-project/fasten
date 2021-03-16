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
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.FastenDefaultDirectedGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.Graph;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalMerger {

    private static final Logger logger = LoggerFactory.getLogger(LocalMerger.class);

    private final Map<String, List<String>> universalParents;
    private final Map<String, List<String>> universalChildren;
    private final Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary;
    private final List<ExtendedRevisionJavaCallGraph> dependencySet;
    private final BiMap<Long, String> allUris;

    public BiMap<Long, String> getAllUris() {
        return this.allUris;
    }

    /**
     * Creates instance of local merger.
     *
     * @param dependencySet all artifacts present in a resolution
     */
    public LocalMerger(final List<ExtendedRevisionJavaCallGraph> dependencySet) {

        final var UCH = createUniversalCHA(dependencySet);
        this.universalParents = UCH.getLeft();
        this.universalChildren = UCH.getRight();
        this.typeDictionary = createTypeDictionary(dependencySet);
        this.dependencySet = dependencySet;
        this.allUris = HashBiMap.create();
    }

    /**
     * Class with resolved calls and CHA.
     */
    public static class CGHA {

        private final ConcurrentMap<IntIntPair, Map<Object, Object>> graph;
        private final ConcurrentMap<String, JavaType> CHA;
        private AtomicInteger nodeCount;

        /**
         * Create CGHA object from an {@link ExtendedRevisionCallGraph}.
         *
         * @param toResolve call graph
         */
        public CGHA(final ExtendedRevisionJavaCallGraph toResolve) {
            this.graph = new ConcurrentHashMap<>(toResolve.getGraph().getResolvedCalls());
            var classHierarchy = HashBiMap.create(toResolve.getClassHierarchy()
                    .getOrDefault(JavaScope.resolvedTypes, HashBiMap.create()));
            this.CHA = new ConcurrentHashMap<>();
            classHierarchy.forEach(this.CHA::put);
            this.nodeCount = new AtomicInteger(toResolve.getNodeCount());
        }

    }

    /**
     * Single call containing source and target IDs, metadata and target node.
     */
    public static class Call {

        private final IntIntPair indices;
        private final Map<Object, Object> metadata;
        private final JavaNode target;

        /**
         * Create Call object from indices, metadata and target node.
         *
         * @param indices  source and target IDs
         * @param metadata call metadata
         * @param target   target node
         */
        public Call(final IntIntPair indices, Map<Object, Object> metadata,
                    final JavaNode target) {
            this.indices = indices;
            this.metadata = metadata;
            this.target = target;
        }

        public Call(final Call call, final JavaNode node) {
            this.indices = call.indices;
            this.metadata = call.metadata;
            this.target = node;
        }

        public Call(final Map.Entry<IntIntPair, Map<Object, Object>> arc,
                    final JavaNode target) {
            this.indices = arc.getKey();
            this.metadata = arc.getValue();
            this.target = target;
        }

        /**
         * Check if the call is to a constructor.
         *
         * @return true, if the constructor is called, false otherwise
         */
        public boolean isConstructor() {
            return target.getSignature().startsWith("<init>");
        }
    }

    /**
     * Create fully merged for the entire dependency set.
     *
     * @return merged call graph
     */
    public DirectedGraph mergeAllDeps() {
        final var result = new FastenDefaultDirectedGraph();
        for (final var dep : this.dependencySet) {
            addThisMergeToResult(result, mergeWithCHA(dep));
        }
        return result;
    }

    private void addThisMergeToResult(FastenDefaultDirectedGraph result,
                                      final ExtendedRevisionJavaCallGraph merged) {
        final var uris = merged.mapOfFullURIStrings();
        final var directedMerge = ExtendedRevisionJavaCallGraph.toLocalDirectedGraph(merged);
        long offset = result.nodes().longStream().max().orElse(0L) + 1;

        for (final var node : directedMerge.nodes()) {
            for (final var successor : directedMerge.successors(node)) {
                final var updatedNode = updateNode(node, offset, uris);
                final var updatedSuccessor = updateNode(successor, offset, uris);
                addEdge(result, updatedNode, updatedSuccessor);
            }
        }
    }

    private long updateNode(final long node, final long offset,
                            final BiMap<Integer, String> uris) {
        var uri = uris.get((int) node);

        if (allUris.containsValue(uri)) {
            return allUris.inverse().get(uri);
        }
        else {
            final var updatedNode = node + offset;
            this.allUris.put(updatedNode, uri);
            return updatedNode;
        }
    }

    private void addEdge(final FastenDefaultDirectedGraph result,
                         final long source, final long target) {
        result.addInternalNode(source);
        result.addInternalNode(target);
        result.addEdge(source, target);
    }

    /**
     * Merges a call graph with its dependencies using CHA algorithm.
     *
     * @return merged call graph
     */
    public ExtendedRevisionJavaCallGraph mergeWithCHA(final ExtendedRevisionJavaCallGraph artifact) {
        final var result = new CGHA(artifact);

        final var externalNodeIdToTypeMap = artifact.externalNodeIdToTypeMap();
        final var internalNodeIdToTypeMap = artifact.internalNodeIdToTypeMap();

        artifact.getGraph().getInternalCalls().entrySet().parallelStream().forEach(arc ->
            processArc(artifact, universalParents, universalChildren, typeDictionary, result,
                externalNodeIdToTypeMap, internalNodeIdToTypeMap, arc, true));
        artifact.getGraph().getExternalCalls().entrySet().parallelStream().forEach(arc ->
            processArc(artifact, universalParents, universalChildren, typeDictionary, result,
                externalNodeIdToTypeMap, internalNodeIdToTypeMap, arc, false));

        return buildRCG(artifact, result);
    }

    private void processArc(final ExtendedRevisionJavaCallGraph artifact,
                            final Map<String, List<String>> universalParents,
                            final Map<String, List<String>> universalChildren,
                            final Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                            final CGHA result,
                            final Int2ObjectMap<JavaType> externalNodeIdToTypeMap,
                            final Int2ObjectMap<JavaType> internalNodeIdToTypeMap,
                            final Map.Entry<IntIntPair, Map<Object, Object>> arc,
                            final boolean isInternal) {
        final var targetKey = arc.getKey().secondInt();
        final var sourceKey = arc.getKey().firstInt();

        boolean isCallBack = false;
        int nodeKey = targetKey;
        JavaType type =
            getType(externalNodeIdToTypeMap, internalNodeIdToTypeMap, isInternal, targetKey);

        if (externalNodeIdToTypeMap.containsKey(sourceKey)) {
            type = getType(externalNodeIdToTypeMap, internalNodeIdToTypeMap, isInternal, sourceKey);
            isCallBack = true;
            nodeKey = sourceKey;
        }
        resolve(universalParents, universalChildren, typeDictionary, result, arc,
            nodeKey, type, isCallBack);

    }

    private JavaType getType(
        final Int2ObjectMap<JavaType> externalNodeIdToTypeMap,
        final Int2ObjectMap<JavaType> internalNodeIdToTypeMap,
        final boolean isInternal,
        final int targetKey) {

        if (isInternal) {
            return internalNodeIdToTypeMap.get(targetKey);
        }else {
            return externalNodeIdToTypeMap.get(targetKey);
        }
    }

    private void resolve(final Map<String, List<String>> universalParents,
                         final Map<String, List<String>> universalChildren,
                         final Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                         final CGHA result,
                         final Map.Entry<IntIntPair, Map<Object, Object>> arc,
                         final int nodeKey,
                         final JavaType type,
                         final boolean isCallback) {

        var call = new Call(arc, type.getMethods().get(nodeKey));

        for (final var entry : arc.getValue().entrySet()) {
            final var callSite = (HashMap<String, Object>) entry.getValue();
            final var receiverTypeUris = getReceiver(callSite);

            if (callSite.get("type").toString().matches("invokevirtual|invokeinterface")) {

                resolveDynamics(universalChildren,universalParents, typeDictionary, result,
                    isCallback, call, receiverTypeUris);

            } else if (callSite.get("type").equals("invokespecial")) {

                resolveSpecials(result, call, typeDictionary, universalParents, type.getUri(),
                    isCallback);

            } else if (callSite.get("type").equals("invokedynamic")) {
                logger.warn("OPAL didn're rewrite the invokedynamic");
            } else {
                resolveReceiverType(typeDictionary, result, isCallback, call, receiverTypeUris);
            }
        }
    }

    private ArrayList<String> getReceiver(final HashMap<String, Object> callSite) {
        return new ArrayList<>(Arrays.asList(((String) callSite.get(
            "receiver")).replace("[","").replace("]","").split(",")));
    }

    private void resolveDynamics(final Map<String, List<String>> universalChildren,
                                 final Map<String, List<String>> universalParents,
                                 final Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                                 final CGHA result, final boolean isCallback, final Call call,
                                 final ArrayList<String> receiverTypeUris) {
        for (final var receiverTypeUri : receiverTypeUris) {
            final var types = universalChildren.getOrDefault(receiverTypeUri, new ArrayList<>());
            boolean foundTarget = false;
            if (!types.isEmpty()) {
                for (final var depTypeUri : types) {
                    foundTarget =
                        findTargets(typeDictionary, result, isCallback, call, foundTarget,
                            depTypeUri);
                }
            }
            if (!foundTarget) {
                for (String depTypeUri : universalParents.getOrDefault(receiverTypeUri, new ArrayList<>())) {
                    if(findTargets(typeDictionary, result, isCallback, call, foundTarget,
                        depTypeUri)){
                        break;
                    }
                }
            }
        }
    }

    private boolean findTargets(Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                                CGHA result, boolean isCallback,
                                Call call, boolean foundTarget,
                                String depTypeUri) {
        for (final var dep : typeDictionary
            .getOrDefault(depTypeUri, new ArrayList<>())) {

            foundTarget = resolveToDynamics(result, call, dep.getClassHierarchy().get(JavaScope.internalTypes)
                    .get(depTypeUri), dep.productVersion, depTypeUri, isCallback);
        }
        return foundTarget;
    }

    private boolean resolveToDynamics(final CGHA cgha, final Call call,
                                      final JavaType type,
                                      final String product, final String depTypeUri,
                                      boolean isCallback) {
        final var node = type.getDefinedMethods().get(call.target.getSignature());
        if (node != null) {
            addEdge(cgha, new Call(call, node), product, type, depTypeUri, isCallback);
            return true;
        }
        return false;
    }

    private void resolveReceiverType(final Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                                     final CGHA result, final boolean isCallback,
                                     final Call call, final List<String> receiverTypeUris) {
        for (final var receiverTypeUri : receiverTypeUris) {
            for (final var dep : typeDictionary
                .getOrDefault(receiverTypeUri, new ArrayList<>())) {

                resolveIfDefined(result, call, dep.getClassHierarchy()
                        .get(JavaScope.internalTypes)
                        .get(receiverTypeUri), dep.product + "$" + dep.version,
                    receiverTypeUri, isCallback);
            }
        }
    }

    private void resolveIfDefined(final CGHA cgha, final Call call,
                                  final JavaType type,
                                  final String product, final String depTypeUri,
                                  boolean isCallback) {
        final var node = type.getDefinedMethods().get(call.target.getSignature());
        if (node != null) {
            addEdge(cgha, new Call(call, node), product, type, depTypeUri, isCallback);
        }
    }

    /**
     * Resolves inits and constructors.
     * The <init> methods are called only when a new instance is created. At least one <init>
     * method will be invoked for each class along the inheritance path of the newly created
     * object, and multiple <init> methods could be invoked for any one class along that path.
     * This is how multiple <init> methods get invoked when an object is instantiated.
     * The virtual machine invokes an <init> method declared in the object's class.
     * That <init> method first invokes either another <init> method in the same class,
     * or an <init> method in its superclass. This process continues all the way up to Object.
     *
     * @param result          call graph with resolved calls
     * @param call            call information
     * @param constructorType type uri
     * @param isCallback      true, if the call is a constructor
     */
    private void resolveSpecials(final CGHA result, final Call call,
                                 final Map<String, List<ExtendedRevisionJavaCallGraph>> typeFinder,
                                 final Map<String, List<String>> universalParents,
                                 final String constructorType, final boolean isCallback) {
        final var typeList = universalParents.get(constructorType);
        if (typeList != null) {
            resolveReceiverType(typeFinder, result, isCallback, call,
                Collections.singletonList(typeList.get(0)));
        }
    }



    /**
     * Create a map with types as keys and a list of {@link ExtendedRevisionCallGraph} that
     * contain this type as values.
     *
     * @param dependencies dependencies including the artifact to resolve
     * @return type dictionary
     */
    private Map<String, List<ExtendedRevisionJavaCallGraph>> createTypeDictionary(
            final List<ExtendedRevisionJavaCallGraph> dependencies) {

        Map<String, List<ExtendedRevisionJavaCallGraph>> result = new HashMap<>();

        for (final var rcg : dependencies) {
            for (final var type : rcg
                    .getClassHierarchy().get(JavaScope.internalTypes)
                    .entrySet()) {
                result.merge(type.getKey(),
                        new ArrayList<>(Collections.singletonList(rcg)), (old, nieuw) -> {
                            old.addAll(nieuw);
                            return old;
                        });
            }
        }
        return result;
    }

    /**
     * Create a universal CHA for all dependencies including the artifact to resolve.
     *
     * @param dependencies dependencies including the artifact to resolve
     * @return universal CHA
     */
    private Pair<Map<String, List<String>>, Map<String, List<String>>> createUniversalCHA(
            final List<ExtendedRevisionJavaCallGraph> dependencies) {
        final var allPackages = new ArrayList<>(dependencies);

        final var result = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
        for (final var aPackage : allPackages) {
            for (final var type : aPackage.getClassHierarchy()
                    .get(JavaScope.internalTypes).entrySet()) {
                if (!result.containsVertex(type.getKey())) {
                    result.addVertex(type.getKey());
                }
                addSuperTypes(result, type.getKey(),
                        type.getValue().getSuperClasses());
                addSuperTypes(result, type.getKey(),
                        type.getValue().getSuperInterfaces());
            }
        }
        final Map<String, List<String>> universalParents = new HashMap<>();
        final Map<String, List<String>> universalChildren = new HashMap<>();
        for (final var type : result.vertexSet()) {

            final var children = new ArrayList<>(Collections.singletonList(type));
            children.addAll(getAllChildren(result, type));
            universalChildren.put(type, children);

            final var parents = new ArrayList<>(Collections.singletonList(type));
            parents.addAll(getAllParents(result, type));
            universalParents.put(type, parents);
        }
        return ImmutablePair.of(universalParents, universalChildren);
    }

    /**
     * Get all parents of a given type.
     *
     * @param graph universal CHA
     * @param type  type uri
     * @return list of types parents
     */
    private List<String> getAllParents(final DefaultDirectedGraph<String, DefaultEdge> graph,
                                       final String type) {
        final var children = Graphs.predecessorListOf(graph, type);
        final List<String> result = new ArrayList<>(children);
        for (final var child : children) {
            result.addAll(getAllParents(graph, child));
        }
        return result;
    }

    /**
     * Get all children of a given type.
     *
     * @param graph universal CHA
     * @param type  type uri
     * @return list of types children
     */
    private List<String> getAllChildren(final DefaultDirectedGraph<String, DefaultEdge> graph,
                                        final String type) {
        final var children = Graphs.successorListOf(graph, type);
        final List<String> result = new ArrayList<>(children);
        for (final var child : children) {
            result.addAll(getAllChildren(graph, child));
        }
        return result;
    }

    /**
     * Add super classes and interfaces to the universal CHA.
     *
     * @param result      universal CHA graph
     * @param sourceTypes source type
     * @param targetTypes list of target target types
     */
    private void addSuperTypes(final DefaultDirectedGraph<String, DefaultEdge> result,
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

    /**
     * Add new edge to the resolved call graph.
     *
     * @param cgha       call graph with resolved calls
     * @param call       new call
     * @param product    product name
     * @param depType    dependency {@link JavaType}
     * @param depTypeUri dependency type uri
     * @param isCallback true if the call is a callback
     */
    private void addEdge(final CGHA cgha, final Call call,
                                      final String product,
                                      final JavaType depType,
                                      final String depTypeUri, boolean isCallback) {
        final int addedKey = addToCHA(cgha, call.target, product, depType, depTypeUri);
        final IntIntPair edge = isCallback ? IntIntPair.of(addedKey, call.indices.secondInt())
             : IntIntPair.of(call.indices.firstInt(), addedKey);
        cgha.graph.put(edge, call.metadata);
    }

    /**
     * Add a new node to CHA.
     *
     * @param cgha       call graph with resolved calls
     * @param target     target Node
     * @param product    product name
     * @param depType    dependency {@link JavaType}
     * @param depTypeUri dependency type uri
     * @return id of a node in the CHA
     */
    private static int addToCHA(final CGHA cgha,
                                final JavaNode target,
                                final String product,
                                final JavaType depType,
                                final String depTypeUri) {
        final var cha = cgha.CHA;
        final var type = cha.computeIfAbsent("//" + product + depTypeUri, x -> 
                new JavaType(x, depType.getSourceFileName(), new Int2ObjectOpenHashMap<>(), new HashMap<>(),
                        depType.getSuperClasses(), depType.getSuperInterfaces(),
                        depType.getAccess(), depType.isFinal()));
        
        final int index;
        synchronized(type) {
            index = type.getMethodKey(target);
            if (index == -1) {
                final int nodeCount = cgha.nodeCount.getAndIncrement();
                type.addMethod(target, nodeCount);
                return nodeCount;
            }
        }
        
        return index;
    }

    /**
     * Build an {@link ExtendedRevisionCallGraph} from the original artifact and newly
     * resolved calls.
     *
     * @param artifact original artifact
     * @param result   resolved calls
     * @return full call graph
     */
    private static ExtendedRevisionJavaCallGraph buildRCG(final ExtendedRevisionJavaCallGraph artifact,
                                                          final CGHA result) {
        final var cha = artifact.getClassHierarchy();
        cha.put(JavaScope.resolvedTypes, result.CHA);
        return ExtendedRevisionJavaCallGraph.extendedBuilder().forge(artifact.forge)
            .cgGenerator(artifact.getCgGenerator())
            .classHierarchy(cha)
            .product(artifact.product)
            .timestamp(artifact.timestamp)
            .version(artifact.version)
            .graph(new Graph(artifact.getGraph().getInternalCalls(),
                artifact.getGraph().getExternalCalls(),
                result.graph))
            .nodeCount(result.nodeCount.get())
            .build();
    }
}