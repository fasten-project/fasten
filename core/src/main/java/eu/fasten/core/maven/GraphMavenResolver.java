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
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyEdge;
import eu.fasten.core.maven.data.DependencyTree;
import eu.fasten.core.maven.data.MavenProduct;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.utils.DependencyGraphUtilities;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

@CommandLine.Command(name = "GraphMavenResolver")
public class GraphMavenResolver implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GraphMavenResolver.class);

    @CommandLine.Option(names = {"-p", "--serializedPath"},
            paramLabel = "PATH",
            description = "Path to load a serialized Maven dependency graph from")
    protected String serializedPath;

    @CommandLine.Option(names = {"--repl"},
            paramLabel = "REPL",
            description = "Start a REPL loop")
    protected boolean repl;

    @CommandLine.Option(names = {"-a", "--artifactId"},
            paramLabel = "ARTIFACT",
            description = "artifactId of the Maven coordinate")
    protected String artifact;

    @CommandLine.Option(names = {"-g", "--groupId"},
            paramLabel = "GROUP",
            description = "groupId of the Maven coordinate")
    protected String group;

    @CommandLine.Option(names = {"-v", "--version"},
            paramLabel = "VERSION",
            description = "version of the Maven coordinate")
    protected String version;

    @CommandLine.Option(names = {"-t", "--timestamp"},
            paramLabel = "TS",
            description = "Timestamp for resolution",
            defaultValue = "-1")
    protected long timestamp;

    @CommandLine.Option(names = {"-s", "--scopes"},
            paramLabel = "SCOPES",
            description = "List of scopes to use for resolution (separated by \",\")",
            defaultValue = Constants.defaultMavenResolutionScopes,
            split = ",")
    protected List<String> scopes;

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

    @CommandLine.Option(names = {"-r", "--reverse"},
            description = "Use reverse resolution (resolve dependents instead of dependencies)")
    boolean reverseResolution;

    @CommandLine.Option(names = {"-fo", "--filter-optional"},
            description = "Filter out optional dependencies")
    boolean filterOptional;

    @CommandLine.Option(names = {"-fs", "--filter-scopes"},
            description = "Filter out dependencies by scope")
    boolean filterScopes;

    @CommandLine.Option(names = {"-fe", "--filter-exclusions"},
            description = "Filter out excluded dependencies")
    boolean filterExclusions;

    private static Graph<Revision, DependencyEdge> dependencyGraph;
    private static Graph<Revision, DependencyEdge> dependentGraph;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new GraphMavenResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {

        if (serializedPath != null) {
            try {
                var optDependencyGraph = DependencyGraphUtilities.loadDependencyGraph(serializedPath);
                if (optDependencyGraph.isPresent()) {
                    dependencyGraph = optDependencyGraph.get();
                    dependentGraph = DependencyGraphUtilities.invertDependencyGraph(dependencyGraph);
                }
            } catch (Exception e) {
                logger.warn("Could not load serialized dependency graph from {}\n{}", serializedPath, e);
            }
        }

        DSLContext dbContext;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
        } catch (SQLException e) {
            logger.error("Could not connect to the database", e);
            return;
        }

        if (artifact != null && group != null && version != null) {

            Set<Revision> artifactSet;
            if (reverseResolution) {
                artifactSet = this.resolveFullDependentsSet(group, artifact, version, timestamp,
                        scopes, dbContext, filterOptional, filterScopes, filterExclusions);
            } else {
                artifactSet = this.resolveFullDependencySet(group, artifact, version, timestamp,
                        scopes, dbContext, filterOptional, filterScopes, filterExclusions);
            }
            logger.info("--------------------------------------------------");
            logger.info("Maven coordinate:");
            logger.info(group + Constants.mvnCoordinateSeparator + artifact
                    + Constants.mvnCoordinateSeparator + version);
            logger.info("--------------------------------------------------");
            logger.info("Found " + artifactSet.size() + " (transitive) dependencies"
                    + (artifactSet.size() > 0 ? ":" : "."));
            artifactSet.forEach(d -> logger.info(d.toString()));
            logger.info("--------------------------------------------------");
        }

        if (repl) {
            if (dependencyGraph == null) {
                logger.warn("REPL mode only works with an initialized graph");
                return;
            }

            repl(dbContext);
        }
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

                Set<Revision> revisions;
                try {

                    if (parts[0].startsWith("!")) {
                        parts[0] = parts[0].substring(1);
                        revisions = resolveDependents(parts[0], parts[1], parts[2], timestamp, db, true);
                    } else {
                        revisions = resolveDependencies(parts[0], parts[1], parts[2], timestamp, db, true);
                    }
                } catch (Exception e) {
                    System.err.println("Error retrieving revisions: " + e.getMessage());
                    continue;
                }

                for (var rev : revisions.stream().sorted(Comparator.comparing(Revision::toString)).
                        collect(Collectors.toList())) {
                    System.out.println(rev.toString());
                }
            }
        }
    }

    /**
     * Performs a BFS on the dependency graph to resolve the dependencies of the provided {@link Revision}, as specified
     * by the provided revision details.
     *
     * @return The (transitive) dependency set
     */
    public Set<Revision> resolveDependencies(String groupId, String artifactId,
                                             String version, long timestamp, DSLContext db, boolean transitive) {

        if (timestamp == -1) {
            var ts = getCreatedAt(groupId, artifactId, version, db);
            if (ts > 0) {
                timestamp = ts;
            }
        }

        Set<Revision> allDeps = new HashSet<>();
        try {
            var parent = getParentArtifact(groupId, artifactId, version, db);
            if (parent != null) {
                allDeps = resolveDependencies(parent, db, false);
            }
        } catch (Exception e) {
            logger.warn("Parent for revision {}:{}:{} not found: {}", groupId, artifactId, version, e.getMessage());
        }

        allDeps.addAll(revisionGraphBFS(dependencyGraph, groupId, artifactId, version, timestamp, transitive, false));

        return allDeps;
    }

    /**
     * Performs a BFS on the dependency graph to resolve the dependencies of the provided {@link Revision}
     *
     * @return The (transitive) dependency set
     */
    public Set<Revision> resolveDependencies(Revision r, DSLContext db, boolean transitive) {
        return resolveDependencies(r.groupId, r.artifactId, r.version.toString(),
                r.createdAt.getTime(), db, transitive);
    }

    /**
     * Performs a BFS on the dependency graph to resolve the dependencies of the provided {@link Revision}, as specified
     * by the provided revision details.
     */
    public Set<Revision> resolveDependents(String groupId, String artifactId,
                                           String version, long timestamp, DSLContext db, boolean transitive) {
        return revisionGraphBFS(dependentGraph, groupId, artifactId, version, timestamp, transitive, true);
    }

    /**
     * Performs a BFS on the dependent graph to resolve all nodes that depend on the provided {@link Revision}
     *
     * @return The (transitive) dependent set
     */
    public Set<Revision> resolveDependents(Revision r, DSLContext db, boolean transitive) {
        return revisionGraphBFS(dependentGraph, r.groupId, r.artifactId, r.version.toString(),
                r.createdAt.getTime(), transitive, true);
    }

    public Set<Revision> revisionGraphBFS(Graph<Revision, DependencyEdge> graph,
                                          String groupId, String artifactId, String version,
                                          long timestamp, boolean transitive, boolean dependents) {

        assert (timestamp > 0);
        var startTS = System.currentTimeMillis();
        logger.debug("BFS from root: {}:{}:{}", groupId, artifactId, version);

        var excludeProducts = new ArrayList<Pair<Revision, MavenProduct>>();
        var edges = graph.outgoingEdgesOf(new Revision(groupId, artifactId, version, new Timestamp(timestamp)));
        for (var exclusionEdge : edges.stream().filter(e -> !e.exclusions.isEmpty()).collect(Collectors.toList())) {
            for (var exclusion : exclusionEdge.exclusions) {
                var product = new MavenProduct(exclusion.groupId, exclusion.artifactId);
                excludeProducts.add(new Pair<>(exclusionEdge.source, product));
            }
        }
        var descendantsMap = new HashMap<Revision, Revision>();
        edges.forEach(e -> descendantsMap.put(e.target, e.target));

        var successors = Graphs.successorListOf(graph, new Revision(groupId, artifactId, version,
                new Timestamp(timestamp)));
        var workQueue = filterSuccessorsByTimestamp(successors, timestamp, dependents).stream().
                map(x -> new Pair<>(x, 1)).collect(Collectors.toCollection(ArrayDeque::new));

        logger.debug("Obtaining first level successors: {} items, {} ms", workQueue.size(),
                System.currentTimeMillis() - startTS);
        var result = workQueue.stream().map(Pair::getFirst).collect(Collectors.toSet());

        if (!transitive) {
            return filterDependenciesByExclusions(result, excludeProducts, descendantsMap);
        }

        var depthRevisions = new ArrayList<>(workQueue);

        while (!workQueue.isEmpty()) {
            var rev = workQueue.poll();
            result.add(rev.getFirst());
            depthRevisions.add(rev);
            var outgoingEdges = graph.outgoingEdgesOf(rev.getFirst());
            for (var exclusionEdge : outgoingEdges.stream().filter(e -> !e.exclusions.isEmpty()).collect(Collectors.toList())) {
                for (var exclusion : exclusionEdge.exclusions) {
                    var product = new MavenProduct(exclusion.groupId, exclusion.artifactId);
                    excludeProducts.add(new Pair<>(exclusionEdge.source, product));
                }
            }
            outgoingEdges.forEach(e -> descendantsMap.put(e.target, e.source));
            var filteredSuccessors = filterSuccessorsByScope(filterOptionalSuccessors(outgoingEdges), scopes)
                    .stream().map(e -> e.target).collect(Collectors.toList());
            var dependencies = filterSuccessorsByTimestamp(filteredSuccessors, timestamp, dependents);
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

        var depSet = resolveConflicts(new HashSet<>(depthRevisions));
        return filterDependenciesByExclusions(depSet, excludeProducts, descendantsMap);
    }

    public Set<Revision> filterDependenciesByExclusions(Set<Revision> dependencies, List<Pair<Revision, MavenProduct>> exclusions, Map<Revision, Revision> descendentsMap) {
        var finalSet = new HashSet<>(dependencies);
        var dependenciesByProduct = dependencies.stream().collect(Collectors.toMap(
                Revision::product,
                List::of,
                (x, y) -> {
                    var z = new ArrayList<Revision>();
                    z.addAll(x);
                    z.addAll(y);
                    return z;
                }));
        for (var excludeProduct : exclusions) {
            for (var dep : dependenciesByProduct.get(excludeProduct.getSecond())) {
                if (dep.product().equals(excludeProduct.getSecond()) && isDescendantOf(dep, excludeProduct.getFirst(), descendentsMap)) {
                    finalSet.remove(dep);
                }
            }
        }
        return finalSet;
    }

    public boolean isDescendantOf(Revision child, Revision parent, Map<Revision, Revision> descendants) {
        while (child != null && !Objects.equals(child, parent)) {
            child = descendants.get(child);
        }
        return Objects.equals(child, parent);
    }

    /**
     * Given a set of n successors for a revision r which are different revisions of the same product, select the
     * revisions that are closest to the release timestamp of r.
     *
     * @return A list of unique revisions per unique product in the input list.
     */
    public List<Revision> filterSuccessorsByTimestamp(List<Revision> successors, long timestamp, boolean dependents) {
        return successors.stream().collect(Collectors.toMap(
                Revision::product,
                List::of,
                (x, y) -> {
                    var z = new ArrayList<Revision>();
                    z.addAll(x);
                    z.addAll(y);
                    return z;
                })).values().stream()
                .map(revisions -> {
                    var latestTimestamp = 0L;
                    if (!dependents) {
                        latestTimestamp = -1L;
                    } else {
                        latestTimestamp = Long.MAX_VALUE;
                    }

                    Revision latest = null;
                    for (var r : revisions) {
                        if (!dependents) {
                            if (r.createdAt.getTime() <= timestamp && r.createdAt.getTime() > latestTimestamp) {
                                latestTimestamp = r.createdAt.getTime();
                                latest = r;
                            }
                        } else {
                            if (r.createdAt.getTime() >= timestamp && r.createdAt.getTime() < latestTimestamp) {
                                latestTimestamp = r.createdAt.getTime();
                                latest = r;
                            }
                        }
                    }
                    if (revisions.size() > 1)
                        logger.debug("Ignored {} revisions for dependency {}, selected: {}, timestamp: {}",
                                revisions.size() - 1, revisions.get(0).product(), latest, timestamp);
                    return latest;
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public Set<DependencyEdge> filterOptionalSuccessors(Set<DependencyEdge> outgoingEdges) {
        return outgoingEdges.stream()
                .filter(edge -> !edge.optional)
                .collect(Collectors.toSet());
    }

    public Set<DependencyEdge> filterSuccessorsByScope(Set<DependencyEdge> outgoingEdges, List<String> allowedScopes) {
        return outgoingEdges.stream()
                .filter(edge -> {
                    if (edge.scope == null || edge.scope.isEmpty()) {
                        edge.scope = "compile";
                    }
                    return allowedScopes.contains(edge.scope);
                }).collect(Collectors.toSet());
    }

    /**
     * Resolve conflicts (duplicate products with different versions) by picking revisions that are closer to the root.
     */
    public Set<Revision> resolveConflicts(Set<Pair<Revision, Integer>> depthRevisions) {
        return depthRevisions.stream().collect(Collectors.toMap(
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
                })).values().stream().map(Pair::getFirst).collect(Collectors.toSet());
    }

    public void buildDependencyGraph(DSLContext dbContext) {
        dependencyGraph = new DependencyGraphBuilder().buildDependencyGraph(dbContext);
    }

    public Set<Revision> resolveFullDependentsSet(String groupId, String artifactId,
                                                  String version, DSLContext dbContext) {
        return this.resolveFullDependentsSet(groupId, artifactId, version, -1,
                Arrays.asList(Dependency.SCOPES), dbContext, false, false, false);
    }

    public Set<Revision> resolveFullDependentsSet(String groupId, String artifactId,
                                                  String version, long timestamp,
                                                  List<String> scopes, DSLContext dbContext,
                                                  boolean filterOptional, boolean filterScopes,
                                                  boolean filterExclusions) {
        if (dependencyGraph == null) {
            buildDependencyGraph(dbContext);
        }
        // Constant memory footprint
        //var reverseGraph = new EdgeReversedGraph(dependencyGraph);
        var reverseGraph = DependencyGraphUtilities.invertDependencyGraph(dependencyGraph);
        return this.resolveDependencySetUsingGraph(groupId, artifactId, version, timestamp,
                scopes, dbContext, reverseGraph, filterOptional, filterScopes, filterExclusions);
    }

    public Set<Revision> resolveFullDependencySet(String groupId, String artifactId,
                                                  String version, DSLContext dbContext) {
        return resolveFullDependencySet(groupId, artifactId, version, -1,
                Arrays.asList(Dependency.SCOPES), dbContext, false, false, false);
    }

    public Set<Revision> resolveFullDependencySet(String groupId, String artifactId,
                                                  String version, long timestamp,
                                                  List<String> scopes, DSLContext dbContext,
                                                  boolean filterOptional, boolean filterScopes,
                                                  boolean filterExclusions) {
        if (dependencyGraph == null) {
            buildDependencyGraph(dbContext);
        }
        var graph = DependencyGraphUtilities.cloneDependencyGraph(dependencyGraph);
        return this.resolveDependencySetUsingGraph(groupId, artifactId, version, timestamp,
                scopes, dbContext, graph, filterOptional, filterScopes, filterExclusions);
    }

    public Set<Revision> resolveDependencySetUsingGraph(String groupId, String artifactId,
                                                        String version, long timestamp,
                                                        List<String> scopes, DSLContext dbContext,
                                                        Graph<Revision, DependencyEdge> dependencyGraph,
                                                        boolean filterOptional, boolean filterScopes,
                                                        boolean filterExclusions) {
        var parents = new HashSet<Revision>();
        parents.add(new Revision(groupId, artifactId, version, new Timestamp(-1)));
        var parent = this.getParentArtifact(groupId, artifactId, version, dbContext);
        while (parent != null) {
            parents.add(parent);
            parent = this.getParentArtifact(parent.groupId, parent.artifactId, parent.version.toString(), dbContext);
        }
        var dependencySet = new HashSet<Revision>();
        for (var artifact : parents) {
            var graph = dependencyGraph;
            if (filterOptional) {
                graph = filterOptionalDependencies(dependencyGraph);
            }
            if (timestamp != -1) {
                graph = filterDependencyGraphByTimestamp(graph, new Timestamp(timestamp));
            }
            if (filterScopes) {
                graph = filterDependencyGraphByScope(graph, scopes);
            }
            var rootNode = findNodeByArtifact(artifact, graph);
            var dependencyTree = buildDependencyTreeFromGraph(graph, rootNode);
            if (filterExclusions) {
                dependencyTree = filterExcludedDependencies(dependencyTree);
            }
            var currentDependencySet = collectDependencyTree(dependencyTree);
            dependencySet.addAll(currentDependencySet);
        }
        dependencySet.removeAll(parents);
        return dependencySet;
    }

    private Revision findNodeByArtifact(Revision artifact, Graph<Revision, DependencyEdge> graph) {
        for (var node : graph.vertexSet()) {
            if (node.equals(artifact)) {
                return node;
            }
        }
        throw new RuntimeException("Could not find resolution artifact's node in the dependency graph: " + artifact);
    }

    /**
     * Filters dependency graph removing edges
     * which point to artifacts released later than the provided timestamp.
     *
     * @param timestamp Timestamp for filtering
     * @return Filtered dependency graph
     */
    public Graph<Revision, DependencyEdge> filterDependencyGraphByTimestamp(
            Graph<Revision, DependencyEdge> dependencyGraph, Timestamp timestamp) {
        var graph = DependencyGraphUtilities.cloneDependencyGraph(dependencyGraph);
        for (var node : dependencyGraph.vertexSet()) {
            if (node.createdAt.after(timestamp)
                    && !node.createdAt.equals(new Timestamp(-1))) {
                dependencyGraph.edgeSet().stream().filter(e -> {
                    if (graph.containsEdge(e)) {
                        return graph.getEdgeTarget(e).equals(node) || graph.getEdgeSource(e).equals(node);
                    }
                    return false;
                }).forEach(graph::removeEdge);
                graph.removeVertex(node);
            }
        }
        return graph;
    }

    public Graph<Revision, DependencyEdge> filterOptionalDependencies(
            Graph<Revision, DependencyEdge> dependencyGraph) {
        var graph = DependencyGraphUtilities.cloneDependencyGraph(dependencyGraph);
        for (var edge : dependencyGraph.edgeSet()) {
            if (edge.optional) {
                graph.removeVertex(dependencyGraph.getEdgeTarget(edge));
                graph.removeEdge(edge);
            }
        }
        return graph;
    }

    public Graph<Revision, DependencyEdge> filterDependencyGraphByScope(
            Graph<Revision, DependencyEdge> dependencyGraph,
            List<String> scopes) {
        var graph = DependencyGraphUtilities.cloneDependencyGraph(dependencyGraph);
        for (var edge : dependencyGraph.edgeSet()) {
            var dependencyScope = edge.scope;
            if (dependencyScope == null || dependencyScope.isEmpty()) {
                dependencyScope = "compile";
            }
            if (!scopes.contains(dependencyScope)) {
                graph.removeVertex(dependencyGraph.getEdgeTarget(edge));
                graph.removeEdge(edge);
            }
        }
        return graph;
    }

    public DependencyTree buildDependencyTreeFromGraph(Graph<Revision, DependencyEdge> graph,
                                                       Revision root) {
        return this.buildDependencyTreeFromGraph(graph, root, new HashSet<>());
    }

    public DependencyTree buildDependencyTreeFromGraph(Graph<Revision, DependencyEdge> graph,
                                                       Revision root,
                                                       Set<Revision> visitedArtifacts) {
        // TODO: Maven should pick the highest version available
        var childTrees = new ArrayList<DependencyTree>();
        visitedArtifacts.add(root);
        var rootEdges = graph.outgoingEdgesOf(root);
        for (var edge : rootEdges) {
            var target = graph.getEdgeTarget(edge);
            if (!visitedArtifacts.contains(target)) {
                childTrees.add(buildDependencyTreeFromGraph(graph, target, visitedArtifacts));
            }
        }
        return new DependencyTree(new Dependency(root.groupId, root.artifactId, root.version.toString()), childTrees);

    }

    public DependencyTree filterExcludedDependencies(DependencyTree dependencyTree) {
        return filterExcludedDependenciesRecursively(dependencyTree, new HashSet<>());
    }

    private DependencyTree filterExcludedDependenciesRecursively(
            DependencyTree dependencyTree, Set<Dependency.Exclusion> exclusions) {
        if (dependencyTree.artifact != null) {
            exclusions.addAll(dependencyTree.artifact.exclusions);
        }
        var filteredDependencies = new ArrayList<DependencyTree>();
        for (var childTree : dependencyTree.dependencies) {
            if (!exclusions.contains(new Dependency.Exclusion(childTree.artifact.groupId,
                    childTree.artifact.artifactId))) {
                filteredDependencies.add(filterExcludedDependenciesRecursively(childTree, exclusions));
            }
        }
        return new DependencyTree(dependencyTree.artifact, filteredDependencies);
    }

    public Set<Revision> collectDependencyTree(DependencyTree rootDependencyTree) {
        var dependencySet = new HashSet<Revision>();
        var packages = new HashSet<String>();
        Queue<DependencyTree> queue = new LinkedList<>();
        queue.add(rootDependencyTree);
        while (!queue.isEmpty()) {
            DependencyTree tree = queue.poll();
            var artifactPackage = tree.artifact.groupId + Constants.mvnCoordinateSeparator
                    + tree.artifact.artifactId;
            if (!packages.contains(artifactPackage)) {
                packages.add(artifactPackage);
                dependencySet.add(new Revision(tree.artifact.groupId, tree.artifact.groupId, tree.artifact.getVersion(), new Timestamp(-1)));
            }
            queue.addAll(tree.dependencies);
        }
        return dependencySet;
    }

    public long getCreatedAt(String groupId, String artifactId, String version, DSLContext context) {
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

    public Revision getParentArtifact(String groupId, String artifactId, String version,
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
}
