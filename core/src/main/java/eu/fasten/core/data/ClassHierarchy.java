/*
 * Copyright 2021 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.data;

import com.google.common.collect.BiMap;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.ModuleNames;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.plexus.util.CollectionUtils;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassHierarchy {
    private static final Logger logger = LoggerFactory.getLogger(ClassHierarchy.class);

    private final Map<String, Map<String, LongSet>> definedMethods;
    private final Map<String, List<String>> universalChildren;
    private final Map<String, List<String>> universalParents;
    private final Map<String, Map<String, LongSet>> abstractMethods;

    public Map<String, Map<String, LongSet>> getDefinedMethods() {
        return definedMethods;
    }

    public Map<String, List<String>> getUniversalChildren() {
        return universalChildren;
    }

    public Map<String, List<String>> getUniversalParents() {
        return universalParents;
    }

    private Map<Long, String> namespaceMap;

    private RocksDao rocksDao;

    public ClassHierarchy(final List<PartialJavaCallGraph> dependencySet,
                          final BiMap<Long, String> allUris) {
        final var childrenAndParents = createUniversalCHA(dependencySet);
        this.universalParents = childrenAndParents.getLeft();
        this.universalChildren = childrenAndParents.getRight();
        final var typeDictionary = createTypeDictionary(dependencySet, allUris);
        this.definedMethods = typeDictionary.getLeft();
        this.abstractMethods = typeDictionary.getRight();
    }

    public ClassHierarchy(final Set<Long> dependencySet, final DSLContext dbContext,
                          final RocksDao rocksDao) {
        this.rocksDao = rocksDao;
        final var universalCHA = createUniversalCHA(dependencySet, dbContext, rocksDao);
        this.universalChildren = new HashMap<>(universalCHA.getRight().size());
        universalCHA.getRight()
            .forEach((k, v) -> this.universalChildren.put(k, new ArrayList<>(v)));
        this.universalParents = new HashMap<>(universalCHA.getLeft().size());
        universalCHA.getLeft().forEach((k, v) -> this.universalParents.put(k, new ArrayList<>(v)));
        this.definedMethods = createTypeDictionary(dependencySet);
        this.abstractMethods = new HashMap<>();
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
     * Create a universal CHA for all dependencies including the artifact to resolve.
     *
     * @param dependencies dependencies including the artifact to resolve
     * @return universal CHA
     */
    private Pair<Map<String, List<String>>, Map<String, List<String>>> createUniversalCHA(
        final List<PartialJavaCallGraph> dependencies) {
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
     * Create a mapping from types and method signatures to callable IDs.
     *
     * @return a type dictionary
     */
    private Map<String, Map<String, LongSet>> createTypeDictionary(final Set<Long> dependencySet) {
        final long startTime = System.currentTimeMillis();
        var result = new HashMap<String, Map<String, LongSet>>();
        int noCGCounter = 0, noMetadaCounter = 0;
        for (Long dependencyId : dependencySet) {
            var cg = getGraphData(dependencyId);
            if (cg == null) {
                noCGCounter++;
                continue;
            }
            var metadata = rocksDao.getGraphMetadata(dependencyId, cg);
            if (metadata == null) {
                noMetadaCounter++;
                continue;
            }

            final var nodesData = metadata.gid2NodeMetadata;
            for (final var nodeId : nodesData.keySet()) {
                final var nodeData = nodesData.get(nodeId.longValue());
                final var typeUri = nodeData.type;
                final var signaturesMap = result.getOrDefault(typeUri, new HashMap<>());
                final var signature = nodeData.signature;
                final var signatureIds = signaturesMap.getOrDefault(signature,
                    new LongOpenHashSet());
                signatureIds.add(nodeId.longValue());
                signaturesMap.put(signature, signatureIds);
                result.put(typeUri, signaturesMap);
            }
        }
        logger.info("For {} dependencies failed to retrieve {} graph data and {} metadata " +
            "from rocks db.", dependencySet.size(), noCGCounter, noMetadaCounter);

        logger.info("Created the type dictionary with {} types in {} seconds", result.size(),
            new DecimalFormat("#0.000")
                .format((System.currentTimeMillis() - startTime) / 1000d));

        return result;
    }

    private DirectedGraph getGraphData(Long dependencyId) {
        DirectedGraph cg;
        try {
            cg = rocksDao.getGraphData(dependencyId);
        } catch (RocksDBException e) {
            throw new RuntimeException("An exception occurred retrieving CGs from rocks DB", e);
        }
        return cg;
    }

    private Pair<Map<String, Map<String, LongSet>>, Map<String, Map<String, LongSet>>> createTypeDictionary(
        final List<PartialJavaCallGraph> dependencySet,
        final BiMap<Long, String> globalUris) {

        Map<String, Map<String, LongSet>> definedResult = new HashMap<>();
        Map<String, Map<String, LongSet>> abstractResult = new HashMap<>();
        for (final var rcg : dependencySet) {
            final var localUris = rcg.mapOfFullURIStrings();
            for (final var type : rcg.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
                final var javaType = type.getValue();
                final var typeUri = type.getKey();

                final var definedMethods = javaType.getDefinedMethods().values();
                final var abstractMethods = CollectionUtils.subtract(javaType.getMethods().values(),
                    definedMethods);

                addMethodsToResult(definedMethods, javaType, globalUris, localUris, typeUri, definedResult);
                addMethodsToResult(abstractMethods, javaType, globalUris, localUris, typeUri, abstractResult);
            }
        }

        return Pair.of(definedResult, abstractResult);
    }

    private void addMethodsToResult(final Collection<JavaNode> methods, final JavaType type,
                                    final BiMap<Long, String> globalUris,
                                    final BiMap<Integer, String> localUris,
                                    final String typeUri,
                                    Map<String, Map<String, LongSet>> result) {
        for (final var method : methods) {
            final var id = nodeToGlobalId(type, method, globalUris, localUris);
            putMethods(result, id, typeUri, method);
        }
    }

    private Long nodeToGlobalId(final JavaType type, final JavaNode node,
                                final BiMap<Long, String> allUris, final BiMap<Integer, String> uris) {
        final var localId = type.getMethodKey(node);
        return allUris.inverse().get(uris.get(localId));
    }

    private void putMethods(Map<String, Map<String, LongSet>> result,
                            final long id, final String typeUri, final JavaNode node) {
        final var oldType = result.getOrDefault(typeUri, new HashMap<>());
        final var oldNode =
            oldType.getOrDefault(node.getSignature(), new LongOpenHashSet());
        oldNode.add(id);
        oldType.put(node.getSignature(), oldNode);
        result.put(typeUri, oldType);
    }

    public Map<String, Map<String, LongSet>> getAbstractMethods() {
        return abstractMethods;
    }
}
