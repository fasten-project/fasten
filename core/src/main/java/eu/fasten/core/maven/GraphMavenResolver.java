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

package eu.fasten.core.maven;

import static eu.fasten.core.maven.utils.DependencyGraphUtilities.buildDependencyGraphFromScratch;
import static eu.fasten.core.maven.utils.DependencyGraphUtilities.doesDependencyGraphExist;
import static eu.fasten.core.maven.utils.DependencyGraphUtilities.loadDependencyGraph;

import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.math3.util.Pair;
import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.maven.data.MavenProduct;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.graph.MavenEdge;
import eu.fasten.core.maven.graph.MavenGraph;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

public class GraphMavenResolver {

    private static final Logger logger = LoggerFactory.getLogger(GraphMavenResolver.class);

    private boolean ignoreMissing = false;

    public static MavenGraph graph;

    public GraphMavenResolver(DSLContext dbContext, String serializedPath) throws Exception {
        if (doesDependencyGraphExist(serializedPath)) {
            graph = loadDependencyGraph(serializedPath);
        } else {
            graph = buildDependencyGraphFromScratch(dbContext, serializedPath);
        }
    }

    public boolean getIgnoreMissing() {
        return ignoreMissing;
    }

    public void setIgnoreMissing(boolean ignoreMissing) {
        this.ignoreMissing = ignoreMissing;
    }

    /**
     * Performs a BFS on the dependency graph to resolve the dependencies of the provided {@link Revision}, as specified
     * by the provided revision details.
     *
     * @return The (transitive) dependency set
     */
    public Set<Revision> resolveDependencies(String groupId, String artifactId, String version, long timestamp,
                                                                 DSLContext db, boolean transitive) {

        if (timestamp == -1) {
            // TODO why not "now"?
            var ts = getCreatedAt(groupId, artifactId, version, db);
            if (ts > 0) {
                timestamp = ts;
            }
        }

        var resultTriples = new ArrayList<Triple<Set<Revision>, List<Pair<Revision, MavenProduct>>, Map<Revision, Revision>>>();

        Set<Revision> allDeps = new ObjectLinkedOpenHashSet<>();
        var exclusions = new ArrayList<Pair<Revision, MavenProduct>>();
        var descendants = new HashMap<Revision, Revision>();

        resultTriples.add(dependencyBFS(groupId, artifactId, version, timestamp, transitive));
        for (var triple : resultTriples) {
            allDeps.addAll(triple.getLeft());
            exclusions.addAll(triple.getMiddle());
            descendants.putAll(triple.getRight());
        }

        return filterDependenciesByExclusions(allDeps, exclusions, descendants);
    }

    /**
     * Resolves the dependencies of the provided {@link Revision}, according to the Maven dependency resolution
     * rules. The timestamp of the provided revision is used to determine the nodes to be included in the dependency
     * set. The first
     *
     * @return The (transitive) dependency set
     */
    public Set<Revision> resolveDependencies(Revision r, DSLContext db, boolean transitive) {
        return resolveDependencies(r.groupId, r.artifactId, r.version.toString(),
                r.createdAt.getTime(), db, transitive);
    }


    public Triple<Set<Revision>, List<Pair<Revision, MavenProduct>>, Map<Revision, Revision>>
    dependencyBFS(String groupId, String artifactId, String version, long timestamp, boolean transitive) {
        assert (timestamp > 0);
        var startTS = System.currentTimeMillis();
        logger.debug("BFS from root: {}:{}:{}", groupId, artifactId, version);

        var excludeProducts = new ArrayList<Pair<Revision, MavenProduct>>();
        var artifact = new Revision(groupId, artifactId, version, new Timestamp(timestamp));
        
        if (!graph.containsVertex(artifact)) {
            
            System.out.printf("Now: %d\n", new Date().getTime());
            System.out.printf("Looking for: %s\n", artifact.toJSON());
            System.out.println("Vertices:");
            for(var r : graph.vertexSet()) {
                if("org.apache.commons".equals(r.groupId)) {
                    System.out.println(r.toJSON());
                }
            }
            throw new RuntimeException("Revision " + artifact + " is not in the dependency graph.");
        }
        var edges = graph.dependencyEdges(artifact);
        for (var exclusionEdge : edges.stream().filter(e -> !e.exclusions.isEmpty()).collect(Collectors.toList())) {
            for (var exclusion : exclusionEdge.exclusions) {
                var product = new MavenProduct(exclusion.groupId, exclusion.artifactId);
                excludeProducts.add(new Pair<>(exclusionEdge.source, product));
            }
        }
        var descendantsMap = new HashMap<Revision, Revision>();
        edges.forEach(e -> descendantsMap.put(e.target, e.source));

        var successors = graph.directDependencies(artifact);
        var workQueue = filterDependenciesByTimestamp(successors, timestamp).stream().
                map(x -> new Pair<>(x, 1)).collect(Collectors.toCollection(ArrayDeque::new));

        logger.debug("Obtaining first level successors: {} items, {} ms", workQueue.size(),
                msSince(startTS));
        var result = new ObjectLinkedOpenHashSet<Revision>();
        workQueue.stream().map(Pair::getFirst).forEachOrdered(result::add);

        if (!transitive) {
            return new ImmutableTriple<>(result, excludeProducts, descendantsMap);
        }

        var depthRevisions = new ArrayList<>(workQueue);

        while (!workQueue.isEmpty()) {
            var rev = workQueue.poll();
            result.add(rev.getFirst());
            depthRevisions.add(rev);
            if (!graph.containsVertex(rev.getFirst())) {
                if (ignoreMissing) {
                    continue;
                }
                throw new RuntimeException("Revision " + rev.getFirst() + " is not in the dependency graph");
            }
            var outgoingEdges = new ObjectLinkedOpenHashSet<>(graph.dependencyEdges(rev.getFirst()));
            for (var exclusionEdge : outgoingEdges.stream().filter(e -> !e.exclusions.isEmpty()).collect(Collectors.toList())) {
                for (var exclusion : exclusionEdge.exclusions) {
                    var product = new MavenProduct(exclusion.groupId, exclusion.artifactId);
                    excludeProducts.add(new Pair<>(exclusionEdge.source, product));
                }
            }
            outgoingEdges.forEach(e -> descendantsMap.put(e.target, e.source));
            var filteredSuccessors = filterOptionalSuccessors(outgoingEdges)
                    .stream().map(e -> e.target).collect(Collectors.toList());
            var dependencies = filterDependenciesByTimestamp(filteredSuccessors, timestamp);
            for (var dependency : dependencies) {
                if (!result.contains(dependency)) {
                    workQueue.add(new Pair<>(dependency, rev.getSecond() + 1));
                }
            }
            logger.debug("Successors for {}:{}:{}: deps: {}, depth: {}, queue: {} items",
                    rev.getFirst().groupId, rev.getFirst().artifactId, rev.getFirst().version,
                    dependencies.size(), rev.getSecond() + 1, workQueue.size());
        }
        logger.debug("BFS finished: {} successors", depthRevisions.size());

        var depSet = resolveConflicts(new ObjectLinkedOpenHashSet<>(depthRevisions));

        logger.debug("Resolved version conflicts, now filtering exclusions");
        return new ImmutableTriple<>(depSet, excludeProducts, descendantsMap);
    }

    private long msSince(long start) {
        return System.currentTimeMillis() - start;
    }

    /**
     * Resolves the dependents of the provided {@link Revision}, as specified by the provided revision details. The
     * provided timestamp determines which nodes will be ignored when traversing dependent nodes. Effectively, the
     * returned dependent set only includes nodes that where released AFTER the provided timestamp.
     */
    public ObjectLinkedOpenHashSet<Revision> resolveDependents(String groupId, String artifactId, String version, long timestamp,
                                                               boolean transitive) {
        return dependentBFS(groupId, artifactId, version, timestamp, transitive);
    }

    /**
     * Resolves the dependents of the provided {@link Revision}. The release timestamp of the provided revision is
     * used to determine which nodes will be ignored when traversing dependent nodes. Effectively, the returned
     * dependent set only includes nodes that where released AFTER the provided revision.
     */
    public ObjectLinkedOpenHashSet<Revision> resolveDependents(Revision r, boolean transitive) {
        return dependentBFS(r.groupId, r.artifactId, r.version.toString(), r.createdAt.getTime(), transitive);
    }

    /**
     * Performs a Breadth-First Search on the {@param dependentGraph} to determine the revisions that depend on
     * the revision indicated by the first 3 parameters, at the indicated {@param timestamp}.
     *
     * @param timestamp  - The cut-off timestamp. The returned dependents have been released after the provided timestamp
     * @param transitive - Whether the BFS should recurse into the graph
     */
    public ObjectLinkedOpenHashSet<Revision> dependentBFS(String groupId, String artifactId, String version, long timestamp,
                                                          boolean transitive) {
        var artifact = new Revision(groupId, artifactId, version, new Timestamp(timestamp));

        if (!graph.containsVertex(artifact)) {
            System.out.printf("Now: %d\n", new Date().getTime());
            System.out.printf("Looking for: %s\n", artifact.toJSON());
            System.out.println("Vertices:");
            for(var r : graph.vertexSet()) {
                if("org.apache.commons".equals(r.groupId)) {
                    System.out.println(r.toJSON());
                }
            }
             throw new RuntimeException("Revision " + artifact + " is not in the dependents graph.");
        }

        var workQueue = new ArrayDeque<>(filterDependentsByTimestamp(graph.directDependents(artifact), timestamp));

        var result = new ObjectLinkedOpenHashSet<>(workQueue);

        if (!transitive) {
            return new ObjectLinkedOpenHashSet<>(workQueue);
        }

        while (!workQueue.isEmpty()) {
            var rev = workQueue.poll();
            if (rev != null) {
                result.add(rev);
                logger.debug("Successors for {}:{}:{}: deps: {}, queue: {} items",
                        rev.groupId, rev.artifactId, rev.version,
                        workQueue.size(), workQueue.size());
            }
            if (!graph.containsVertex(rev)) {
                if (ignoreMissing) {
                    continue;
                } else {
                    System.out.printf("Now: %d\n", new Date().getTime());
                    System.out.printf("Looking for: %s\n", artifact.toJSON());
                    System.out.println("Vertices:");
                    for(var r : graph.vertexSet()) {
                        if("org.apache.commons".equals(r.groupId)) {
                            System.out.println(r.toJSON());
                        }
                    }
                        throw new RuntimeException("Revision " + rev + " is not in the dependents graph.");
                }
            }
            var dependents = filterDependentsByTimestamp(graph.directDependents(artifact), timestamp);
            for (var dependent : dependents) {
                if (!result.contains(dependent)) {
                    workQueue.add(dependent);
                }
            }
        }
        return result;
    }

    public static ObjectLinkedOpenHashSet<Revision> filterDependenciesByExclusions(Set<Revision> dependencies,
                                                                            List<Pair<Revision, MavenProduct>> exclusions,
                                                                            Map<Revision, Revision> descendantsMap) {
        var finalSet = new ObjectLinkedOpenHashSet<>(dependencies);
        var dependenciesByProduct = dependencies.stream().collect(Collectors.groupingBy(Revision::product));
        for (var excludeProduct : exclusions) {
            if (!dependenciesByProduct.containsKey(excludeProduct.getSecond())) {
                continue;
            }
            for (var dep : dependenciesByProduct.get(excludeProduct.getSecond())) {
                if (dep.product().equals(excludeProduct.getSecond()) && isDescendantOf(dep, excludeProduct.getFirst(), descendantsMap)) {
                    finalSet.remove(dep);
                }
            }
        }
        return finalSet;
    }

    public static boolean isDescendantOf(Revision child, Revision parent, Map<Revision, Revision> descendants) {
        var visited = new ObjectLinkedOpenHashSet<Revision>();
        while (child != null && !Objects.equals(child, parent)) {
            if (!visited.contains(child)) {
                visited.add(child);
                child = descendants.get(child);
            } else {
                break;
            }
        }
        return Objects.equals(child, parent);
    }

    /**
     * Given a set of n successors for a revision r which are different revisions of the same product, select the
     * revisions that are closest to the release timestamp of r.
     *
     * @return A list of unique revisions per unique product in the input list.
     */
    protected static List<Revision> filterDependenciesByTimestamp(List<Revision> successors, long timestamp) {
        return successors.stream().
                collect(Collectors.groupingBy(Revision::product)).
                values().stream().
                map(revisions -> {
                    var latestTimestamp = -1L;

                    Revision latest = null;
                    for (var r : revisions) {
                        try {
                        if (r.createdAt.getTime() <= timestamp && r.createdAt.getTime() > latestTimestamp) {
                            latestTimestamp = r.createdAt.getTime();
                            latest = r;
                        }
                        } catch (NullPointerException e) {
                            throw e;
                        }
                    }
                    if (revisions.size() > 1)
                        logger.debug("Ignored {} revisions for product {}, selected: {}, timestamp: {}",
                                revisions.size() - 1, revisions.get(0).product(), latest, timestamp);
                    return latest;
                }).
                filter(Objects::nonNull).
                collect(Collectors.toList());
    }

    protected static List<Revision> filterDependentsByTimestamp(List<Revision> successors, long timestamp) {
        try {
        return successors.stream().
                filter(revision -> revision.createdAt.getTime() >= timestamp).
                collect(Collectors.toList());
        } catch (NullPointerException e) {
            throw e;
        }
    }

    protected static Set<MavenEdge> filterOptionalSuccessors(Set<MavenEdge> outgoingEdges) {
        var result = new ObjectLinkedOpenHashSet<MavenEdge>();
        outgoingEdges.stream()
                .filter(edge -> !edge.optional)
                .forEachOrdered(result::add);
        return result;
    }

    protected static Set<MavenEdge> filterSuccessorsByScope(Set<MavenEdge> outgoingEdges, List<String> allowedScopes) {
        var result = new ObjectLinkedOpenHashSet<MavenEdge>();
        outgoingEdges.stream()
                .filter(edge -> {
                    var scope = edge.scope;
                    if (scope == null || scope.isEmpty()) {
                        scope = "compile";
                    }
                    return allowedScopes.contains(scope);
                }).forEachOrdered(result::add);
        return result;
    }

    protected static Set<MavenEdge> filterSuccessorsByType(Set<MavenEdge> outgoingEdges, List<String> allowedTypes) {
        var result = new ObjectLinkedOpenHashSet<MavenEdge>();
        outgoingEdges.stream()
                .filter(edge -> {
                    var type = edge.type;
                    if (type == null || type.isEmpty()) {
                        type = "jar";
                    }
                    return allowedTypes.contains(type);
                }).forEachOrdered(result::add);
        return result;
    }

    /**
     * Resolve conflicts (duplicate products with different versions) by picking revisions that are closer to the root.
     */
    protected static Set<Revision> resolveConflicts(Set<Pair<Revision, Integer>> depthRevisions) {
        var result = new ObjectLinkedOpenHashSet<Revision>();
        depthRevisions.stream().collect(Collectors.toMap(
                x -> x.getFirst().product(),
                y -> y,
                (x, y) -> {
                    if (x.getFirst().equals(y.getFirst())) return x;

                    if (x.getSecond() < y.getSecond()) {
                        logger.debug("Conflict resolution. Select: {}, distance: {}. Ignore: {}, distance: {}",
                                x.getFirst(), x.getSecond(), y.getFirst(), y.getSecond());
                        return x;
                    } else {
                        logger.debug("Conflict resolution. Select: {}, distance: {}. Ignore: {}, distance: {}",
                                y.getFirst(), y.getSecond(), x.getFirst(), x.getSecond());
                        return y;
                    }
                })).values().stream().map(Pair::getFirst).forEachOrdered(result::add);
        return result;
    }

    private long getCreatedAt(String groupId, String artifactId, String version, DSLContext context) {
        var packageName = groupId + Constants.mvnCoordinateSeparator + artifactId;
        var result = context.select(PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
                .from(PackageVersions.PACKAGE_VERSIONS)
                .join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                .fetchOne();

        if (result == null || result.component1() == null) {
            return -1;
        }
        return result.component1().getTime();
    }

    public Set<Revision> findAllRevisionsInThePath(Revision source, Revision target) {
        var paths = getPaths(graph, source, target, new ObjectLinkedOpenHashSet<>(), new ArrayList<>(), new ArrayList<>());
        var pathsNodes = new ObjectLinkedOpenHashSet<Revision>();
        for (var path : paths) {
            pathsNodes.addAll(path);
        }
        return pathsNodes;
    }

    private List<List<Revision>> getPaths(MavenGraph graph, Revision source, Revision target,
                                          Set<Revision> visited, List<Revision> path, List<List<Revision>> vulnerablePaths) {
        if (path.isEmpty()) {
            path.add(source);
        }
        if (source == target) {
            vulnerablePaths.add(new ArrayList<>(path));
            return vulnerablePaths;
        }
        visited.add(source);
        for (var edge : graph.dependencyEdges(source)) {
            var node = edge.target;
            if (!visited.contains(node)) {
                path.add(node);
                getPaths(graph, node, target, visited, path, vulnerablePaths);
                path.remove(node);
            }
        }
        visited.remove(source);
        return vulnerablePaths;
    }

    public Set<Revision>  resolveDependencies(Set<Revision> revisions, DSLContext dbContext, boolean transitive) {
        var virtualNode = addVirtualNode(new ObjectLinkedOpenHashSet<>(revisions));
        var deps = resolveDependencies(virtualNode, dbContext, transitive);
        // TODO handle crashes
        removeVirtualNode(virtualNode);
        return deps;
    }

    private Revision addVirtualNode(ObjectLinkedOpenHashSet<Revision> directDependencies) {
        var nodeGroup = String.valueOf(directDependencies.stream().reduce(0, (x, r) -> x + r.groupId.hashCode(), Integer::sum));
        var nodeArtifact = String.valueOf(directDependencies.stream().reduce(0, (x, r) -> x + r.artifactId.hashCode(), Integer::sum));
        var nodeVersion = String.valueOf(directDependencies.stream().reduce(0, (x, r) -> x + r.version.hashCode(), Integer::sum));
        var vnode = new Revision(-1, nodeGroup, nodeArtifact, nodeVersion, new Timestamp(-1));
        graph.addNode(vnode);
        directDependencies.forEach(dep -> {
            var e = new MavenEdge(vnode, dep, "compile", false, Set.of(), "jar");
            graph.addDependencyEdge(e);
        });
        return vnode;
    }

    private void removeVirtualNode(Revision virtualNode) {
        var edges = graph.dependencyEdges(virtualNode);
        edges.forEach(e -> graph.removeDependencyEdge(e));
        graph.removeNode(virtualNode);
    }
}
