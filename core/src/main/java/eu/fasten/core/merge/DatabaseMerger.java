package eu.fasten.core.merge;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseMerger {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMerger.class);

    private final DSLContext dbContext;
    private final RocksDao rocksDao;
    private final String artifact;
    private final List<String> dependencies;

    /**
     * Create instance of database merger.
     *
     * @param artifact     artifact to resolve
     * @param dependencies dependencies of the artifact
     * @param dbContext    DSL context
     * @param rocksDao     rocks DAO
     */
    public DatabaseMerger(final String artifact, final List<String> dependencies,
                          final DSLContext dbContext, final RocksDao rocksDao) {
        this.dbContext = dbContext;
        this.rocksDao = rocksDao;
        this.artifact = artifact;
        this.dependencies = dependencies;
    }

    /**
     * Single arc containing source and target IDs and a list of receivers.
     */
    private static class Arc {
        private final Long source;
        private final Long target;
        private final ReceiverRecord[] receivers;

        /**
         * Create new Arc instance.
         *
         * @param source    source ID
         * @param target    target ID
         * @param receivers list of receivers
         */
        public Arc(final Long source, final Long target, final ReceiverRecord[] receivers) {
            this.source = source;
            this.target = target;
            this.receivers = receivers;
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
        public Node(final String uri) {
            this.typeUri = uri.split("\\.[^.]*\\(")[0];
            this.signature = uri.replace(this.typeUri + ".", "");
        }

        /**
         * Check if the given method is a constructor.
         *
         * @return true, if the method is constructor
         */
        public boolean isConstructor() {
            return signature.startsWith("%3Cinit%3E");
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
     * Merges a call graph with its dependencies using CHA algorithm.
     *
     * @return merged call graph
     */
    public DirectedGraph mergeWithCHA() {
        var result = new ArrayImmutableDirectedGraph.Builder();

        final var dependenciesIds = getDependenciesIds();
        final var universalCHA = createUniversalCHA(dependenciesIds);
        final var typeDictionary = createTypeDictionary(dependenciesIds);

        final var callGraphData = fetchCallGraphData();
        final var typeMap = createTypeMap(callGraphData);
        final var arcs = getArcs(callGraphData);

        final long startTime = System.currentTimeMillis();
        for (final var arc : arcs) {
            if (callGraphData.isExternal(arc.target)) {
                resolve(result, arc, typeMap.get(arc.target), universalCHA, typeDictionary, false);
            } else {
                resolve(result, arc, typeMap.get(arc.source), universalCHA, typeDictionary,
                        callGraphData.isExternal(arc.source));
            }
        }
        logger.info("Stitched in {} seconds", new DecimalFormat("#0.000")
                .format((System.currentTimeMillis() - startTime) / 1000d));
        return result.build();
    }

    /**
     * Resolve an external call.
     *
     * @param result         graph with resolved calls
     * @param arc            source, target and receivers information
     * @param node           type and method information
     * @param universalCHA   universal class hierarchy of all dependencies
     * @param typeDictionary Mapping of types and method signatures to their global IDs
     * @param isCallback     true, if a given arc is a callback
     */
    private void resolve(final ArrayImmutableDirectedGraph.Builder result,
                         final Arc arc,
                         final Node node,
                         final Graph<String, DefaultEdge> universalCHA,
                         final Map<String, Map<String, Set<Long>>> typeDictionary,
                         final boolean isCallback) {
        if (node.isConstructor()) {
            resolveClassInits(result, arc, node, universalCHA, typeDictionary, isCallback);
        }

        for (var entry : arc.receivers) {
            var receiverTypeUri = entry.component3();
            var type = entry.component2().getLiteral();
            if (type.equals("virtual") || type.equals("interface") || type.equals("dynamic")) {
                final var types = new ArrayList<>(Collections.singletonList(receiverTypeUri));
                if (universalCHA.containsVertex(receiverTypeUri)) {
                    types.addAll(Graphs.successorListOf(universalCHA, receiverTypeUri));
                }
                //System.out.print("DYNAMIC: " + arc.source + " -> " + arc.target + " :: ");
                for (final var depTypeUri : types) {
                    for (final var target : typeDictionary.getOrDefault(depTypeUri,
                            new HashMap<>()).getOrDefault(node.signature, new HashSet<>())) {
                        addEdge(result, arc.source, target, isCallback);
                        //System.out.print(target + " ");
                    }
                }
                //System.out.println();
            } else {
                //System.out.print("DEFINED: " + arc.source + " -> " + arc.target + " :: ");
                for (final var target : typeDictionary.getOrDefault(receiverTypeUri,
                        new HashMap<>()).getOrDefault(node.signature, new HashSet<>())) {
                    addEdge(result, arc.source, target, isCallback);
                    //System.out.print(target + " ");
                }
                //System.out.println();
            }

        }
    }

    /**
     * Resolves constructors.
     *
     * @param result         {@link DirectedGraph} with resolved calls
     * @param arc            source, target and receivers information
     * @param node           type and method information
     * @param universalCHA   universal class hierarchy of all dependencies
     * @param typeDictionary Mapping of types and method signatures to their global IDs
     * @param isCallback     true, if a given arc is a callback
     */
    private void resolveClassInits(final ArrayImmutableDirectedGraph.Builder result,
                                   final Arc arc,
                                   final Node node,
                                   final Graph<String, DefaultEdge> universalCHA,
                                   final Map<String, Map<String, Set<Long>>> typeDictionary,
                                   final boolean isCallback) {
        final var typeList = new ArrayList<>(Collections.singletonList(node.typeUri));
        if (universalCHA.containsVertex(node.typeUri)) {
            typeList.addAll(Graphs.predecessorListOf(universalCHA, node.typeUri));
        }

        //System.out.print("CONSTRUCTOR: " + arc.source + " -> " + arc.target + " :: ");
        for (final var superTypeUri : typeList) {
            var superSignature = node.signature.replace("%3Cinit%3E", "%3Cclinit%3E");
            for (final var target : typeDictionary.getOrDefault(superTypeUri,
                    new HashMap<>()).getOrDefault(superSignature, new HashSet<>())) {
                addEdge(result, arc.source, target, isCallback);
                //System.out.print(target + " ");
            }
        }
        //System.out.println();
    }

    /**
     * Retrieve external calls and constructor calls from a call graph
     *
     * @param callGraphData call graph
     * @return list of external and constructor calls
     */
    private List<Arc> getArcs(final DirectedGraph callGraphData) {
        var arcsList = new ArrayList<Arc>();
        Condition arcsCondition = null;
        for (var source : callGraphData.nodes()) {
            for (var target : callGraphData.successors(source)) {
                if (callGraphData.isExternal(target)
                        || callGraphData.isExternal(source)
                        || source.equals(target)) {
                    if (arcsCondition == null) {
                        arcsCondition = Edges.EDGES.SOURCE_ID.eq(source)
                                .and(Edges.EDGES.TARGET_ID.eq(target));
                    } else {
                        arcsCondition = arcsCondition.or(Edges.EDGES.SOURCE_ID.eq(source)
                                .and(Edges.EDGES.TARGET_ID.eq(target)));
                    }
                }
            }
        }
        dbContext.select(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                .from(Edges.EDGES)
                .where(arcsCondition)
                .fetch()
                .forEach(arc -> arcsList.add(new Arc(arc.value1(), arc.value2(), arc.value3())));
        return arcsList;
    }

    /**
     * Retrieve a call graph from a graph database given a maven coordinate.
     *
     * @return call graph
     */
    private DirectedGraph fetchCallGraphData() {
        var package_name = artifact.split(":")[0] + ":" + artifact.split(":")[1];
        var version = artifact.split(":")[2];

        var artifact_id = dbContext
                .select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                .and(Packages.PACKAGES.PACKAGE_NAME.eq(package_name))
                .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetchOne()
                .component1();

        DirectedGraph callGraphData = null;
        try {
            callGraphData = rocksDao.getGraphData(artifact_id);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }

        return callGraphData;
    }

    /**
     * Create a mapping from callable IDs to {@link Node}.
     *
     * @param callGraphData call graph
     * @return a map of callable IDs and {@link Node}
     */
    private Map<Long, Node> createTypeMap(final DirectedGraph callGraphData) {
        var typeMap = new HashMap<Long, Node>();
        var nodesIds = callGraphData.nodes();
        var callables = dbContext
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(nodesIds))
                .fetch();

        callables.forEach(callable -> typeMap.put(callable.value1(), new Node(callable.value2())));
        return typeMap;
    }

    /**
     * Create a mapping from types and method signatures to callable IDs
     *
     * @param dependenciesIds IDs of dependencies
     * @return a type dictionary
     */
    private Map<String, Map<String, Set<Long>>> createTypeDictionary(final Set<Long> dependenciesIds) {
        var result = new HashMap<String, Map<String, Set<Long>>>();

        var callables = getCallables(dependenciesIds);

        dbContext.select(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(callables))
                .fetch()
                .forEach(callable -> {
                    var typeUri = callable.value1().split("\\.[^.]*\\(")[0];
                    var signature = callable.value1().replace(typeUri + ".", "");
                    result.putIfAbsent(typeUri, new HashMap<>());
                    var type = result.get(typeUri);
                    var newestSet = new HashSet<Long>();
                    newestSet.add(callable.value2());
                    type.merge(signature, newestSet, (old, newest) -> {
                        old.addAll(newest);
                        return old;
                    });
                });

        logger.info("Created the type dictionary with {} types", result.size());

        return result;
    }

    /**
     * Create a universal class hierarchy from all dependencies.
     *
     * @param dependenciesIds IDs of dependencies
     * @return universal CHA
     */
    private Graph<String, DefaultEdge> createUniversalCHA(final Set<Long> dependenciesIds) {
        var universalCHA = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);

        var callables = getCallables(dependenciesIds);

        var modulesIds = dbContext
                .select(Callables.CALLABLES.MODULE_ID)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(callables))
                .fetch();

        var modules = dbContext
                .select(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA)
                .from(Modules.MODULES)
                .where(Modules.MODULES.ID.in(modulesIds))
                .fetch();

        for (var callable : modules) {
            if (!universalCHA.containsVertex(callable.value1())) {
                universalCHA.addVertex(callable.value1());
            }

            var superClasses = new JSONObject(callable.value2().data())
                    .getJSONArray("superClasses").toList();
            var superInterfaces = new JSONObject(callable.value2().data())
                    .getJSONArray("superInterfaces").toList();
            addSuperTypes(universalCHA, callable.value1(), superClasses);
            addSuperTypes(universalCHA, callable.value1(), superInterfaces);
        }

        logger.info("Created the Universal CHA with {} vertices", universalCHA.vertexSet().size());

        return universalCHA;
    }

    /**
     * Get callables from dependencies.
     *
     * @param dependenciesIds dependencies IDs
     * @return list of callables
     */
    private List<Long> getCallables(final Set<Long> dependenciesIds) {
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
     * @return set of IDs of dependencies
     */
    private Set<Long> getDependenciesIds() {
        var coordinates = new HashSet<>(dependencies);
        coordinates.add(artifact);

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
                               final List<Object> targetTypes) {
        for (final var superClass : targetTypes) {
            if (!result.containsVertex((String) superClass)) {
                result.addVertex((String) superClass);
            }
            if (!result.containsEdge(sourceType, (String) superClass)) {
                result.addEdge((String) superClass, sourceType);
            }
        }
    }

    /**
     * Add a resolved edge to the {@link DirectedGraph}.
     *
     * @param result     graph with resolved calls
     * @param source     source callable ID
     * @param target     target callable ID
     * @param isCallback true, if a given arc is a callback
     */
    private void addEdge(final ArrayImmutableDirectedGraph.Builder result,
                         final Long source, final Long target, final boolean isCallback) {
        try {
            result.addExternalNode(source);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            result.addExternalNode(target);
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
