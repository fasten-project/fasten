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

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.graphdb.GraphMetadata;
import eu.fasten.core.data.graphdb.RocksDao;
import java.text.DecimalFormat;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import eu.fasten.core.data.metadatadb.codegen.tables.*;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallSitesRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseMerger {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMerger.class);

    private final Map<String, Set<String>> universalParents;
    private final Map<String, Set<String>> universalChildren;
    private final Map<String, Map<String, Set<Long>>> typeDictionary;
    private final DSLContext dbContext;
    private final RocksDao rocksDao;
    private final Set<Long> dependencySet;
    private Map<Long, String> namespaceMap;

    private boolean ignoreMissing;

    /**
     * Create instance of database merger from package names.
     *
     * @param dependencySet dependencies present in the resolution
     * @param dbContext     DSL context
     * @param rocksDao      rocks DAO
     */
    public DatabaseMerger(final List<String> dependencySet,
                          final DSLContext dbContext, final RocksDao rocksDao) {
        this.dbContext = dbContext;
        this.rocksDao = rocksDao;
        this.dependencySet = getDependenciesIds(dependencySet, dbContext);
        final var universalCHA = createUniversalCHA(this.dependencySet, dbContext, rocksDao);

        this.universalParents = universalCHA.getLeft();
        this.universalChildren = universalCHA.getRight();
        this.typeDictionary = createTypeDictionary(this.dependencySet, dbContext, rocksDao);

        this.ignoreMissing = false;
    }

    /**
     * Create instance of database merger from package versions ids.
     *
     * @param dependencySet dependencies present in the resolution
     * @param dbContext     DSL context
     * @param rocksDao      rocks DAO
     */
    public DatabaseMerger(final Set<Long> dependencySet,
                          final DSLContext dbContext, final RocksDao rocksDao) {
        this.dbContext = dbContext;
        this.rocksDao = rocksDao;
        this.dependencySet = dependencySet;
        final var universalCHA = createUniversalCHA(dependencySet, dbContext, rocksDao);

        this.universalParents = universalCHA.getLeft();
        this.universalChildren = universalCHA.getRight();
        this.typeDictionary = createTypeDictionary(dependencySet, dbContext, rocksDao);

        this.ignoreMissing = false;
    }

    public boolean getIgnoreMissing() {
        return ignoreMissing;
    }

    public void setIgnoreMissing(final boolean ignoreMissing) {
        this.ignoreMissing = ignoreMissing;
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
         * @param source    source ID
         * @param target    target ID
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
            this.signature = StringUtils.substringAfter(uri.getEntity(), ".");
        }

        public Node (final String typeUri, final String signature) {
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

    /**
     * Create fully merged for the entire dependency set.
     *
     * @return merged call graph
     */
    public DirectedGraph mergeAllDeps() {
        List<DirectedGraph> depGraphs = new ArrayList<>();
        for (final var dep : this.dependencySet) {
            var merged = mergeWithCHA(dep);
            if (merged != null) {
                depGraphs.add(merged);
            }
        }
        return augmentGraphs(depGraphs);
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

    public Map<Long, String> getTypeUrisMap(final List<CallSitesRecord> callSites) {
        var ids = new HashSet<Long>();
        callSites.forEach(c -> ids.addAll(Arrays.asList(c.getReceiverTypeIds())));
        var result = dbContext.selectFrom(ModuleNames.MODULE_NAMES).where(ModuleNames.MODULE_NAMES.ID.in(ids)).fetch();
        var map = new HashMap<Long, String>(result.size());
        result.forEach(r -> map.put(r.getId(), r.getName()));
        return map;
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
                var node = new Node(nodeMetadata.type, nodeMetadata.signature);
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
     * Resolve an external call.
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
        var receivers = arc.target.receiverUris.replace("[", "").replace("]", "").split(",");
        for (String receiverTypeUri : receivers) {
            var type = arc.target.type.toString();
            switch (type) {
                case "virtual":
                case "interface":
                    final var types = universalChildren.get(receiverTypeUri);
                    if (types != null) {
                        for (final var depTypeUri : types) {
                            for (final var target : typeDictionary.getOrDefault(depTypeUri,
                                    new HashMap<>())
                                    .getOrDefault(node.signature, new HashSet<>())) {
                                addEdge(result, callGraphData, arc.source, target, isCallback);
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
            e.printStackTrace();
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
     * Create a mapping from callable IDs to {@link Node}.
     *
     * @param callGraphData call graph
     * @param dbContext     DSL context
     * @return a map of callable IDs and {@link Node}
     */
    private Map<Long, Node> createTypeMap(final DirectedGraph callGraphData, final DSLContext dbContext) {
        var typeMap = new HashMap<Long, Node>();
        var nodesIds = callGraphData.nodes();
        var callables = dbContext
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(nodesIds))
                .fetch();

        callables.forEach(callable -> typeMap.put(callable.value1(),
                new Node(FastenJavaURI.create(callable.value2()).decanonicalize())));
        return typeMap;
    }

    /**
     * Tries to fetch a FastenURI from a metadata database with a given callable ID or throws
     * a new RuntimeException if no callable is found.
     *
     * @param callableId ID of a callable to fetch
     * @param dbContext  DSL context
     * @return Node
     */
    private Node fetchMissingNode(final Long callableId, final DSLContext dbContext) {
        var callable = dbContext
                .select(Callables.CALLABLES.FASTEN_URI)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.eq(callableId))
                .fetchOne();
        if (callable == null) {
            throw new RuntimeException("Callable with ID " + callableId
                    + " couldn't be found in the metadata database");
        }
        return new Node(FastenJavaURI.create(callable.value1()).decanonicalize());
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

    /**
     * Add super classes and interfaces to the universal CHA
     *
     * @param result      universal CHA graph
     * @param sourceType  source type
     * @param targetTypes list of target target types
     */
    private void addSuperTypes(final DefaultDirectedGraph<String, DefaultEdge> result,
                               final String sourceType,
                               final List<String> targetTypes) {
        for (final var superClass : targetTypes) {
            if (!result.containsVertex(superClass)) {
                result.addVertex(superClass);
            }
            if (!result.containsEdge(sourceType, superClass)) {
                result.addEdge(superClass, sourceType);
            }
        }
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
}
