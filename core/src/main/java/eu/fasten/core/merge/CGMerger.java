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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.graphdb.GraphMetadata;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.ModuleNames;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

public class CGMerger {

    private static final Logger logger = LoggerFactory.getLogger(CGMerger.class);

    private final Map<String, List<String>> universalChildren;
    private final Map<String, List<String>> universalParents;
    private final Map<String, Map<String, LongSet>> typeDictionary;

    private DSLContext dbContext;
    private RocksDao rocksDao;
    private Set<Long> dependencySet;
    private Map<Long, String> namespaceMap;

    private List<Pair<DirectedGraph, ExtendedRevisionJavaCallGraph>> ercgDependencySet;
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
        this.allUris = HashBiMap.create();
        final var graphAndDict = getDirectedGraphsAndTypeDict(dependencySet);
        this.ercgDependencySet = graphAndDict.getLeft();
        this.typeDictionary = graphAndDict.getRight();
    }

    private Pair<List<Pair<DirectedGraph, ExtendedRevisionJavaCallGraph>>, Map<String, Map<String,
            LongSet>>> getDirectedGraphsAndTypeDict(
            final List<ExtendedRevisionJavaCallGraph> dependencySet) {

        List<Pair<DirectedGraph, ExtendedRevisionJavaCallGraph>> depSet = new ArrayList<>();
        long offset = 0L;
        for (final var dep : dependencySet) {
            final var directedDep = ercgToDirectedGraph(dep, offset);
            offset = this.allUris.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
            depSet.add(ImmutablePair.of(directedDep, dep));
        }

        Map<String, Map<String, LongSet>> typeDict = new HashMap<>();
        for (final var rcg : dependencySet) {
            final var uris = rcg.mapOfFullURIStrings();
            for (final var type : rcg.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
                type.getValue().getDefinedMethods().forEach((signature, node) -> {
                    final var localId = type.getValue().getMethodKey(node);
                    final var oldType = typeDict.getOrDefault(type.getKey(), new HashMap<>());
                    final var oldNode = oldType.getOrDefault(node.getSignature(), new LongOpenHashSet());
                    oldNode.add(this.allUris.inverse().get(uris.get(localId)).longValue());
                    oldType.put(node.getSignature(), oldNode);
                    typeDict.put(type.getKey(), oldType);
                });
            }
        }

        return ImmutablePair.of(depSet, typeDict);
    }

    private DirectedGraph ercgToDirectedGraph(final ExtendedRevisionJavaCallGraph ercg, long offset) {
        final var result = new MergedDirectedGraph();
        final var uris = ercg.mapOfFullURIStrings();
        final var directedMerge = ExtendedRevisionJavaCallGraph.toLocalDirectedGraph(ercg);

        for (Long node : directedMerge.nodes()) {
            var uri = uris.get(node.intValue());

            if (!allUris.containsValue(uri)) {
                final var updatedNode = node + offset;
                this.allUris.put(updatedNode, uri);
                result.addVertex(updatedNode);
            }
        }

        return result;
    }


    /**
     * Create instance of callgraph merger from package names.
     *
     * @param dependencySet coordinates of dependencies present in the resolution
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
        universalCHA.getRight()
                .forEach((k, v) -> this.universalChildren.put(k, new ArrayList<>(v)));
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
        universalCHA.getRight()
                .forEach((k, v) -> this.universalChildren.put(k, new ArrayList<>(v)));
        this.universalParents = new HashMap<>(universalCHA.getLeft().size());
        universalCHA.getLeft().forEach((k, v) -> this.universalParents.put(k, new ArrayList<>(v)));
        this.typeDictionary = createTypeDictionary(dependencySet, dbContext, rocksDao);
    }

    public DirectedGraph mergeWithCHA(final long id) {
        final var callGraphData = fetchCallGraphData(id, rocksDao);
        var graphArcs = getArcs(id, callGraphData, rocksDao);
        return mergeWithCHA(callGraphData, graphArcs);
    }

    public DirectedGraph mergeWithCHA(final String artifact) {
        return mergeWithCHA(getPackageVersionId(artifact));
    }

    public DirectedGraph mergeWithCHA(final ExtendedRevisionJavaCallGraph cg) {
        for (final var directedERCGPair : this.ercgDependencySet) {
            if (cg.productVersion.equals(directedERCGPair.getRight().productVersion)) {
                return mergeWithCHA(directedERCGPair.getKey(), getERCGArcs(directedERCGPair.getRight()));
            }
        }
        logger.warn("This cg does not exist in the dependency set.");
        return new MergedDirectedGraph();
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


    private GraphMetadata getERCGArcs(final ExtendedRevisionJavaCallGraph ercg) {
        final var map = new Long2ObjectOpenHashMap<GraphMetadata.NodeMetadata>();
        final var allMethods = ercg.mapOfAllMethods();
        final var allUris = ercg.mapOfFullURIStrings();
        final var typeMap = ercg.nodeIDtoTypeNameMap();
        for (final var callsite : ercg.getGraph().getCallSites().entrySet()) {
            final var source = callsite.getKey().firstInt();
            final var target = callsite.getKey().secondInt();
            final var signature = allMethods.get(source).getSignature();
            final var type = typeMap.get(source);
            final var receivers = new HashSet<GraphMetadata.ReceiverRecord>();
            final var metadata = callsite.getValue();
            for (var obj : metadata.values()) {
                var receiver = (HashMap<String, Object>) obj;
                var receiverTypes = getReceiver(receiver);
                var callType = getCallType(receiver);
                var line = (int) receiver.get("line");
                var receiverSignature = allMethods.get(target).getSignature();
                receivers.add(new GraphMetadata.ReceiverRecord(line, callType, receiverSignature,
                        receiverTypes));
            }
            final var globalSource = this.allUris.inverse().get(allUris.get(source));
            var value = map.get(globalSource.longValue());
            if (value == null) {
                value = new GraphMetadata.NodeMetadata(type, signature, new ArrayList<>(receivers));
            } else {
                receivers.addAll(value.receiverRecords);
                value.receiverRecords.removeAll(receivers);
                value.receiverRecords.addAll(receivers);
            }
            map.put(globalSource.longValue(), value);
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
     * @param callGraphData DirectedGraph of the dependency to stitch
     * @param graphArcs     GraphMetadata of the dependency to stitch
     * @return merged call graph
     */
    public DirectedGraph mergeWithCHA(final DirectedGraph callGraphData, final GraphMetadata graphArcs) {
        final long totalTime = System.currentTimeMillis();

        if (callGraphData == null) {
            logger.error("Empty call graph data");
            return null;
        }

        var result = new MergedDirectedGraph();

        if (graphArcs == null) {
            return null;
        }

        final Set<LongLongPair> edges = ConcurrentHashMap.newKeySet();
        graphArcs.gid2NodeMetadata.long2ObjectEntrySet().parallelStream().forEach(entry -> {
            var sourceId = entry.getLongKey();
            var nodeMetadata = entry.getValue();
            for (var receiver : nodeMetadata.receiverRecords) {
                var arc = new Arc(sourceId, receiver);
                resolve(edges, arc, receiver.receiverSignature, callGraphData.isExternal(sourceId));
            }
        });


        for(LongLongPair edge: edges) {
            addEdge(result, edge.firstLong(), edge.secondLong());
        }

        logger.info("Merged call graphs in {} seconds, num node: {}, num edges: {}",
            new DecimalFormat("#0.000").format(
                (System.currentTimeMillis() - totalTime) / 1000d), result.numNodes(),
            result.numArcs());
        return result;
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
                var merged = mergeWithCHA(dep.getKey(), getERCGArcs(dep.getRight()));
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
     * @param arc           source, target and receivers information
     * @param signature     signature of the target
     * @param isCallback    true, if a given arc is a callback
     */
    private void resolve(final Set<LongLongPair> edges,
                         final Arc arc,
                         final String signature,
                         final boolean isCallback) {
	
	// Cache frequently accessed variables
    final Map<String, LongSet> emptyMap = Collections.emptyMap();
	final LongSet emptyLongSet = LongSets.emptySet();
	Map<String, Map<String, LongSet>> typeDictionary = this.typeDictionary;
	Map<String, List<String>> universalParents = this.universalParents;
	Map<String, List<String>> universalChildren = this.universalChildren;
	
        for (String receiverTypeUri : arc.target.receiverTypes) {
	    switch (arc.target.callType) {
                case VIRTUAL:
                case INTERFACE:
                    var foundTarget = false;
                    for (final var target : typeDictionary.getOrDefault(receiverTypeUri,
                            emptyMap).getOrDefault(signature, emptyLongSet)) {
                        addCall(edges, arc.source, target, isCallback);
                        foundTarget = true;
                    }
                    if (!foundTarget) {
                        final var parents = universalParents.get(receiverTypeUri);
                        if (parents != null) {
                            for (final var parentUri : parents) {
                                for (final var target : typeDictionary.getOrDefault(parentUri,
                                        emptyMap)
                                        .getOrDefault(signature, emptyLongSet)) {
                                    addCall(edges, arc.source, target, isCallback);
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
                                            emptyMap)
                                            .getOrDefault(signature, emptyLongSet)) {
                                        addCall(edges, arc.source, target,
                                                isCallback);
                                    }
                                }
                            }
                        }
                    }
                    break;
                case DYNAMIC:
                    logger.warn("OPAL didn't rewrite the dynamic");
                    break;
                default:
                    for (final var target : typeDictionary.getOrDefault(receiverTypeUri,
                            emptyMap).getOrDefault(signature, emptyLongSet)) {
                        addCall(edges, arc.source, target, isCallback);
                    }
                    break;
            }
        }
    }

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
    private Map<String, Map<String, LongSet>> createTypeDictionary(
            final Set<Long> dependenciesIds, final DSLContext dbContext, final RocksDao rocksDao) {
        final long startTime = System.currentTimeMillis();
        var result = new HashMap<String, Map<String, LongSet>>();

        var callables = getCallables(dependenciesIds, rocksDao);

        dbContext.select(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(callables))
                .fetch()
                .forEach(callable -> {
                    var node = new Node(FastenJavaURI.create(callable.value1()).decanonicalize());
                    result.putIfAbsent(node.typeUri, new HashMap<>());
                    var type = result.get(node.typeUri);
                    var newestSet = new LongOpenHashSet();
                    newestSet.add(callable.value2().longValue());
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
                .select(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES,
                        Modules.MODULES.SUPER_INTERFACES)
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
                var superClasses = Arrays.stream(callable.value2()).map(n -> namespaceMap.get(n))
                        .collect(Collectors.toList());
                addSuperTypes(universalCHA, namespaceMap.get(callable.value1()), superClasses);
            } catch (NullPointerException ignore) {
            }
            try {
                var superInterfaces = Arrays.stream(callable.value3()).map(n -> namespaceMap.get(n))
                        .collect(Collectors.toList());
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


    private void addEdge(final MergedDirectedGraph result,
                         final long source, final long target) {
        result.addVertex(source);
        result.addVertex(target);
        result.addEdge(source, target);
    }

    /**
     * Augment generated merged call graphs.
     *
     * @param depGraphs merged call graphs
     * @return augmented graph
     */
    private DirectedGraph augmentGraphs(final List<DirectedGraph> depGraphs) {
        var result = new MergedDirectedGraph();
        int numNode = 0;
        for (DirectedGraph depGraph : depGraphs) {
            numNode += depGraph.numNodes();
            for (LongLongPair longLongPair : depGraph.edgeSet()) {
                addEdge(result, longLongPair.firstLong(), longLongPair.secondLong());
            }
        }
        logger.info("Number of Augmented nodes: {}", numNode);

        return result;
    }

    /**
     * Clone internal calls and internal arcs to the merged call graph.
     *
     * @param result        resulting merged call graph
     * @param callGraphData initial call graph
     */
    private void cloneNodesAndArcs(final MergedDirectedGraph result,
                                   final DirectedGraph callGraphData) {
        var internalNodes = callGraphData.nodes();
        internalNodes.removeAll(callGraphData.externalNodes());
        for (var node : internalNodes) {
            result.addInternalNode(node);
        }
        for (var source : internalNodes) {
            for (var target : callGraphData.successors(source)) {
                if (callGraphData.isInternal(target)) {
                    result.addEdge(source, target);
                }
            }
        }
    }

    /**
     * Add a resolved edge to the {@link DirectedGraph}.
     *
     * @param source        source callable ID
     * @param target        target callable ID
     * @param isCallback    true, if a given arc is a callback
     */
    private void addCall(final Set<LongLongPair> edges,
                         Long source, Long target, final boolean isCallback) {
	if (isCallback) {
	    Long t = source;
	    source = target;
	    target = t;
	}

	edges.add(LongLongPair.of(source, target));
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
    private GraphMetadata getArcs(final long index, final DirectedGraph callGraphData,
                                  final RocksDao rocksDao) {
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
