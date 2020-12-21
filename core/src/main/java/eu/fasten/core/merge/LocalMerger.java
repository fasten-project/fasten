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
import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedBuilderJava;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.Graph;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalMerger {

    private static final Logger logger = LoggerFactory.getLogger(LocalMerger.class);

    private final Map<String, Set<String>> universalParents;
    private final Map<String, Set<String>> universalChildren;
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

        private final Map<List<Integer>, Map<Object, Object>> graph;
        private final BiMap<String, JavaType> CHA;
        private int nodeCount;

        /**
         * Create CGHA object from an {@link ExtendedRevisionCallGraph}.
         *
         * @param toResolve call graph
         */
        public CGHA(final ExtendedRevisionJavaCallGraph toResolve) {
            this.graph = toResolve.getGraph().getResolvedCalls();
            var classHierarchy = HashBiMap.create(toResolve.getClassHierarchy()
                    .getOrDefault(JavaScope.resolvedTypes, HashBiMap.create()));
            this.CHA = HashBiMap.create();
            classHierarchy.forEach((key, value) -> this.CHA.put(key.toString(), value));
            this.nodeCount = toResolve.getNodeCount();
        }

        /**
         * Converts the CHA BiMap to a HashMap.
         *
         * @return CHA hashmap
         */
        private HashMap<FastenURI, JavaType> toHashMap() {
            var result = new HashMap<FastenURI, JavaType>();
            this.CHA.forEach((key, value) -> result.put(FastenURI.create(key), value));
            return result;
        }
    }

    /**
     * Single call containing source and target IDs, metadata and target node.
     */
    public static class Call {

        private final List<Integer> indices;
        private final Map<Object, Object> metadata;
        private final JavaNode target;

        /**
         * Create Call object from indices, metadata and target node.
         *
         * @param indices  source and target IDs
         * @param metadata call metadata
         * @param target   target node
         */
        public Call(final List<Integer> indices, Map<Object, Object> metadata,
                    final JavaNode target) {
            this.indices = indices;
            this.metadata = metadata;
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
        final var result = new ArrayImmutableDirectedGraph.Builder();
        var offset = 0l;
        for (final var dep : this.dependencySet) {
            final var merged = mergeWithCHA(dep);
            final var directedMerge = ExtendedRevisionJavaCallGraph.toLocalDirectedGraph(merged);
            addThisMergeToResult(result, directedMerge, merged.mapOfFullURIStrings(), offset);
            offset = offset + directedMerge.nodes().size();
        }
        return result.build();
    }

    private void addThisMergeToResult(ArrayImmutableDirectedGraph.Builder result,
                                      final DirectedGraph directedMerge,
                                      final BiMap<Integer, String> uris,
                                      final Long offset) {

        for (final var node : directedMerge.nodes()) {
            for (final var successor : directedMerge.successors(node)) {
                addEdge(result, directedMerge, node + offset, successor + offset);
            }
        }
        for (final var node : uris.entrySet()) {
            this.allUris.put(node.getKey() + offset, node.getValue());
        }
    }

    private void addEdge(final ArrayImmutableDirectedGraph.Builder result,
                         final DirectedGraph callGraphData,
                         final Long source, final Long target) {

        try {
            if (new HashSet<>(callGraphData.nodes()).contains(source)
                && callGraphData.isInternal(source)) {
                result.addInternalNode(source);
            } else {
                result.addExternalNode(source);
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            if (new HashSet<>(callGraphData.nodes()).contains(target)
                && callGraphData.isInternal(target)) {
                result.addInternalNode(target);
            } else {
                result.addExternalNode(target);
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            result.addArc(source, target);
        } catch (IllegalArgumentException ignored) {
        }
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

        final var externalTypeToId = HashBiMap.create(artifact.getClassHierarchy()
                .get(JavaScope.externalTypes)).inverse();
        final var internalTypeToId = HashBiMap.create(artifact.getClassHierarchy()
                .get(JavaScope.internalTypes)).inverse();

        artifact.getGraph().getExternalCalls().entrySet().parallelStream().forEach(arc -> {
            final var targetKey = arc.getKey().get(1);
            final var sourceKey = arc.getKey().get(0);

            if (externalNodeIdToTypeMap.containsKey(targetKey)) {
                final var type = externalNodeIdToTypeMap.get(targetKey);
                resolve(result, arc, targetKey, type, externalTypeToId.get(type).toString(), false);

            } else if (externalNodeIdToTypeMap.containsKey(sourceKey)) {
                final var type = externalNodeIdToTypeMap.get(sourceKey);
                resolve(result, arc, sourceKey, type, externalTypeToId.get(type).toString(), true);
            } else {
                final var type = internalNodeIdToTypeMap.get(sourceKey);
                final var typeUri = internalTypeToId.get(type).toString();
                final var call = new Call(arc.getKey(), arc.getValue(),
                        type.getMethods().get(sourceKey));
                resolveInitsAndConstructors(result, call, typeUri, false);
            }
        });
        return buildRCG(artifact, result);
    }

    /**
     * Resolve an external call.
     *
     * @param result     call graph with resolved calls
     * @param arc        source, target, and metadata
     * @param nodeKey    node id
     * @param type       type information
     * @param typeUri    type uri
     * @param isCallback true, if the call is a callback
     */
    private void resolve(final CGHA result,
                         final Map.Entry<List<Integer>, Map<Object, Object>> arc,
                         final Integer nodeKey,
                         final JavaType type,
                         final String typeUri,
                         final boolean isCallback) {

        var call = new Call(arc.getKey(), arc.getValue(), type.getMethods().get(nodeKey));
        if (call.isConstructor()) {
            resolveInitsAndConstructors(result, call, typeUri, isCallback);
        }

        for (final var entry : arc.getValue().entrySet()) {
            final var callSite = (HashMap<String, Object>) entry.getValue();
            final var receiverTypeUri = (String) callSite.get("receiver");
            if (callSite.get("type").equals("invokevirtual")
                    || callSite.get("type").equals("invokeinterface")) {
                final var types = universalChildren.get(receiverTypeUri);
                if (types != null) {
                    for (final var depTypeUri : types) {
                        for (final var dep : typeDictionary
                                .getOrDefault(depTypeUri, new ArrayList<>())) {

                            resolveToDynamics(result, call, dep.getClassHierarchy()
                                            .get(JavaScope.internalTypes)
                                            .get(FastenURI.create(depTypeUri)),
                                    dep.product + "$" + dep.version, depTypeUri, isCallback);
                        }
                    }
                }
            } else if (callSite.get("type").equals("invokespecial")) {
                resolveInitsAndConstructors(result, call, typeUri, isCallback);

            } else if (callSite.get("type").equals("invokedynamic")) {
                logger.warn("OPAL didn't rewrite the invokedynamic");
            } else {
                for (final var dep : typeDictionary
                        .getOrDefault(receiverTypeUri, new ArrayList<>())) {
                    resolveIfDefined(result, call, dep.getClassHierarchy()
                                    .get(JavaScope.internalTypes)
                                    .get(FastenURI.create(receiverTypeUri)),
                            dep.product + "$" + dep.version, receiverTypeUri, isCallback);
                }
            }
        }
    }

    /**
     * Resolve dynamic call.
     *
     * @param cgha       call graph with resolved calls
     * @param call       new call
     * @param product    product name
     * @param type       dependency {@link JavaType}
     * @param depTypeUri dependency type uri
     * @param isCallback true if the call is a callback
     */
    private void resolveToDynamics(final CGHA cgha, final Call call,
                                   final JavaType type,
                                   final String product, final String depTypeUri,
                                   boolean isCallback) {
        final var node = type.getDefined(call.target.getSignature());
        node.ifPresent(integerJavaNodeEntry ->
                addEdge(cgha, new Call(call.indices, call.metadata, integerJavaNodeEntry.getValue()),
                        product, type, depTypeUri, isCallback));
    }

    /**
     * Resolve defined call.
     *
     * @param cgha       call graph with resolved calls
     * @param call       new call
     * @param product    product name
     * @param type       dependency {@link JavaType}
     * @param depTypeUri dependency type uri
     * @param isCallback true if the call is a callback
     */
    private void resolveIfDefined(final CGHA cgha, final Call call,
                                  final JavaType type,
                                  final String product, final String depTypeUri,
                                  boolean isCallback) {
        final var node = type.getDefined(call.target.getSignature());
        node.ifPresent(integerJavaNodeEntry -> addEdge(cgha, new Call(call.indices, call.metadata, integerJavaNodeEntry.getValue()),
                product, type, depTypeUri, isCallback));
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
    private void resolveInitsAndConstructors(final CGHA result, final Call call,
                                             final String constructorType, boolean isCallback) {

        final var typeList = universalParents.get(constructorType);
        if (typeList != null) {
            for (final var superTypeUri : typeList) {
                for (final var dep : typeDictionary.getOrDefault(superTypeUri, new ArrayList<>())) {

                    resolveIfDefined(result, call, dep.getClassHierarchy()
                                    .get(JavaScope.internalTypes)
                                    .get(FastenURI.create(superTypeUri)),
                            dep.product + "$" + dep.version, superTypeUri, isCallback);

                    final var callToInit =
                            new Call(Arrays.asList(call.indices.get(0), result.nodeCount),
                                    call.metadata, new JavaNode(
                                    FastenURI.create("/" + call.target.getUri().getNamespace()
                                            + "/" + call.target.getClassName()
                                            + ".%3Cclinit%3E()" + FastenJavaURI.pctEncodeArg("/java.lang/VoidType")),
                                    call.target.getMetadata()));

                    resolveIfDefined(result, callToInit, dep.getClassHierarchy()
                                    .get(JavaScope.internalTypes)
                                    .get(FastenURI.create(superTypeUri)),
                            dep.product + "$" + dep.version, superTypeUri, isCallback);
                }
            }
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
                result.merge(type.getKey().toString(),
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
    private Pair<Map<String, Set<String>>, Map<String, Set<String>>> createUniversalCHA(
            final List<ExtendedRevisionJavaCallGraph> dependencies) {
        final var allPackages = new ArrayList<>(dependencies);

        final var result = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
        for (final var aPackage : allPackages) {
            for (final var type : aPackage.getClassHierarchy()
                    .get(JavaScope.internalTypes).entrySet()) {
                if (!result.containsVertex(type.getKey().toString())) {
                    result.addVertex(type.getKey().toString());
                }
                addSuperTypes(result, type.getKey().toString(),
                        type.getValue().getSuperClasses());
                addSuperTypes(result, type.getKey().toString(),
                        type.getValue().getSuperInterfaces());
            }
        }
        final Map<String, Set<String>> universalParents = new HashMap<>();
        final Map<String, Set<String>> universalChildren = new HashMap<>();
        for (final var type : result.vertexSet()) {

            final var children = new HashSet<>(Collections.singletonList(type));
            children.addAll(getAllChildren(result, type));
            universalChildren.put(type, children);

            final var parents = new HashSet<>(Collections.singletonList(type));
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
    private synchronized void addEdge(final CGHA cgha, final Call call,
                                      final String product,
                                      final JavaType depType,
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
        final var keyType = "//" + product + depTypeUri;
        final var type = cgha.CHA.getOrDefault(keyType,
                new JavaType(depType.getSourceFileName(), HashBiMap.create(),
                        depType.getSuperClasses(), depType.getSuperInterfaces(),
                        depType.getAccess(), depType.isFinal()));
        final var index = type.addMethod(
                new JavaNode(target.getUri(),
                        target.getMetadata()),
                cgha.nodeCount);
        cgha.CHA.put(keyType, type);
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
        final var cha = new HashMap<>(artifact.getClassHierarchy());
        cha.put(JavaScope.resolvedTypes, result.toHashMap());
        return new ExtendedBuilderJava().forge(artifact.forge)
                .cgGenerator(artifact.getCgGenerator())
                .classHierarchy(cha)
                .product(artifact.product)
                .timestamp(artifact.timestamp)
                .version(artifact.version)
                .graph(new Graph(artifact.getGraph().getInternalCalls(),
                        artifact.getGraph().getExternalCalls(),
                        result.graph))
                .nodeCount(result.nodeCount)
                .build();
    }
}