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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
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
import static java.util.Collections.emptyList;

public class CGMerger {

    private static final Logger logger = LoggerFactory.getLogger(CGMerger.class);

    private final Map<String, List<String>> universalChildren;
    private Map<String, Map<String, Set<Long>>> typeDictionary;
    private DSLContext dbContext;
    private RocksDao rocksDao;
    private Set<Long> dependencySet;
    private Map<Long, String> namespaceMap;


    private Map<String, List<String>> universalParents;
    private List<ExtendedRevisionJavaCallGraph> ercgDependencySet;
    private BiMap<Long, String> allUris;
    private Map<String, List<ExtendedRevisionJavaCallGraph>> ercgTypeDictionary;


    public BiMap<Long, String> getAllUris() {
        return this.allUris;
    }

    /**
     * Creates instance of local merger.
     *
     * @param dependencySet all artifacts present in a resolution
     */
    public CGMerger(final List<ExtendedRevisionJavaCallGraph> dependencySet) {

        final var UCH = createUniversalCHA(dependencySet);
        this.universalParents = UCH.getLeft();
        this.universalChildren = UCH.getRight();
        this.ercgTypeDictionary = createTypeDictionary(dependencySet);
        this.ercgDependencySet = dependencySet;
        this.allUris = HashBiMap.create();
    }

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

    private void processArc(final Map<String, List<String>> universalParents,
                            final Map<String, List<String>> universalChildren,
                            final Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                            final CGHA result,
                            final Int2ObjectMap<JavaType> externalNodeIdToTypeMap,
                            final Int2ObjectMap<JavaType> internalNodeIdToTypeMap,
                            final Map.Entry<IntIntPair, Map<Object, Object>> arc) {
        final var targetKey = arc.getKey().secondInt();
        final var sourceKey = arc.getKey().firstInt();

        boolean isCallBack = false;
        int nodeKey = targetKey;
        JavaType type = null;
        if (internalNodeIdToTypeMap.containsKey(targetKey)) {
            type = internalNodeIdToTypeMap.get(targetKey);
        }
        if (externalNodeIdToTypeMap.containsKey(targetKey)) {
            type = externalNodeIdToTypeMap.get(targetKey);
        }
        if (externalNodeIdToTypeMap.containsKey(sourceKey)) {
            type = externalNodeIdToTypeMap.get(sourceKey);
            isCallBack = true;
            nodeKey = sourceKey;
        }
        if (type != null) {
            resolve(universalParents, universalChildren, typeDictionary, result, arc,
                    nodeKey, type, isCallBack);
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

                resolveDynamics(universalChildren, universalParents, typeDictionary, result,
                        isCallback, call, receiverTypeUris);

            } else if (callSite.get("type").equals("invokedynamic")) {
                logger.warn("OPAL didn're rewrite the invokedynamic");
            } else {

                resolveReceiverType(typeDictionary, result, isCallback, call, receiverTypeUris);
            }
        }
    }

    private ArrayList<String> getReceiver(final HashMap<String, Object> callSite) {
        return new ArrayList<>(Arrays.asList(((String) callSite.get(
                "receiver")).replace("[", "").replace("]", "").split(",")));
    }

    private void resolveDynamics(final Map<String, List<String>> universalChildren,
                                 final Map<String, List<String>> universalParents,
                                 final Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                                 final CGHA result, final boolean isCallback, final Call call,
                                 final ArrayList<String> receiverTypeUris) {

        boolean foundTarget = false;
        for (final var receiverTypeUri : receiverTypeUris) {
            foundTarget =
                    findTargets(typeDictionary, result, isCallback, call, foundTarget,
                            receiverTypeUri);
            if (!foundTarget) {
                for (String depTypeUri : universalParents.getOrDefault(receiverTypeUri, emptyList())) {
                    if (findTargets(typeDictionary, result, isCallback, call, foundTarget,
                            depTypeUri)) {
                        break;
                    }
                }
            }
            if (!foundTarget) {
                final var types = universalChildren.getOrDefault(receiverTypeUri, emptyList());
                for (final var depTypeUri : types) {
                    findTargets(typeDictionary, result, isCallback, call, foundTarget, depTypeUri);
                }
            }
        }
    }

    private boolean findTargets(Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                                CGHA result, boolean isCallback,
                                Call call, boolean foundTarget,
                                String depTypeUri) {
        for (final var dep : typeDictionary
                .getOrDefault(depTypeUri, emptyList())) {

            foundTarget = resolveToDynamics(result, call, dep.getClassHierarchy().get(JavaScope.internalTypes)
                    .get(depTypeUri), dep.productVersion, depTypeUri, isCallback, foundTarget);
        }
        return foundTarget;
    }

    private boolean resolveToDynamics(final CGHA cgha, final Call call,
                                      final JavaType type,
                                      final String product, final String depTypeUri,
                                      boolean isCallback, boolean foundTarget) {
        final var node = type.getDefinedMethods().get(call.target.getSignature());
        if (node != null) {
            addEdge(cgha, new Call(call, node), product, type, depTypeUri, isCallback);
            return true;
        }
        return foundTarget;
    }

    private void resolveReceiverType(final Map<String, List<ExtendedRevisionJavaCallGraph>> typeDictionary,
                                     final CGHA result, final boolean isCallback,
                                     final Call call, final List<String> receiverTypeUris) {
        for (final var receiverTypeUri : receiverTypeUris) {
            for (final var dep : typeDictionary
                    .getOrDefault(receiverTypeUri, emptyList())) {

                resolveIfDefined(result, call, dep.getClassHierarchy()
                                .get(JavaScope.internalTypes)
                                .get(receiverTypeUri), dep.productVersion,
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
        synchronized (type) {
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
                .graph(new JavaGraph(artifact.getGraph().getCallSites(),
                        new HashMap<>(),
                        result.graph))
                .nodeCount(result.nodeCount.get())
                .build();


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

    private void addEdge(final FastenDefaultDirectedGraph result,
                         final long source, final long target) {
        result.addInternalNode(source);
        result.addInternalNode(target);
        result.addEdge(source, target);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Create instance of database merger from package names.
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
        this.typeDictionary = createTypeDictionary(this.dependencySet, dbContext, rocksDao);
    }

    /**
     * Create instance of database merger from package versions ids.
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
        this.typeDictionary = createTypeDictionary(dependencySet, dbContext, rocksDao);

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
            this.signature = StringUtils.substringAfter(uri.getEntity(), ".");
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
        for (String receiverTypeUri : arc.target.receiverTypes) {
            var type = arc.target.callType.toString();
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


    public ExtendedRevisionJavaCallGraph mergeWithCHA(final ExtendedRevisionJavaCallGraph artifact) {
        final var result = new CGHA(artifact);

        final var externalNodeIdToTypeMap = artifact.externalNodeIdToTypeMap();
        final var internalNodeIdToTypeMap = artifact.internalNodeIdToTypeMap();

        artifact.getGraph().getCallSites().entrySet().parallelStream().forEach(arc ->
                processArc(universalParents, universalChildren, ercgTypeDictionary, result,
                        externalNodeIdToTypeMap, internalNodeIdToTypeMap, arc));
        return buildRCG(artifact, result);
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
        if (this.dbContext == null) {
            final var result = new FastenDefaultDirectedGraph();
            for (final var dep : this.ercgDependencySet) {
                addThisMergeToResult(result, mergeWithCHA(dep));
            }
            return result;
        } else {
            List<DirectedGraph> depGraphs = new ArrayList<>();
            for (final var dep : this.dependencySet) {
                var merged = mergeWithCHA(dep);
                if (merged != null) {
                    depGraphs.add(merged);
                }
            }
            return augmentGraphs(depGraphs);
        }
    }
}
