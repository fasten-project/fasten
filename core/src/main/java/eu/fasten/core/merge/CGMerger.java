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
import eu.fasten.core.data.ClassHierarchy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.callableindex.GraphMetadata;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CGMerger {

    private static final Logger logger = LoggerFactory.getLogger(CGMerger.class);

    private final ClassHierarchy classHierarchy;

    private DSLContext dbContext;
    private RocksDao rocksDao;
    private Set<Long> dependencySet;

    private List<Pair<DirectedGraph, PartialJavaCallGraph>> ercgDependencySet;
    private BiMap<Long, String> allUris;

    private Map<String, Map<String, String>> externalUris;
    private long externalGlobalIds = 0;

    public BiMap<Long, String> getAllUris() {
        return this.allUris;
    }

    public ClassHierarchy getClassHierarchy() {
        return classHierarchy;
    }

    /**
     * Creates instance of callgraph merger.
     *
     * @param dependencySet all artifacts present in a resolution
     */
    public CGMerger(final List<PartialJavaCallGraph> dependencySet) {
        this(dependencySet, false);
    }

    /**
     * Creates instance of callgraph merger.
     *
     * @param dependencySet all artifacts present in a resolution
     * @param withExternals true if unresolved external calls should be kept in the generated graph, they will be
     *                      assigned negative ids
     */
    public CGMerger(final List<PartialJavaCallGraph> dependencySet, boolean withExternals) {

        this.allUris = HashBiMap.create();
        if (withExternals) {
            this.externalUris = new HashMap<>();
        }
        this.ercgDependencySet = convertToDirectedGraphs(dependencySet);
        this.classHierarchy = new ClassHierarchy(dependencySet, this.allUris);
    }

    private List<Pair<DirectedGraph, PartialJavaCallGraph>> convertToDirectedGraphs(
        final List<PartialJavaCallGraph> dependencySet) {
        List<Pair<DirectedGraph, PartialJavaCallGraph>> depSet = new ArrayList<>();
        long offset = 0L;
        for (final var dep : dependencySet) {
            final var directedDep = ercgToDirectedGraph(dep, offset);
            offset = this.allUris.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
            depSet.add(ImmutablePair.of(directedDep, dep));
        }
        return depSet;
    }

    private DirectedGraph ercgToDirectedGraph(final PartialJavaCallGraph ercg, long offset) {
        final var result = new MergedDirectedGraph();
        final var uris = ercg.mapOfFullURIStrings();
        final var internalNodes = getAllInternalNodes(ercg);

        for (Long node : internalNodes) {
            var uri = uris.get(node.intValue());

            if (!allUris.containsValue(uri)) {
                final var updatedNode = node + offset;
                this.allUris.put(updatedNode, uri);
                result.addVertex(updatedNode);
            }
        }

        // Index external URIs
        if (isWithExternals()) {
            for (Map.Entry<String, JavaType> entry : ercg.getClassHierarchy()
                .get(JavaScope.externalTypes).entrySet()) {
                Map<String, String> typeMap =
                    this.externalUris.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
                for (JavaNode node : entry.getValue().getMethods().values()) {
                    typeMap.put(node.getSignature(), node.getUri().toString());
                }
            }
        }

        return result;
    }

    private LongSet getAllInternalNodes(PartialJavaCallGraph pcg) {
        LongSet nodes = new LongOpenHashSet();
        pcg.getClassHierarchy().get(JavaScope.internalTypes)
            .forEach((key, value) -> value.getMethods().keySet().forEach(nodes::add));
        return nodes;
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
        this.classHierarchy = new ClassHierarchy(this.dependencySet, dbContext, rocksDao);
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
        this.classHierarchy = new ClassHierarchy(dependencySet, dbContext, rocksDao);
    }

    /**
     * @return true if unresolved external calls should be kept in the generated graph
     */
    public boolean isWithExternals() {
        return this.externalUris != null;
    }

    public DirectedGraph mergeWithCHA(final long id) {
        final var callGraphData = fetchCallGraphData(id, rocksDao);
        var metadata = rocksDao.getGraphMetadata(id, callGraphData);
        return mergeWithCHA(callGraphData, metadata);
    }

    public DirectedGraph mergeWithCHA(final String artifact) {
        return mergeWithCHA(getPackageVersionId(artifact));
    }

    public DirectedGraph mergeWithCHA(final PartialJavaCallGraph cg) {
        for (final var directedERCGPair : this.ercgDependencySet) {
            if (cg.uri.equals(directedERCGPair.getRight().uri)) {
                return mergeWithCHA(directedERCGPair.getKey(),
                    getERCGArcs(directedERCGPair.getRight()));
            }
        }
        logger.warn("This cg does not exist in the dependency set.");
        return new MergedDirectedGraph();
    }

    public BiMap<Long, String> getAllUrisFromDB(DirectedGraph dg) {
        Set<Long> gIDs = new HashSet<>();
        for (Long node : dg.nodes()) {
            if (node > 0) {
                gIDs.add(node);
            }
        }
        BiMap<Long, String> uris = HashBiMap.create();
        dbContext
            .select(Callables.CALLABLES.ID, Packages.PACKAGES.PACKAGE_NAME,
                PackageVersions.PACKAGE_VERSIONS.VERSION,
                Callables.CALLABLES.FASTEN_URI)
            .from(Callables.CALLABLES, Modules.MODULES, PackageVersions.PACKAGE_VERSIONS,
                Packages.PACKAGES)
            .where(Callables.CALLABLES.ID.in(gIDs))
            .and(Modules.MODULES.ID.eq(Callables.CALLABLES.MODULE_ID))
            .and(PackageVersions.PACKAGE_VERSIONS.ID.eq(Modules.MODULES.PACKAGE_VERSION_ID))
            .and(Packages.PACKAGES.ID.eq(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID))
            .fetch().forEach(record -> uris.put(record.component1(),
                "fasten://mvn!" + record.component2() + "$" + record.component3() +
                    record.component4()));

        return uris;
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

    private GraphMetadata getERCGArcs(final PartialJavaCallGraph ercg) {
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
                // TODO this cast seems to be unnecessary
                @SuppressWarnings("unchecked")
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
     * @param callGraph DirectedGraph of the dependency to stitch
     * @param metadata  GraphMetadata of the dependency to stitch
     * @return merged call graph
     */
    public DirectedGraph mergeWithCHA(final DirectedGraph callGraph, final GraphMetadata metadata) {
        if (callGraph == null) {
            logger.error("Empty call graph data");
            return null;
        }
        if (metadata == null) {
            logger.error("Graph metadata is not available, cannot merge");
            return null;
        }

        var result = new MergedDirectedGraph();

        final Set<LongLongPair> edges = ConcurrentHashMap.newKeySet();

        metadata.gid2NodeMetadata.long2ObjectEntrySet().parallelStream().forEach(entry -> {
            var sourceId = entry.getLongKey();
            var nodeMetadata = entry.getValue();
            for (var receiver : nodeMetadata.receiverRecords) {
                var arc = new Arc(sourceId, receiver);
                var signature = receiver.receiverSignature;
                if (receiver.receiverSignature.startsWith("/")) {
                    signature =
                        CallGraphUtils.decode(StringUtils.substringAfter(
                            FastenJavaURI.create(receiver.receiverSignature).decanonicalize()
                                .getEntity(), "."));
                }
                if (!resolve(edges, arc, signature, callGraph.isExternal(sourceId))) {
                    // The target could not be resolved, store it as external node
                    if (isWithExternals()) {
                        addExternal(result, edges, arc);
                    }
                }
            }
        });

        for (LongLongPair edge : edges) {
            addEdge(result, edge.firstLong(), edge.secondLong());
        }

        return result;
    }

    /**
     * Add a non resolved edge to the {@link DirectedGraph}.
     */
    private synchronized void addExternal(final MergedDirectedGraph result,
                                          final Set<LongLongPair> edges, Arc arc) {
        for (String type : arc.target.receiverTypes) {
            // Find external node URI
            Map<String, String> typeMap = this.externalUris.get(type);
            if (typeMap != null) {
                String nodeURI = typeMap.get(arc.target.receiverSignature);

                if (nodeURI != null) {
                    // Find external node id
                    Long target = this.allUris.inverse().get(nodeURI);
                    if (target == null) {
                        // Allocate a global id to the external node
                        target = --this.externalGlobalIds;

                        // Add the external node to the graph if not already there
                        this.allUris.put(target, nodeURI);
                        result.addExternalNode(target);
                    }

                    edges.add(LongLongPair.of(arc.source, target));
                }
            }
        }
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
     *
     * @param arc        source, target and receivers information
     * @param signature  signature of the target
     * @param isCallback true, if a given arc is a callback
     */
    private boolean resolve(final Set<LongLongPair> edges,
                            final Arc arc,
                            final String signature,
                            final boolean isCallback) {

        // Cache frequently accessed variables
        final Map<String, LongSet> emptyMap = Collections.emptyMap();
        final LongSet emptyLongSet = LongSets.emptySet();
        Map<String, Map<String, LongSet>> typeDictionary = this.classHierarchy.getDefinedMethods();
        Map<String, List<String>> universalParents = this.classHierarchy.getUniversalParents();
        Map<String, List<String>> universalChildren = this.classHierarchy.getUniversalChildren();

        boolean resolved = false;

        for (String receiverTypeUri : arc.target.receiverTypes) {
            switch (arc.target.callType) {
                case VIRTUAL:
                case INTERFACE:
                    var foundTarget = false;

                    for (final var target : typeDictionary.getOrDefault(receiverTypeUri,
                        emptyMap).getOrDefault(signature, emptyLongSet)) {
                        addCall(edges, arc.source, target, isCallback);
                        resolved = true;
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
                                    resolved = true;
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
                                        addCall(edges, arc.source, target, isCallback);
                                        resolved = true;
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
                        resolved = true;
                    }
                    break;
            }
        }

        return resolved;
    }

    private ArrayList<String> getReceiver(final HashMap<String, Object> callSite) {
        return new ArrayList<>(Arrays.asList(((String) callSite.get(
            "receiver")).replace("[", "").replace("]", "").split(",")));
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
                addNode(result, longLongPair.firstLong(),
                    depGraph.isExternal(longLongPair.firstLong()));
                addNode(result, longLongPair.secondLong(),
                    depGraph.isExternal(longLongPair.secondLong()));
                result.addEdge(longLongPair.firstLong(), longLongPair.secondLong());
            }
        }
        logger.info("Number of Augmented nodes: {} edges: {}", numNode, result.numArcs());

        return result;
    }

    private void addNode(MergedDirectedGraph result, long node, boolean external) {
        if (external) {
            result.addExternalNode(node);
        } else {
            result.addInternalNode(node);
        }
    }

    /**
     * Add a resolved edge to the {@link DirectedGraph}.
     *
     * @param source     source callable ID
     * @param target     target callable ID
     * @param isCallback true, if a given arc is a callback
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
     * Get dependencies IDs from a metadata database.
     *
     * @param dbContext DSL context
     * @return set of IDs of dependencies
     */
    Set<Long> getDependenciesIds(final List<String> dependencySet,
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
