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
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalMerger {

    private static final Logger logger = LoggerFactory.getLogger(LocalMerger.class);

    private final ExtendedRevisionCallGraph artifact;
    private final List<ExtendedRevisionCallGraph> dependencies;

    /**
     * Creates instance of local merger.
     *
     * @param artifact     artifact to resolve
     * @param dependencies dependencies of the artifact
     */
    public LocalMerger(final ExtendedRevisionCallGraph artifact,
                       final List<ExtendedRevisionCallGraph> dependencies) {
        this.artifact = artifact;
        this.dependencies = dependencies;
        this.dependencies.add(artifact);
    }

    public static class CGHA {

        private final Map<List<Integer>, Map<Object, Object>> graph;
        private final BiMap<String, ExtendedRevisionCallGraph.Type> CHA;
        private int nodeCount;

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

        private final List<Integer> indices;
        private final Map<Object, Object> metadata;
        private final ExtendedRevisionCallGraph.Node target;

        public Call(final List<Integer> indices, Map<Object, Object> metadata,
                    final ExtendedRevisionCallGraph.Node target) {
            this.indices = indices;
            this.metadata = metadata;
            this.target = target;
        }

        public boolean isConstructor() {
            return target.getSignature().startsWith("<init>");
        }
    }

    /**
     * Merges a call graph with its dependencies using CHA algorithm.
     *
     * @return merged call graph
     */
    public ExtendedRevisionCallGraph mergeWithCHA() {
        final var UCH = createUniversalCHA();
        final var universalParents = UCH.getLeft();
        final var universalChildren = UCH.getRight();
        final var typeDictionary = createTypeDictionary();

        final var result = new CGHA(artifact);
        final var externalNodeIdToTypeMap = artifact.externalNodeIdToTypeMap();
        final var internalNodeIdToTypeMap = artifact.internalNodeIdToTypeMap();

        final var externalTypeToId = HashBiMap.create(artifact.getClassHierarchy()
                .get(ExtendedRevisionCallGraph.Scope.externalTypes)).inverse();
        final var internalTypeToId = HashBiMap.create(artifact.getClassHierarchy()
                .get(ExtendedRevisionCallGraph.Scope.internalTypes)).inverse();

        artifact.getGraph().getExternalCalls().entrySet().parallelStream().forEach(arc -> {
            final var targetKey = arc.getKey().get(1);
            final var sourceKey = arc.getKey().get(0);

            if (externalNodeIdToTypeMap.containsKey(targetKey)) {
                final var type = externalNodeIdToTypeMap.get(targetKey);
                resolve(universalParents, universalChildren, typeDictionary, result, arc,
                        targetKey, type, externalTypeToId.get(type).toString(), false);

            } else if (externalNodeIdToTypeMap.containsKey(sourceKey)) {
                final var type = externalNodeIdToTypeMap.get(sourceKey);
                resolve(universalParents, universalChildren, typeDictionary, result, arc,
                        sourceKey, type, externalTypeToId.get(type).toString(), true);
            } else {
                final var type = internalNodeIdToTypeMap.get(sourceKey);
                final var typeUri = internalTypeToId.get(type).toString();
                final var call = new Call(arc.getKey(), arc.getValue(),
                        type.getMethods().get(sourceKey));
                resolveInitsAndConstructors(result, call, typeDictionary, universalParents, typeUri,
                        false);
            }
        });
        return buildRCG(artifact, result);
    }

    /**
     * Resolve an external call.
     *
     * @param universalParents  universal CHA with parents nodes
     * @param universalChildren universal CHA with child nodes
     * @param typeDictionary    type dictionary
     * @param result            call graph with resolved calls
     * @param arc               source, target, and metadata
     * @param nodeKey           node id
     * @param type              type information
     * @param typeUri           type uri
     * @param isCallback        true, if the call is a callback
     */
    private void resolve(final Map<String, List<String>> universalParents,
                         final Map<String, List<String>> universalChildren,
                         final Map<String, List<ExtendedRevisionCallGraph>> typeDictionary,
                         final CGHA result,
                         final Map.Entry<List<Integer>, Map<Object, Object>> arc,
                         final Integer nodeKey,
                         final ExtendedRevisionCallGraph.Type type,
                         final String typeUri,
                         final boolean isCallback) {

        var call = new Call(arc.getKey(), arc.getValue(), type.getMethods().get(nodeKey));
        if (call.isConstructor()) {
            resolveInitsAndConstructors(result, call, typeDictionary, universalParents, typeUri,
                    isCallback);
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
                                            .get(ExtendedRevisionCallGraph.Scope.internalTypes)
                                            .get(FastenURI.create(depTypeUri)),
                                    dep.product + "$" + dep.version, depTypeUri, isCallback);
                        }
                    }
                }
            } else if (callSite.get("type").equals("invokespecial")) {
                resolveInitsAndConstructors(result, call, typeDictionary, universalParents, typeUri,
                        isCallback);

            } else if (callSite.get("type").equals("invokedynamic")) {
                logger.warn("OPAL didn't rewrite the invokedynamic");
            } else {
                for (final var dep : typeDictionary
                        .getOrDefault(receiverTypeUri, new ArrayList<>())) {
                    resolveIfDefined(result, call, dep.getClassHierarchy()
                                    .get(ExtendedRevisionCallGraph.Scope.internalTypes)
                                    .get(FastenURI.create(receiverTypeUri)),
                            dep.product + "$" + dep.version, receiverTypeUri, isCallback);
                }
            }
        }
    }

    private Collection<String> getChildernToLeaf(final Graph<String, DefaultEdge> universalCHA,
                                                 final List<String> types) {

        final var result = new ArrayList<>(types);
        for (final var type : types) {
            result.addAll(getParentsUpToRoot(universalCHA, Graphs.successorListOf(universalCHA,
                    type)));
        }
        return result;
    }

    private void resolveToDynamics(final CGHA cgha, final Call call,
                                   final ExtendedRevisionCallGraph.Type type,
                                   final String product, final String depTypeUri,
                                   boolean isCallback) {
        //TODO maybe we need to dereletivize the uris before putting them into signatures
        final var node = type.getDefinedMethods().get(call.target.getSignature());
        if (node != null) {
            addEdge(cgha, new Call(call.indices, call.metadata, node),
                    product, type, depTypeUri, isCallback);
        }
    }

    /**
     * Resolves inits and constructors.
     *
     * @param result           call graph with resolved calls
     * @param call             call information
     * @param typeFinder       type dictionary
     * @param universalParents universal CHA with parent nodes
     * @param constructorType  type uri
     * @param isCallback       true, if the call is a constructor
     */
    private void resolveInitsAndConstructors(final CGHA result, final Call call,
                                             final Map<String, List<ExtendedRevisionCallGraph>> typeFinder,
                                             final Map<String, List<String>> universalParents,
                                             final String constructorType, boolean isCallback) {
        // The <init> methods are called only when a new instance is created. At least one <init>
        // method will be invoked for each class along the inheritance path of the newly created
        // object, and multiple <init> methods could be invoked for any one class along that path.
        // This is how multiple <init> methods get invoked when an object is instantiated.
        // The virtual machine invokes an <init> method declared in the object's class.
        // That <init> method first invokes either another <init> method in the same class,
        // or an <init> method in its superclass. This process continues all the way up to Object.
        final var typeList = universalParents.get(constructorType);
        if (typeList != null) {
            for (final var superTypeUri : typeList) {
                for (final var dep : typeFinder.getOrDefault(superTypeUri, new ArrayList<>())) {

                    resolveIfDefined(result, call, dep.getClassHierarchy()
                                    .get(ExtendedRevisionCallGraph.Scope.internalTypes)
                                    .get(FastenURI.create(superTypeUri)),
                            dep.product + "$" + dep.version, superTypeUri, isCallback);

                    final var callToInit =
                            new Call(Arrays.asList(call.indices.get(0), result.nodeCount),
                                    call.metadata, new ExtendedRevisionCallGraph.Node(
                                    call.target.changeName(call.target.getClassName(), "<clinit>"),
                                    call.target.getMetadata()));

                    resolveIfDefined(result, callToInit, dep.getClassHierarchy()
                                    .get(ExtendedRevisionCallGraph.Scope.internalTypes)
                                    .get(FastenURI.create(superTypeUri)),
                            dep.product + "$" + dep.version, superTypeUri, isCallback);
                }
            }
        }
    }

    private Collection<? extends String> getParentsUpToRoot(
            final Graph<String, DefaultEdge> universalCHA,
            final List<String> types) {
        final var result = new ArrayList<>(types);
        for (final var type : types) {
            result.addAll(getParentsUpToRoot(universalCHA, Graphs.predecessorListOf(universalCHA,
                    type)));
        }
        return result;
    }

    /**
     * Create a map with types as keys and a list of {@link ExtendedRevisionCallGraph} that
     * contain this type as values.
     *
     * @return type dictionary
     */
    private Map<String, List<ExtendedRevisionCallGraph>> createTypeDictionary() {

        Map<String, List<ExtendedRevisionCallGraph>> result = new HashMap<>();

        for (final var rcg : dependencies) {
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

    /**
     * Create a universal CHA for all dependencies including the artifact to resolve.
     *
     * @return universal CHA
     */
    private Pair<Map<String, List<String>>, Map<String, List<String>>> createUniversalCHA() {
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
     * Add super classes and interfaces to the universal CHA
     *
     * @param result      universal CHA graph
     * @param sourceTypes  source type
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

    private void resolveIfDefined(final CGHA cgha, final Call call,
                                  final ExtendedRevisionCallGraph.Type type,
                                  final String product, final String depTypeUri,
                                  boolean isCallback) {
        final var node = type.getDefinedMethods().get(call.target.getSignature());
        if (node != null) {
            addEdge(cgha, new Call(call.indices, call.metadata, node),
                    product, type, depTypeUri, isCallback);
        }
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
                        depType.getDefinedMethods(), depType.getSuperClasses(),
                        depType.getSuperInterfaces(), depType.getAccess(), depType.isFinal()));
        final var index = type.addMethod(
                new ExtendedRevisionCallGraph.Node(target.getUri(),
                        target.getMetadata()),
                cgha.nodeCount);
        cgha.CHA.put(keyType, type);
        return index;
    }

    private static String getVisionedUri(final String uri, final String product) {
        return uri.replaceFirst("/", Matcher.quoteReplacement("//" + product + "/"));
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