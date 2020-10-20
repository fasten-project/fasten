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
import org.apache.commons.lang3.tuple.Pair;
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

@CommandLine.Command(name = "MavenResolver")
public class GraphMavenResolver implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MavenResolver.class);

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

    private static Map<Dependency, List<Pair<Dependency, Timestamp>>> dependencyGraph;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new MavenResolver()).execute(args);
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
            Set<Dependency> dependencySet;
            dependencySet = this.resolveFullDependencySet(group, artifact, version, timestamp,
                    scopes, dbContext);
            logger.info("--------------------------------------------------");
            logger.info("Maven coordinate:");
            logger.info(group + Constants.mvnCoordinateSeparator + artifact
                    + Constants.mvnCoordinateSeparator + version);
            logger.info("--------------------------------------------------");
            logger.info("Full dependency set:");
            dependencySet.forEach(d -> logger.info(d.toCanonicalForm()));
            logger.info("--------------------------------------------------");
        } else {
            logger.error("You need to specify Maven coordinate by providing its "
                    + "artifactId ('-a'), groupId ('-g') and version ('-v'). "
                    + "Optional timestamp (-t) can also be provided.");
        }
    }

    public Set<Dependency> resolveFullDependencySet(String groupId, String artifactId,
                                                    String version, DSLContext dbContext) {
        return resolveFullDependencySet(groupId, artifactId, version, -1,
                Arrays.asList(Dependency.SCOPES), dbContext);
    }

    public Set<Dependency> resolveFullDependencySet(String groupId, String artifactId,
                                                    String version, long timestamp,
                                                    List<String> scopes, DSLContext dbContext) {
        if (dependencyGraph == null) {
            dependencyGraph = new DependencyGraphBuilder().buildMavenDependencyGraph(dbContext);
        }
        var filteredGraph = dependencyGraph;
        if (timestamp != -1) {
            filteredGraph = filterDependencyGraphByTimestamp(filteredGraph, new Timestamp(timestamp));
        }
        var parents = new HashSet<Dependency>();
        parents.add(new Dependency(groupId, artifactId, version));
        var parent = this.getParentArtifact(groupId, artifactId, version, dbContext);
        while (parent != null) {
            parents.add(parent);
            parent = this.getParentArtifact(parent.getGroupId(), parent.getArtifactId(),
                    parent.getVersion(), dbContext);
        }
        var dependencySet = new HashSet<Dependency>();
        for (var parentArtifact : parents) {
            var dependencyTree = buildFullDependencyTree(parentArtifact.getGroupId(),
                    parentArtifact.getArtifactId(), parentArtifact.getVersion(), filteredGraph);
            dependencyTree = filterOptionalDependencies(dependencyTree);
            dependencyTree = filterDependencyTreeByScope(dependencyTree, scopes);
            dependencyTree = filterExcludedDependencies(dependencyTree);
            var currentDependencySet = collectDependencyTree(dependencyTree);
            currentDependencySet.remove(new Dependency(groupId, artifactId, version));
            dependencySet.addAll(currentDependencySet);
        }
        return dependencySet;
    }

    public DependencyTree buildFullDependencyTree(String groupId, String artifactId, String version,
                                                  Map<Dependency, List<Pair<Dependency, Timestamp>>> dependencyGraph) {
        var artifact = new Dependency(groupId, artifactId, version);
        var edges = dependencyGraph.get(artifact);
        DependencyTree dependencyTree;
        if (edges == null || edges.isEmpty()) {
            dependencyTree = new DependencyTree(artifact, new ArrayList<>());
        } else {
            var childTrees = new ArrayList<DependencyTree>();
            for (var edge : edges) {
                childTrees.add(this.buildFullDependencyTree(
                        edge.getLeft().getGroupId(), edge.getLeft().getArtifactId(),
                        edge.getLeft().getVersion(), dependencyGraph
                ));
            }
            dependencyTree = new DependencyTree(artifact, childTrees);
        }
        return dependencyTree;
    }

    /**
     * Filters dependency graph removing edges
     * which point to artifacts released later than the provided timestamp.
     *
     * @param timestamp Timestamp for filtering
     * @return Filtered dependency graph
     */
    public Map<Dependency, List<Pair<Dependency, Timestamp>>> filterDependencyGraphByTimestamp(
            Map<Dependency, List<Pair<Dependency, Timestamp>>> dependencyGraph,
            Timestamp timestamp) {
        var graph = dependencyGraph
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        for (var artifact : dependencyGraph.keySet()) {
            for (var edge : dependencyGraph.get(artifact)) {
                if (edge.getRight().after(timestamp)) {
                    var edges = dependencyGraph.get(artifact)
                            .stream()
                            .filter(e -> !e.equals(edge))
                            .collect(Collectors.toList());
                    graph.put(artifact, edges);
                    var dep = edge.getLeft();
                    graph.remove(new Dependency(dep.groupId, dep.artifactId, dep.getVersion()));
                }
            }
        }
        return graph;
    }

    public DependencyTree filterOptionalDependencies(DependencyTree dependencyTree) {
        var filteredDependencies = new ArrayList<DependencyTree>();
        for (var childTree : dependencyTree.dependencies) {
            if (!childTree.artifact.optional) {
                filteredDependencies.add(filterOptionalDependencies(childTree));
            }
        }
        return new DependencyTree(dependencyTree.artifact, filteredDependencies);
    }

    public DependencyTree filterDependencyTreeByScope(DependencyTree dependencyTree,
                                                      List<String> scopes) {
        var filteredDependencies = new ArrayList<DependencyTree>();
        for (var childTree : dependencyTree.dependencies) {
            var dependencyScope = childTree.artifact.scope;
            if (dependencyScope == null || dependencyScope.isEmpty()) {
                dependencyScope = "compile";
            }
            if (scopes.contains(dependencyScope)) {
                filteredDependencies.add(filterDependencyTreeByScope(childTree, scopes));
            }
        }
        return new DependencyTree(dependencyTree.artifact, filteredDependencies);
    }

    public DependencyTree filterExcludedDependencies(DependencyTree dependencyTree) {
        return filterExcludedDependenciesRecursively(dependencyTree, new HashSet<>());
    }

    private DependencyTree filterExcludedDependenciesRecursively(
            DependencyTree dependencyTree, Set<Dependency.Exclusion> exclusions) {
        exclusions.addAll(dependencyTree.artifact.exclusions);
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
        return new Dependency(parentCoordinate);
    }
}
