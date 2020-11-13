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
import eu.fasten.core.maven.data.DependencyTree;
import eu.fasten.core.maven.data.graph.DependencyEdge;
import eu.fasten.core.maven.data.graph.DependencyNode;
import eu.fasten.core.maven.utils.DependencyGraphUtilities;
import org.jgrapht.Graph;
import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@CommandLine.Command(name = "GraphMavenResolver")
public class GraphMavenResolver implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GraphMavenResolver.class);

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

    private static Graph<DependencyNode, DependencyEdge> dependencyGraph;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new GraphMavenResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        if (artifact != null && group != null && version != null) {
            DSLContext dbContext;
            try {
                dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
            } catch (SQLException e) {
                logger.error("Could not connect to the database", e);
                return;
            }
            Set<Dependency> artifactSet;
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
            artifactSet.forEach(d -> logger.info(d.toCanonicalForm()));
            logger.info("--------------------------------------------------");
        } else {
            logger.error("You need to specify Maven coordinate by providing its "
                    + "artifactId ('-a'), groupId ('-g') and version ('-v'). "
                    + "Optional timestamp (-t) can also be provided.");
        }
    }

    public void buildDependencyGraph(DSLContext dbContext) {
        dependencyGraph = new DependencyGraphBuilder().buildDependencyGraph(dbContext);
    }

    public Set<Dependency> resolveFullDependentsSet(String groupId, String artifactId,
                                                    String version, DSLContext dbContext) {
        return this.resolveFullDependentsSet(groupId, artifactId, version, -1,
                Arrays.asList(Dependency.SCOPES), dbContext, false, false, false);
    }

    public Set<Dependency> resolveFullDependentsSet(String groupId, String artifactId,
                                                    String version, long timestamp,
                                                    List<String> scopes, DSLContext dbContext,
                                                    boolean filterOptional, boolean filterScopes,
                                                    boolean filterExclusions) {
        if (dependencyGraph == null) {
            buildDependencyGraph(dbContext);
        }
        var reverseGraph = DependencyGraphUtilities.invertDependencyGraph(dependencyGraph);
        return this.resolveDependencySetUsingGraph(groupId, artifactId, version, timestamp,
                scopes, dbContext, reverseGraph, filterOptional, filterScopes, filterExclusions);
    }

    public Set<Dependency> resolveFullDependencySet(String groupId, String artifactId,
                                                    String version, DSLContext dbContext) {
        return resolveFullDependencySet(groupId, artifactId, version, -1,
                Arrays.asList(Dependency.SCOPES), dbContext, false, false, false);
    }

    public Set<Dependency> resolveFullDependencySet(String groupId, String artifactId,
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

    public Set<Dependency> resolveDependencySetUsingGraph(String groupId, String artifactId,
                                                          String version, long timestamp,
                                                          List<String> scopes, DSLContext dbContext,
                                                          Graph<DependencyNode, DependencyEdge> dependencyGraph,
                                                          boolean filterOptional, boolean filterScopes,
                                                          boolean filterExclusions) {
        var parents = new HashSet<Dependency>();
        parents.add(new Dependency(groupId, artifactId, version));
        var parent = this.getParentArtifact(groupId, artifactId, version, dbContext);
        while (parent != null) {
            parents.add(parent);
            parent = this.getParentArtifact(parent.getGroupId(), parent.getArtifactId(),
                    parent.getVersion(), dbContext);
        }
        var dependencySet = new HashSet<Dependency>();
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

    private DependencyNode findNodeByArtifact(Dependency artifact, Graph<DependencyNode, DependencyEdge> graph) {
        for (var node : graph.vertexSet()) {
            if (node.artifact.equals(artifact)) {
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
    public Graph<DependencyNode, DependencyEdge> filterDependencyGraphByTimestamp(
            Graph<DependencyNode, DependencyEdge> dependencyGraph, Timestamp timestamp) {
        var graph = DependencyGraphUtilities.cloneDependencyGraph(dependencyGraph);
        for (var node : dependencyGraph.vertexSet()) {
            if (node.releaseTimestamp.after(timestamp)
                    && !node.releaseTimestamp.equals(new Timestamp(-1))) {
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

    public Graph<DependencyNode, DependencyEdge> filterOptionalDependencies(
            Graph<DependencyNode, DependencyEdge> dependencyGraph) {
        var graph = DependencyGraphUtilities.cloneDependencyGraph(dependencyGraph);
        for (var edge : dependencyGraph.edgeSet()) {
            if (edge.optional) {
                graph.removeVertex(dependencyGraph.getEdgeTarget(edge));
                graph.removeEdge(edge);
            }
        }
        return graph;
    }

    public Graph<DependencyNode, DependencyEdge> filterDependencyGraphByScope(
            Graph<DependencyNode, DependencyEdge> dependencyGraph,
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

    public DependencyTree buildDependencyTreeFromGraph(Graph<DependencyNode, DependencyEdge> graph,
                                                       DependencyNode root) {
        return this.buildDependencyTreeFromGraph(graph, root, new HashSet<>());
    }

    public DependencyTree buildDependencyTreeFromGraph(Graph<DependencyNode, DependencyEdge> graph,
                                                       DependencyNode root,
                                                       Set<Dependency> visitedArtifacts) {
//        var childTrees = new ArrayList<DependencyTree>();
//        visitedArtifacts.add(root.artifact);
//        var rootEdges = graph.outgoingEdgesOf(root);
//        for (var edge : rootEdges) {
//            var target = graph.getEdgeTarget(edge);
//            if (!visitedArtifacts.contains(target.artifact)) {
//                childTrees.add(buildDependencyTreeFromGraph(graph, target, visitedArtifacts));
//            }
//        }
//        return new DependencyTree(root.artifact, childTrees);
        return new DependencyTree(new Dependency("foo", "bar", "test"), new ArrayList<DependencyTree>());

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

    public Set<Dependency> collectDependencyTree(DependencyTree rootDependencyTree) {
        var dependencySet = new HashSet<Dependency>();
        var packages = new HashSet<String>();
        Queue<DependencyTree> queue = new LinkedList<>();
        queue.add(rootDependencyTree);
        while (!queue.isEmpty()) {
            DependencyTree tree = queue.poll();
            var artifactPackage = tree.artifact.groupId + Constants.mvnCoordinateSeparator
                    + tree.artifact.artifactId;
            if (!packages.contains(artifactPackage)) {
                packages.add(artifactPackage);
                dependencySet.add(tree.artifact);
            }
            queue.addAll(tree.dependencies);
        }
        return dependencySet;
    }

    public Dependency getParentArtifact(String groupId, String artifactId, String version,
                                        DSLContext context) {
        var packageName = groupId + Constants.mvnCoordinateSeparator + artifactId;
        var result = context.select(PackageVersions.PACKAGE_VERSIONS.METADATA)
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
        } catch (JSONException e) {
            logger.error("Could not parse JSON for package version's metadata", e);
            return null;
        }
        // TODO
        return new Dependency("foo", "bar", "123");
    }
}
