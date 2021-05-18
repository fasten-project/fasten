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
import eu.fasten.core.data.*;
import eu.fasten.core.data.graphdb.GraphMetadata;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.*;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CGMerger {

    private static final Logger logger = LoggerFactory.getLogger(CGMerger.class);

    private final Map<String, List<String>> universalChildren;
    private final Map<String, List<String>> universalParents;

    private Map<String, Map<String, Set<Long>>> typeDictionary;
    private DSLContext dbContext;
    private RocksDao rocksDao;
    private Set<Long> dependencySet;
    private Map<Long, String> namespaceMap;

    private List<ExtendedRevisionJavaCallGraph> ercgDependencySet;
    private BiMap<Long, String> allUris;


    public BiMap<Long, String> getAllUris() {
        return this.allUris;
    }

    /**
     * Creates instance of callgraph merger.
     *
     * @param dependencySet all artifacts present in a resolution
     */
    public CGMerger(final List<ExtendedRevisionJavaCallGraph> dependencySet) {

        final var UCH = createUniversalCHA(dependencySet);
        this.universalParents = UCH.getLeft();
        this.universalChildren = UCH.getRight();
        this.ercgDependencySet = dependencySet;
        this.allUris = HashBiMap.create();
    }

    /**
     * Create instance of callgraph merger from package names.
     *
     * @param dependencySet dependencies present in the resolution
     * @param dbContext     DSL context
     * @param rocksDao      rocks DAO
     */
    public CGMerger(final List<String> dependencySet,
                    final DSLContext dbContext, final RocksDao rocksDao) {
        this.dbContext = dbContext;
        this.rocksDao = rocksDao;
        this.dependencySet = getDependenciesIds(dependencySet, dbContext);
        final var universalCHA = createUniversalCHA(this.dependencySet, dbContext, rocksDao);
        this.universalChildren = new HashMap<>(universalCHA.getRight().size());
        universalCHA.getRight().forEach((k, v) -> this.universalChildren.put(k, new ArrayList<>(v)));
        this.universalParents = new HashMap<>(universalCHA.getLeft().size());
        universalCHA.getLeft().forEach((k, v) -> this.universalParents.put(k, new ArrayList<>(v)));
        this.typeDictionary = createTypeDictionary(this.dependencySet, dbContext, rocksDao);
    }

    /**
     * Create instance of callgraph merger from package versions ids.
     *
     * @param dependencySet dependencies present in the resolution
     * @param dbContext     DSL context
     * @param rocksDao      rocks DAO
     */
    public CGMerger(final Set<Long> dependencySet,
                    final DSLContext dbContext, final RocksDao rocksDao) {
        this.dbContext = dbContext;
        this.rocksDao = rocksDao;
        this.dependencySet = dependencySet;
        final var universalCHA = createUniversalCHA(dependencySet, dbContext, rocksDao);
        this.universalChildren = new HashMap<>(universalCHA.getRight().size());
        universalCHA.getRight().forEach((k, v) -> this.universalChildren.put(k, new ArrayList<>(v)));
        this.universalParents = new HashMap<>(universalCHA.getLeft().size());
        universalCHA.getLeft().forEach((k, v) -> this.universalParents.put(k, new ArrayList<>(v)));
        this.typeDictionary = createTypeDictionary(dependencySet, dbContext, rocksDao);
    }

    // Local
    /**
     * Class with resolved calls and CHA.
     */
    public static class CGHA {

        private final ConcurrentMap<IntIntPair, Map<Object, Object>> graph;
        private final ConcurrentMap<String, JavaType> CHA;
        private final AtomicInteger nodeCount;

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

    // Local
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
     * Single arc containing source and target IDs and a list of receivers.
     */
    private static class Arc {
        private final Long source;
        private final GraphMetadata.ReceiverRecord target;

        /**
         * Create new Arc instance.
         *
         * @param source source ID
         * @param target target ID
         */
        public Arc(final Long source, final GraphMetadata.ReceiverRecord target) {
            this.source = source;
            this.target = target;
        }
    }

    /**
     * Node containing method signature and type information.
     */
    private static class Node {
        private final String signature;
        private final String typeUri;

        /**
         * Create new Node instance.
         *
         * @param uri fastenURI
         */
        public Node(final FastenJavaURI uri) {
            this.typeUri = "/" + uri.getNamespace() + "/" + uri.getClassName();
            this.signature = StringUtils.substringAfter(uri.decanonicalize().getEntity(), ".");
        }

        public Node(final String typeUri, final String signature) {
            this.typeUri = typeUri;
            this.signature = signature;
        }

        /**
         * Check if the given method is a constructor.
         *
         * @return true, if the method is constructor
         */
        public boolean isConstructor() {
            return signature.startsWith("<init>");
        }

        /**
         * Get full fastenURI.
         *
         * @return fastenURI
         */
        public String getUri() {
            return typeUri + "." + signature;
        }
    }

    private long updateNode(final long node, final long offset,
                            final BiMap<Integer, String> uris) {
        var uri = uris.get((int) node);

        if (allUris.containsValue(uri)) {
            return allUris.inverse().get(uri);
        } else {
            final var updatedNode = node + offset;
            this.allUris.put(updatedNode, uri);
            return updatedNode;
        }
    }

    public DirectedGraph mergeWithCHA(final ExtendedRevisionJavaCallGraph artifact) {
        final var artifactCGHA = new CGHA(artifact);
        final var callGraphData = ercgToDirectedGraph(artifact, artifactCGHA);

        var result = new ArrayImmutableDirectedGraph.Builder();
        cloneNodesAndArcs(result, callGraphData);

        var graphArcs = getERCGArcs(artifact);
        for (var entry : graphArcs.gid2NodeMetadata.long2ObjectEntrySet()) {
            var sourceId = entry.getLongKey();
            var nodeMetadata = entry.getValue();
            for (var receiver : nodeMetadata.receiverRecords) {
                var arc = new Arc(sourceId, receiver);
                var node = new Node(nodeMetadata.type, receiver.receiverSignature);
                resolve(result, callGraphData, arc, node, callGraphData.isExternal(sourceId));
            }
        }
        return result.build();
    }

    private DirectedGraph ercgToDirectedGraph(ExtendedRevisionJavaCallGraph ercg, CGHA result) {
        final var directedGraph = new FastenDefaultDirectedGraph();
        addThisMergeToResult(directedGraph, buildRCG(ercg, result));
        return directedGraph;
    }

    private GraphMetadata getERCGArcs(ExtendedRevisionJavaCallGraph ercg) {
        final var map = new Long2ObjectOpenHashMap<GraphMetadata.NodeMetadata>();
        for (var callsite : ercg.getGraph().getCallSites().entrySet()) {
            var source = callsite.getKey().firstInt();
            var target = callsite.getKey().secondInt();
            var signature = ercg.mapOfAllMethods().get(source).getSignature();
            var type = ercg.nodeIDtoTypeNameMap().get(source);
            var receivers = new ArrayList<GraphMetadata.ReceiverRecord>();
            var metadata = callsite.getValue();
            for (var obj : metadata.values()) {
                var receiver = (HashMap<String, Object>) obj;
                var receiverTypes = getReceiver(receiver);
                var callType = getCallType(receiver);
                var line = (int) receiver.get("line");
                var receiverSignature = ercg.mapOfAllMethods().get(target).getSignature();
                receivers.add(new GraphMetadata.ReceiverRecord(line, callType, receiverSignature, receiverTypes));
            }
            map.put(source, new GraphMetadata.NodeMetadata(type, signature, receivers));
        }
        return new GraphMetadata(map);
    }

    private GraphMetadata.ReceiverRecord.CallType getCallType(HashMap<String, Object> callsite) {
        switch (callsite.get("type").toString()) {
            case "invokespecial":
                return GraphMetadata.ReceiverRecord.CallType.SPECIAL;
            case "invokestatic":
                return GraphMetadata.ReceiverRecord.CallType.STATIC;
            case "invokevirtual":
                return GraphMetadata.ReceiverRecord.CallType.VIRTUAL;
            case "invokeinterface":
                return GraphMetadata.ReceiverRecord.CallType.INTERFACE;
            case "invokedynamic":
                return GraphMetadata.ReceiverRecord.CallType.DYNAMIC;
            default:
                return null;
        }
    }

    /**
     * Merges a call graph with its dependencies using CHA algorithm.
     *
     * @param artifactId package version id of an artifact to resolve
     * @return merged call graph
     */
    public DirectedGraph mergeWithCHA(final long artifactId) {
        final long totalTime = System.currentTimeMillis();
        final var callGraphData = fetchCallGraphData(artifactId, rocksDao);

        if (callGraphData == null) {
            logger.error("Empty call graph data");
            return null;
        }

        var result = new ArrayImmutableDirectedGraph.Builder();

        cloneNodesAndArcs(result, callGraphData);

        final long startTime = System.currentTimeMillis();

        var graphArcs = getArcs(artifactId, callGraphData, rocksDao);
        if (graphArcs == null) {
            return null;
        }
        for (var entry : graphArcs.gid2NodeMetadata.long2ObjectEntrySet()) {
            var sourceId = entry.getLongKey();
            var nodeMetadata = entry.getValue();
            for (var receiver : nodeMetadata.receiverRecords) {
                var arc = new Arc(sourceId, receiver);
                var node = new Node(nodeMetadata.type, receiver.receiverSignature);
                resolve(result, callGraphData, arc, node, callGraphData.isExternal(sourceId));
            }
        }
        logger.info("Stitched in {} seconds", new DecimalFormat("#0.000")
                .format((System.currentTimeMillis() - startTime) / 1000d));
        logger.info("Merged call graphs in {} seconds", new DecimalFormat("#0.000")
                .format((System.currentTimeMillis() - totalTime) / 1000d));
        return result.build();
    }

    /**
     * Merges a call graph with its dependencies using CHA algorithm.
     *
     * @param artifact artifact to resolve
     * @return merged call graph
     */
    public DirectedGraph mergeWithCHA(final String artifact) {
        return mergeWithCHA(getPackageVersionId(artifact));
    }

    /**
     * Create fully merged for the entire dependency set.
     *
     * @return merged call graph
     */
    public DirectedGraph mergeAllDeps() {
        List<DirectedGraph> depGraphs = new ArrayList<>();
        if (this.dbContext == null) {
            for (final var dep : this.ercgDependencySet) {
                var merged = mergeWithCHA(dep);
                if (merged != null) {
                    depGraphs.add(merged);
                }
            }
        } else {
            for (final var dep : this.dependencySet) {
                var merged = mergeWithCHA(dep);
                if (merged != null) {
                    depGraphs.add(merged);
                }
            }
        }
        return augmentGraphs(depGraphs);
    }

    /**
     * Resolve call.
     *
     * @param result        graph with resolved calls
     * @param callGraphData graph for the artifact to resolve
     * @param arc           source, target and receivers information
     * @param node          type and method information
     * @param isCallback    true, if a given arc is a callback
     */
    private void resolve(final ArrayImmutableDirectedGraph.Builder result,
                         final DirectedGraph callGraphData,
                         final Arc arc,
                         final Node node,
                         final boolean isCallback) {
        for (String receiverTypeUri : arc.target.receiverTypes) {
            var type = arc.target.callType.toString();
            switch (type) {
                case "virtual":
                case "interface":
                    var foundTarget = false;
                    for (final var target : typeDictionary.getOrDefault(receiverTypeUri,
                            new HashMap<>()).getOrDefault(node.signature, new HashSet<>())) {
                        addEdge(result, callGraphData, arc.source, target, isCallback);
                        foundTarget = true;
                    }
                    if (!foundTarget) {
                        final var parents = universalParents.get(receiverTypeUri);
                        if (parents != null) {
                            for (final var parentUri : parents) {
                                for (final var target : typeDictionary.getOrDefault(parentUri,
                                        new HashMap<>())
                                        .getOrDefault(node.signature, new HashSet<>())) {
                                    addEdge(result, callGraphData, arc.source, target, isCallback);
                                    foundTarget = true;
                                    break;
                                }
                                if (foundTarget) {
                                    break;
                                }
                            }
                        }
                        if (!foundTarget) {
                            final var types = universalChildren.get(receiverTypeUri);
                            if (types != null) {
                                for (final var depTypeUri : types) {
                                    for (final var target : typeDictionary.getOrDefault(depTypeUri,
                                            new HashMap<>())
                                            .getOrDefault(node.signature, new HashSet<>())) {
                                        addEdge(result, callGraphData, arc.source, target,
                                                isCallback);
                                    }
                                }
                            }
                        }
                    }
                    break;
                case "dynamic":
                    logger.warn("OPAL didn't rewrite the dynamic");
                    break;
                default:
                    for (final var target : typeDictionary.getOrDefault(receiverTypeUri,
                            new HashMap<>()).getOrDefault(node.signature, new HashSet<>())) {
                        addEdge(result, callGraphData, arc.source, target, isCallback);
                    }
                    break;
            }
        }
    }

    // Local
    private ArrayList<String> getReceiver(final HashMap<String, Object> callSite) {
        return new ArrayList<>(Arrays.asList(((String) callSite.get(
                "receiver")).replace("[", "").replace("]", "").split(",")));
    }

    /**
     * Create a mapping from types and method signatures to callable IDs.
     *
     * @param dependenciesIds IDs of dependencies
     * @param dbContext       DSL context
     * @param rocksDao        rocks DAO
     * @return a type dictionary
     */
    private Map<String, Map<String, Set<Long>>> createTypeDictionary(
            final Set<Long> dependenciesIds, final DSLContext dbContext, final RocksDao rocksDao) {
        final long startTime = System.currentTimeMillis();
        var result = new HashMap<String, Map<String, Set<Long>>>();

        var callables = getCallables(dependenciesIds, rocksDao);

        dbContext.select(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(callables))
                .fetch()
                .forEach(callable -> {
                    var node = new Node(FastenJavaURI.create(callable.value1()).decanonicalize());
                    result.putIfAbsent(node.typeUri, new HashMap<>());
                    var type = result.get(node.typeUri);
                    var newestSet = new HashSet<Long>();
                    newestSet.add(callable.value2());
                    type.merge(node.signature, newestSet, (old, newest) -> {
                        old.addAll(newest);
                        return old;
                    });
                });

        logger.info("Created the type dictionary with {} types in {} seconds", result.size(),
                new DecimalFormat("#0.000")
                        .format((System.currentTimeMillis() - startTime) / 1000d));

        return result;
    }

    // Local

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
                        type.getValue().getSuperClasses()
                                .stream().map(FastenURI::toString).collect(Collectors.toList()));
                addSuperTypes(result, type.getKey(),
                        type.getValue().getSuperInterfaces()
                                .stream().map(FastenURI::toString).collect(Collectors.toList()));
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
            universalParents.put(type, organize(parents));
        }
        return ImmutablePair.of(universalParents, universalChildren);
    }

    /**
     * Create a universal class hierarchy from all dependencies.
     *
     * @param dependenciesIds IDs of dependencies
     * @param dbContext       DSL context
     * @param rocksDao        rocks DAO
     * @return universal CHA
     */
    private Pair<Map<String, Set<String>>, Map<String, Set<String>>> createUniversalCHA(
            final Set<Long> dependenciesIds, final DSLContext dbContext, final RocksDao rocksDao) {
        final long startTime = System.currentTimeMillis();
        var universalCHA = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);

        var callables = getCallables(dependenciesIds, rocksDao);

        var modulesIds = dbContext
                .select(Callables.CALLABLES.MODULE_ID)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(callables))
                .fetch();

        var modules = dbContext
                .select(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES, Modules.MODULES.SUPER_INTERFACES)
                .from(Modules.MODULES)
                .where(Modules.MODULES.ID.in(modulesIds))
                .fetch();

        var namespaceIDs = new HashSet<>(modules.map(Record3::value1));
        modules.forEach(m -> namespaceIDs.addAll(Arrays.asList(m.value2())));
        modules.forEach(m -> namespaceIDs.addAll(Arrays.asList(m.value3())));
        var namespaceResults = dbContext
                .select(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME)
                .from(ModuleNames.MODULE_NAMES)
                .where(ModuleNames.MODULE_NAMES.ID.in(namespaceIDs))
                .fetch();
        this.namespaceMap = new HashMap<>(namespaceResults.size());
        namespaceResults.forEach(r -> namespaceMap.put(r.value1(), r.value2()));

        for (var callable : modules) {
            if (!universalCHA.containsVertex(namespaceMap.get(callable.value1()))) {
                universalCHA.addVertex(namespaceMap.get(callable.value1()));
            }

            try {
                var superClasses = Arrays.stream(callable.value2()).map(n -> namespaceMap.get(n)).collect(Collectors.toList());
                addSuperTypes(universalCHA, namespaceMap.get(callable.value1()), superClasses);
            } catch (NullPointerException ignore) {
            }
            try {
                var superInterfaces = Arrays.stream(callable.value3()).map(n -> namespaceMap.get(n)).collect(Collectors.toList());
                addSuperTypes(universalCHA, namespaceMap.get(callable.value1()), superInterfaces);
            } catch (NullPointerException ignore) {
            }
        }

        final Map<String, Set<String>> universalParents = new HashMap<>();
        final Map<String, Set<String>> universalChildren = new HashMap<>();
        for (final var type : universalCHA.vertexSet()) {

            final var children = new HashSet<>(Collections.singletonList(type));
            children.addAll(getAllChildren(universalCHA, type));
            universalChildren.put(type, children);

            final var parents = new HashSet<>(Collections.singletonList(type));
            parents.addAll(getAllParents(universalCHA, type));
            universalParents.put(type, parents);
        }

        logger.info("Created the Universal CHA with {} vertices in {}",
                universalCHA.vertexSet().size(),
                new DecimalFormat("#0.000")
                        .format((System.currentTimeMillis() - startTime) / 1000d));

        return ImmutablePair.of(universalParents, universalChildren);
    }

    // Local
    private List<String> organize(ArrayList<String> parents) {
        final List<String> result = new ArrayList<>();
        for (String parent : parents) {
            if (!result.contains(parent) && !parent.equals("/java.lang/Object")) {
                result.add(parent);
            }
        }
        result.add("/java.lang/Object");
        return result;
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
                               final List<String> targetTypes) {
        for (final var superClass : targetTypes) {
            if (!result.containsVertex(superClass)) {
                result.addVertex(superClass);
            }
            if (!result.containsEdge(sourceTypes, superClass)) {
                result.addEdge(superClass, sourceTypes);
            }
        }
    }

    // Local
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
                .graph(new JavaGraph(artifact.getGraph().getCallSites(),
                        new HashMap<>(),
                        result.graph))
                .nodeCount(result.nodeCount.get())
                .build();
    }

    // Local
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

    // Local
    private void addEdge(final FastenDefaultDirectedGraph result,
                         final long source, final long target) {
        result.addInternalNode(source);
        result.addInternalNode(target);
        result.addEdge(source, target);
    }

    /**
     * Augment generated merged call graphs.
     *
     * @param depGraphs merged call graphs
     * @return augmented graph
     */
    private DirectedGraph augmentGraphs(final List<DirectedGraph> depGraphs) {
        var result = new ArrayImmutableDirectedGraph.Builder();

        for (final var depGraph : depGraphs) {
            for (final var node : depGraph.nodes()) {
                for (final var successor : depGraph.successors(node)) {
                    addEdge(result, depGraph, node, successor, false);
                }
            }
        }
        return result.build();
    }

    /**
     * Clone internal calls and internal arcs to the merged call graph.
     *
     * @param result        resulting merged call graph
     * @param callGraphData initial call graph
     */
    private void cloneNodesAndArcs(final ArrayImmutableDirectedGraph.Builder result,
                                   final DirectedGraph callGraphData) {
        var internalNodes = callGraphData.nodes();
        internalNodes.removeAll(callGraphData.externalNodes());
        for (var node : internalNodes) {
            result.addInternalNode(node);
        }
        for (var source : internalNodes) {
            for (var target : callGraphData.successors(source)) {
                if (callGraphData.isInternal(target)) {
                    result.addArc(source, target);
                }
            }
        }
    }

    /**
     * Add a resolved edge to the {@link DirectedGraph}.
     *
     * @param result        graph with resolved calls
     * @param source        source callable ID
     * @param callGraphData graph for the artifact to resolve
     * @param target        target callable ID
     * @param isCallback    true, if a given arc is a callback
     */
    private void addEdge(final ArrayImmutableDirectedGraph.Builder result,
                         final DirectedGraph callGraphData,
                         final Long source, final Long target, final boolean isCallback) {
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
            if (isCallback) {
                result.addArc(target, source);
            } else {
                result.addArc(source, target);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Fetches metadata of the nodes of first arg from database.
     *
     * @param graph DirectedGraph to search for its callable's metadata in the database.
     * @return Map of callable ids and their corresponding metadata in the form of
     * JSONObject.
     */
    public Map<Long, JSONObject> getCallablesMetadata(final DirectedGraph graph) {
        final Map<Long, JSONObject> result = new HashMap<>();

        final var metadata = dbContext
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.METADATA)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(graph.nodes()))
                .fetch();
        for (final var callable : metadata) {
            result.put(callable.value1(), new JSONObject(callable.value2().data()));
        }
        return result;
    }

    /**
     * Retrieve external calls and constructor calls from a call graph.
     *
     * @param callGraphData call graph
     * @return list of external and constructor calls
     */
    private GraphMetadata getArcs(final long index, final DirectedGraph callGraphData, final RocksDao rocksDao) {
        try {
            return rocksDao.getGraphMetadata(index, callGraphData);
        } catch (RocksDBException e) {
            logger.error("Could not retrieve arcs (graph metadata) from graph database:", e);
            return null;
        }
    }

    /**
     * Retrieve a call graph from a graph database given a maven coordinate.
     *
     * @param rocksDao rocks DAO
     * @return call graph
     */
    private DirectedGraph fetchCallGraphData(final long artifactId, final RocksDao rocksDao) {
        DirectedGraph callGraphData = null;
        try {
            callGraphData = rocksDao.getGraphData(artifactId);
        } catch (RocksDBException e) {
            logger.error("Could not retrieve callgraph data from the graph database:", e);
        }
        return callGraphData;
    }

    /**
     * Get package version id for an artifact.
     *
     * @param artifact artifact in format groupId:artifactId:version
     * @return package version id
     */
    private long getPackageVersionId(final String artifact) {
        var packageName = artifact.split(":")[0] + ":" + artifact.split(":")[1];
        var version = artifact.split(":")[2];
        return Objects.requireNonNull(dbContext
                .select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                .and(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetchOne())
                .component1();
    }

    /**
     * Get callables from dependencies.
     *
     * @param dependenciesIds dependencies IDs
     * @param rocksDao        rocks DAO
     * @return list of callables
     */
    private List<Long> getCallables(final Set<Long> dependenciesIds, final RocksDao rocksDao) {
        var callables = new ArrayList<Long>();
        for (var id : dependenciesIds) {
            try {
                var cg = rocksDao.getGraphData(id);
                var nodes = cg.nodes();
                nodes.removeAll(cg.externalNodes());
                callables.addAll(nodes);
            } catch (RocksDBException | NullPointerException e) {
                logger.error("Couldn't retrieve a call graph with ID: {}", id);
            }
        }
        return callables;
    }

    /**
     * Get dependencies IDs from a metadata database.
     *
     * @param dbContext DSL context
     * @return set of IDs of dependencies
     */
    private Set<Long> getDependenciesIds(final List<String> dependencySet,
                                         final DSLContext dbContext) {
        var coordinates = new HashSet<>(dependencySet);

        Condition depCondition = null;

        for (var dependency : coordinates) {
            var packageName = dependency.split(":")[0] + ":" + dependency.split(":")[1];
            var version = dependency.split(":")[2];

            if (depCondition == null) {
                depCondition = Packages.PACKAGES.PACKAGE_NAME.eq(packageName)
                        .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version));
            } else {
                depCondition = depCondition.or(Packages.PACKAGES.PACKAGE_NAME.eq(packageName)
                        .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version)));
            }
        }
        return dbContext
                .select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(depCondition)
                .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetch()
                .intoSet(PackageVersions.PACKAGE_VERSIONS.ID);
    }
}
