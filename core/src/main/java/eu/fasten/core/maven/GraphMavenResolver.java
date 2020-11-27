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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    static Graph<Revision, DependencyEdge> dependencyGraph;
    static Graph<Revision, DependencyEdge> dependentGraph;

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
                logger.warn("Could not load serialized dependency graph from {}\n", serializedPath, e);
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
                artifactSet = this.resolveDependents(group, artifact, version, timestamp,
                        dbContext, true);
            } else {
                artifactSet = this.resolveDependencies(group, artifact, version, timestamp,
                        dbContext, true);
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
                    e.printStackTrace(System.err);
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

        var resultTriples = new ArrayList<Triple<Set<Revision>, List<Pair<Revision, MavenProduct>>, Map<Revision, Revision>>>();

        Set<Revision> allDeps = new HashSet<>();
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

        resultTriples.add(revisionGraphBFS(dependencyGraph, groupId, artifactId, version, timestamp, transitive, false));
        for (var triple : resultTriples) {
            allDeps.addAll(triple.getLeft());
            exclusions.addAll(triple.getMiddle());
            descendants.putAll(triple.getRight());
        }

        return filterDependenciesByExclusions(allDeps, exclusions, descendants);
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
    public Set<Revision> resolveDependents(String groupId, String artifactId, String version,
                                           long timestamp, DSLContext db, boolean transitive) {
        var triple = revisionGraphBFS(dependentGraph, groupId, artifactId, version, timestamp, transitive, true);
        return triple.getLeft();
    }

    /**
     * Performs a BFS on the dependent graph to resolve all nodes that depend on the provided {@link Revision}
     *
     * @return The (transitive) dependent set
     */
    public Triple<Set<Revision>, List<Pair<Revision, MavenProduct>>, Map<Revision, Revision>>
    resolveDependents(Revision r, boolean transitive) {
        return revisionGraphBFS(dependentGraph, r.groupId, r.artifactId, r.version.toString(),
                r.createdAt.getTime(), transitive, true);
    }

    public Triple<Set<Revision>, List<Pair<Revision, MavenProduct>>, Map<Revision, Revision>>
    revisionGraphBFS(Graph<Revision, DependencyEdge> graph,
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
        edges.forEach(e -> descendantsMap.put(e.target, e.source));

        var successors = Graphs.successorListOf(graph, new Revision(groupId, artifactId, version,
                new Timestamp(timestamp)));
        var workQueue = filterSuccessorsByTimestamp(successors, timestamp, dependents).stream().
                map(x -> new Pair<>(x, 1)).collect(Collectors.toCollection(ArrayDeque::new));

        logger.debug("Obtaining first level successors: {} items, {} ms", workQueue.size(),
                System.currentTimeMillis() - startTS);
        var result = workQueue.stream().map(Pair::getFirst).collect(Collectors.toSet());

        if (!transitive) {
            return new ImmutableTriple<>(result, excludeProducts, descendantsMap);
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

        logger.debug("Resolved version conflicts, now filtering exclusions");
        return new ImmutableTriple<>(depSet, excludeProducts, descendantsMap);
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
            if (!dependenciesByProduct.containsKey(excludeProduct.getSecond())) {
                continue;
            }
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

    public void buildDependencyGraph(DSLContext dbContext, String serializedGraphPath) {
        try {
            var graphOpt = DependencyGraphUtilities.loadDependencyGraph(serializedGraphPath);
            if (graphOpt.isEmpty()) {
                dependencyGraph = DependencyGraphUtilities.buildDependencyGraphFromScratch(dbContext, serializedGraphPath);
            } else {
                dependencyGraph = graphOpt.get();
            }
        } catch (Exception e) {
            logger.error("Could not build the dependency graph", e);
        }
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
