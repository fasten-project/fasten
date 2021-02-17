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

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.DependencyEdge;
import eu.fasten.core.maven.data.MavenProduct;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.utils.DependencyGraphUtilities;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.math3.util.Pair;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(name = "GraphMavenResolver")
public class GraphMavenResolver implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GraphMavenResolver.class);

    @CommandLine.Option(names = {"-p", "--serializedPath"},
            paramLabel = "PATH",
            description = "Path to load a serialized Maven dependency graph from",
            required = true)
    protected String serializedPath;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:postgres")
    protected String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    protected String dbUser;

    private boolean ignoreMissing = false;

    static Graph<Revision, DependencyEdge> dependencyGraph;
    static Graph<Revision, DependencyEdge> dependentGraph;

    static List<String> scopes = new ArrayList<>();

    static {
        scopes.add("compile");
        scopes.add("provided");
//        scopes.add("test");
        scopes.add("runtime");
//        scopes.add("system");
//        scopes.add("import");
    }

    static List<String> types = new ArrayList<>();

    static {
        types.add("jar");
        types.add("war");
        types.add("xar");
    }

    public boolean getIgnoreMissing() {
        return ignoreMissing;
    }

    public void setIgnoreMissing(boolean ignoreMissing) {
        this.ignoreMissing = ignoreMissing;
    }

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new GraphMavenResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {

        try {
            var optDependencyGraph = DependencyGraphUtilities.loadDependencyGraph(serializedPath);
            if (optDependencyGraph.isPresent()) {
                dependencyGraph = optDependencyGraph.get();
                dependentGraph = DependencyGraphUtilities.invertDependencyGraph(dependencyGraph);
            }
        } catch (Exception e) {
            logger.warn("Could not load serialized dependency graph from {}\n", serializedPath, e);
        }

        DSLContext dbContext;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
        } catch (SQLException e) {
            logger.error("Could not connect to the database", e);
            return;
        }

        repl(dbContext);
    }

    public void repl(DSLContext db) {
        System.out.println("Query format: [!]group:artifact:version<:ts>");
        System.out.println("! at the beginning means search for dependents (default: dependencies)");
        System.out.println("ts: Use the provided timestamp for resolution (default: the artifact release timestamp)");
        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                var input = scanner.nextLine();

                if (input.equals("")) continue;
                if (input.equals("quit") || input.equals("exit")) break;

                var parts = input.split(":");

                if (parts.length < 3 || parts[2] == null) {
                    System.out.println("Wrong input: " + input + ". Format is: [!]group:artifact:version<:ts>");
                    continue;
                }

                var timestamp = -1L;
                if (parts.length > 3 && parts[3] != null) {
                    try {
                        timestamp = Long.parseLong(parts[3]);
                    } catch (NumberFormatException nfe) {
                        System.err.println("Error parsing the provided timestamp");
                        continue;
                    }
                }

                ObjectLinkedOpenHashSet<Revision> revisions;
                var startTS = System.currentTimeMillis();
                try {

                    if (parts[0].startsWith("!")) {
                        parts[0] = parts[0].substring(1);
                        revisions = resolveDependents(parts[0], parts[1], parts[2], timestamp, true);
                    } else {
                        revisions = resolveDependencies(parts[0], parts[1], parts[2], timestamp, db, true);
                    }
                } catch (Exception e) {
                    System.err.println("Error retrieving revisions: " + e.getMessage());
                    e.printStackTrace(System.err);
                    continue;
                }

                for (var rev : revisions.stream().sorted(Comparator.comparing(Revision::toString)).
                        collect(Collectors.toList())) {
                    System.out.println(rev.toString());
                }
                System.err.println(revisions.size() + " revisions, " + (System.currentTimeMillis() - startTS) + " ms");
            }
        }
    }

    /**
     * Performs a BFS on the dependency graph to resolve the dependencies of the provided {@link Revision}, as specified
     * by the provided revision details.
     *
     * @return The (transitive) dependency set
     */
    public ObjectLinkedOpenHashSet<Revision> resolveDependencies(String groupId, String artifactId, String version, long timestamp,
                                                                 DSLContext db, boolean transitive) {

        if (timestamp == -1) {
            var ts = getCreatedAt(groupId, artifactId, version, db);
            if (ts > 0) {
                timestamp = ts;
            }
        }

        var resultTriples = new ArrayList<Triple<ObjectLinkedOpenHashSet<Revision>, List<Pair<Revision, MavenProduct>>, Map<Revision, Revision>>>();

        ObjectLinkedOpenHashSet<Revision> allDeps = new ObjectLinkedOpenHashSet<>();
        var exclusions = new ArrayList<Pair<Revision, MavenProduct>>();
        var descendants = new HashMap<Revision, Revision>();

        try {
            var parent = getParentArtifact(groupId, artifactId, version, db);
            if (parent != null) {
                allDeps = resolveDependencies(parent, db, false);
            }
        } catch (Exception e) {
            logger.warn("Parent for revision {}:{}:{} not found: {}", groupId, artifactId, version, e.getMessage());
        }

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
    public ObjectLinkedOpenHashSet<Revision> resolveDependencies(Revision r, DSLContext db, boolean transitive) {
        return resolveDependencies(r.groupId, r.artifactId, r.version.toString(),
                r.createdAt.getTime(), db, transitive);
    }

    public Revision addVirtualNode(ObjectLinkedOpenHashSet<Revision> directDependencies) {
        var nodeGroup = String.valueOf(directDependencies.stream().reduce(0, (x, r) -> x + r.groupId.hashCode(), Integer::sum));
        var nodeArtifact = String.valueOf(directDependencies.stream().reduce(0, (x, r) -> x + r.artifactId.hashCode(), Integer::sum));
        var nodeVersion = String.valueOf(directDependencies.stream().reduce(0, (x, r) -> x + r.version.hashCode(), Integer::sum));
        var node = new Revision(-1, nodeGroup, nodeArtifact, nodeVersion, new Timestamp(-1));
        dependencyGraph.addVertex(node);
        directDependencies.forEach(d -> dependencyGraph.addVertex(d));
        directDependencies.forEach(d -> dependencyGraph.addEdge(node, d, new DependencyEdge(node, d, "compile", false, new ArrayList<>(), "jar")));
        return node;
    }

    public void removeVirtualNode(Revision virtualNode) {
        var edges = dependencyGraph.outgoingEdgesOf(virtualNode);
        edges.forEach(e -> dependencyGraph.removeEdge(e));
        dependencyGraph.removeVertex(virtualNode);
    }


    public Triple<ObjectLinkedOpenHashSet<Revision>, List<Pair<Revision, MavenProduct>>, Map<Revision, Revision>>
    dependencyBFS(String groupId, String artifactId, String version, long timestamp, boolean transitive) {
        assert (timestamp > 0);
        var startTS = System.currentTimeMillis();
        logger.debug("BFS from root: {}:{}:{}", groupId, artifactId, version);

        var excludeProducts = new ArrayList<Pair<Revision, MavenProduct>>();
        var artifact = new Revision(groupId, artifactId, version, new Timestamp(timestamp));
        if (!dependencyGraph.containsVertex(artifact)) {
            throw new RuntimeException("Revision " + artifact + " is not in the dependency graph. Probably it is missing in the database");
        }
        var edges = dependencyGraph.outgoingEdgesOf(artifact);
        for (var exclusionEdge : edges.stream().filter(e -> !e.exclusions.isEmpty()).collect(Collectors.toList())) {
            for (var exclusion : exclusionEdge.exclusions) {
                var product = new MavenProduct(exclusion.groupId, exclusion.artifactId);
                excludeProducts.add(new Pair<>(exclusionEdge.source, product));
            }
        }
        var descendantsMap = new HashMap<Revision, Revision>();
        edges.forEach(e -> descendantsMap.put(e.target, e.source));

        var successors = Graphs.successorListOf(dependencyGraph, artifact);
        var workQueue = filterDependenciesByTimestamp(successors, timestamp).stream().
                map(x -> new Pair<>(x, 1)).collect(Collectors.toCollection(ArrayDeque::new));

        logger.debug("Obtaining first level successors: {} items, {} ms", workQueue.size(),
                System.currentTimeMillis() - startTS);
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
            if (!dependencyGraph.containsVertex(rev.getFirst())) {
                if (ignoreMissing) {
                    continue;
                } else {
                    throw new RuntimeException("Revision " + rev.getFirst() + " is not in the dependency graph. Probably it is missing in the database");
                }
            }
            var outgoingEdges = new ObjectLinkedOpenHashSet<>(dependencyGraph.outgoingEdgesOf(rev.getFirst()));
            for (var exclusionEdge : outgoingEdges.stream().filter(e -> !e.exclusions.isEmpty()).collect(Collectors.toList())) {
                for (var exclusion : exclusionEdge.exclusions) {
                    var product = new MavenProduct(exclusion.groupId, exclusion.artifactId);
                    excludeProducts.add(new Pair<>(exclusionEdge.source, product));
                }
            }
            outgoingEdges.forEach(e -> descendantsMap.put(e.target, e.source));
            var filteredSuccessors = filterSuccessorsByType(filterSuccessorsByScope(filterOptionalSuccessors(outgoingEdges), scopes), types)
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

        if (!dependentGraph.containsVertex(artifact)) {
            throw new RuntimeException("Revision " + artifact + " is not in the dependents graph. Probably it is missing in the database");
        }

        var workQueue = new ArrayDeque<>(filterDependentsByTimestamp(Graphs.successorListOf(dependentGraph, artifact), timestamp));

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
            if (!dependentGraph.containsVertex(rev)) {
                if (ignoreMissing) {
                    continue;
                } else {
                    throw new RuntimeException("Revision " + rev + " is not in the dependents graph. Probably it is missing in the database");
                }
            }
            var dependents = filterDependentsByTimestamp(Graphs.successorListOf(dependentGraph, rev), timestamp);
            for (var dependent : dependents) {
                if (!result.contains(dependent)) {
                    workQueue.add(dependent);
                }
            }
        }
        return result;
    }

    public ObjectLinkedOpenHashSet<Revision> filterDependenciesByExclusions(Set<Revision> dependencies,
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

    public boolean isDescendantOf(Revision child, Revision parent, Map<Revision, Revision> descendants) {
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
    protected List<Revision> filterDependenciesByTimestamp(List<Revision> successors, long timestamp) {
        return successors.stream().
                collect(Collectors.groupingBy(Revision::product)).
                values().stream().
                map(revisions -> {
                    var latestTimestamp = -1L;

                    Revision latest = null;
                    for (var r : revisions) {
                        if (r.createdAt.getTime() <= timestamp && r.createdAt.getTime() > latestTimestamp) {
                            latestTimestamp = r.createdAt.getTime();
                            latest = r;
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

    protected List<Revision> filterDependentsByTimestamp(List<Revision> successors, long timestamp) {
        return successors.stream().
                filter(revision -> revision.createdAt.getTime() >= timestamp).
                collect(Collectors.toList());
    }

    protected ObjectLinkedOpenHashSet<DependencyEdge> filterOptionalSuccessors(ObjectLinkedOpenHashSet<DependencyEdge> outgoingEdges) {
        var result = new ObjectLinkedOpenHashSet<DependencyEdge>();
        outgoingEdges.stream()
                .filter(edge -> !edge.optional)
                .forEachOrdered(result::add);
        return result;
    }

    protected ObjectLinkedOpenHashSet<DependencyEdge> filterSuccessorsByScope(ObjectLinkedOpenHashSet<DependencyEdge> outgoingEdges, List<String> allowedScopes) {
        var result = new ObjectLinkedOpenHashSet<DependencyEdge>();
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

    protected ObjectLinkedOpenHashSet<DependencyEdge> filterSuccessorsByType(ObjectLinkedOpenHashSet<DependencyEdge> outgoingEdges, List<String> allowedTypes) {
        var result = new ObjectLinkedOpenHashSet<DependencyEdge>();
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
    protected ObjectLinkedOpenHashSet<Revision> resolveConflicts(ObjectLinkedOpenHashSet<Pair<Revision, Integer>> depthRevisions) {
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

    public void buildDependencyGraph(DSLContext dbContext, String serializedGraphPath) throws Exception {
        var graphOpt = DependencyGraphUtilities.loadDependencyGraph(serializedGraphPath);
        if (graphOpt.isEmpty()) {
            dependencyGraph = DependencyGraphUtilities.buildDependencyGraphFromScratch(dbContext, serializedGraphPath);
        } else {
            dependencyGraph = graphOpt.get();
        }
        dependentGraph = DependencyGraphUtilities.invertDependencyGraph(dependencyGraph);
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

    private Revision getParentArtifact(String groupId, String artifactId, String version,
                                       DSLContext context) {
        var packageName = groupId + Constants.mvnCoordinateSeparator + artifactId;
        var result = context.select(PackageVersions.PACKAGE_VERSIONS.METADATA,
                PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
                .from(PackageVersions.PACKAGE_VERSIONS)
                .join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                .fetchOne();
        if (result == null || result.component1() == null) {
            return null;
        }
        var metadata = new JSONObject(result.component1().data());
        String parentCoordinate;
        try {
            parentCoordinate = metadata.getString("parentCoordinate");
            if (parentCoordinate.isEmpty()) {
                return null;
            }
            var coordinates = parentCoordinate.split(":");
            return new Revision(coordinates[0], coordinates[1], coordinates[2], result.component2());
        } catch (JSONException e) {
            logger.error("Could not parse JSON for package version's metadata", e);
            return null;
        }
    }

    public ObjectLinkedOpenHashSet<Revision> findAllRevisionsInThePath(Revision source, Revision target) {
        var paths = getPaths(dependencyGraph, source, target, new ObjectLinkedOpenHashSet<>(), new ArrayList<>(), new ArrayList<>());
        var pathsNodes = new ObjectLinkedOpenHashSet<Revision>();
        for (var path : paths) {
            pathsNodes.addAll(path);
        }
        return pathsNodes;
    }

    private List<List<Revision>> getPaths(Graph<Revision, DependencyEdge> graph, Revision source, Revision target,
                                          ObjectLinkedOpenHashSet<Revision> visited, List<Revision> path, List<List<Revision>> vulnerablePaths) {
        if (path.isEmpty()) {
            path.add(source);
        }
        if (source == target) {
            vulnerablePaths.add(new ArrayList<>(path));
            return vulnerablePaths;
        }
        visited.add(source);
        for (var edge : graph.outgoingEdgesOf(source)) {
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
}
