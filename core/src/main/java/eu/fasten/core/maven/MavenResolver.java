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
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyTree;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import picocli.CommandLine;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(name = "MavenResolver")
public class MavenResolver implements Runnable {

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

    @CommandLine.Option(names = {"-o", "--online"},
            description = "Use online resolution mode")
    protected boolean onlineMode;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new MavenResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        if (artifact != null && group != null && version != null) {
            DSLContext dbContext = null;
            if (!onlineMode && timestamp != -1) {
                try {
                    dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
                } catch (SQLException e) {
                    System.err.println("Could not connect to the database: " + e.getMessage());
                    return;
                }
            }
            Set<Dependency> dependencySet;
            if (!onlineMode) {
                dependencySet = this.resolveFullDependencySet(group, artifact, version,
                        timestamp, scopes, dbContext);
            } else {
                var coordinate = group + Constants.mvnCoordinateSeparator + artifact
                        + Constants.mvnCoordinateSeparator + version;
                dependencySet = this.resolveFullDependencySetOnline(coordinate, timestamp, dbContext);
            }
            System.out.println("--------------------------------------------------");
            System.out.println("Maven coordinate:");
            System.out.println(group + Constants.mvnCoordinateSeparator + artifact
                    + Constants.mvnCoordinateSeparator + version);
            System.out.println("--------------------------------------------------");
            System.out.println("Full dependency set:");
            dependencySet.forEach(System.out::println);
            System.out.println("--------------------------------------------------");
        } else {
            System.err.println("You need to specify Maven coordinate by providing its "
                    + "artifactId ('-a'), groupId ('-g') and version ('-v'). "
                    + "Optional timestamp (-t) can also be provided.");
        }
    }

    /**
     * Creates and resolves a full dependency set of the given Maven artifact.
     *
     * @param groupId    groupId of the Maven artifact to resolve
     * @param artifactId artifactId of the Maven artifact to resolve
     * @param version    version of the Maven artifact to resolve
     * @param timestamp  timestamp to perform version filtering (-1 in order not to filter)
     * @param scopes     Dependency scopes to use (other scopes will be filtered out)
     * @param dbContext  Database connection context
     * @return Set of dependencies including transitive dependencies
     */
    public Set<Dependency> resolveFullDependencySet(String groupId, String artifactId,
                                                    String version, long timestamp,
                                                    List<String> scopes, DSLContext dbContext) {
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
                    parentArtifact.getArtifactId(), parentArtifact.getVersion(),
                    dbContext);
            dependencyTree = filterOptionalDependencies(dependencyTree);
            dependencyTree = filterDependencyTreeByScope(dependencyTree, scopes);
            dependencyTree = filterExcludedDependencies(dependencyTree);
            var currentDependencySet = collectDependencyTree(dependencyTree);
            currentDependencySet.remove(new Dependency(groupId, artifactId, version));
            if (timestamp != -1) {
                currentDependencySet = filterDependenciesByTimestamp(currentDependencySet,
                        new Timestamp(timestamp), dbContext);
            }
            dependencySet.addAll(currentDependencySet);
        }
        return dependencySet;
    }

    /**
     * Creates and resolves a full dependency set of the given Maven artifact.
     *
     * @param groupId    groupId of the Maven artifact to resolve
     * @param artifactId artifactId of the Maven artifact to resolve
     * @param version    version of the Maven artifact to resolve
     * @param dbContext  Database connection context
     * @return Set of dependencies including transitive dependencies
     */
    public Set<Dependency> resolveFullDependencySet(String groupId, String artifactId,
                                                    String version, DSLContext dbContext) {
        return resolveFullDependencySet(groupId, artifactId, version, -1,
                Arrays.asList(Constants.defaultMavenResolutionScopes.split(",").clone()),
                dbContext);
    }

    /**
     * Builds a full dependency tree using data from the database.
     *
     * @param groupId    groupId of the artifact to resolve
     * @param artifactId artifactId of the artifact to resolve
     * @param version    version of the artifact to resolve
     * @param dbContext  Database connection context
     * @return Dependency tree with all transitive dependencies
     */
    public DependencyTree buildFullDependencyTree(String groupId, String artifactId, String version,
                                                  DSLContext dbContext,
                                                  Map<Dependency, List<Dependency>> dependencyGraph) {
        var artifact = new Dependency(groupId, artifactId, version);
        if (dependencyGraph == null) {
            dependencyGraph = new DependencyGraphBuilder().buildMavenDependencyGraph(dbContext);
        }
        if (dependencyGraph == null || dependencyGraph.isEmpty()) {
            return null;
        }
        var dependencies = new ArrayList<>(dependencyGraph.get(artifact));
        DependencyTree dependencyTree;
        if (dependencies.isEmpty()) {
            dependencyTree = new DependencyTree(artifact, new ArrayList<>());
        } else {
            var childTrees = new ArrayList<DependencyTree>();
            for (var dep : dependencies) {
                childTrees.add(this.buildFullDependencyTree(dep.getGroupId(), dep.getArtifactId(),
                        dep.getVersion(), dbContext, dependencyGraph));
            }
            dependencyTree = new DependencyTree(artifact, childTrees);
        }
        return dependencyTree;
    }

    public DependencyTree buildFullDependencyTree(String groupId, String artifactId, String version,
                                                  DSLContext dbContext) {
        return buildFullDependencyTree(groupId, artifactId, version, dbContext, null);
    }

    /**
     * Filters out the optional dependencies from the dependency tree.
     *
     * @param dependencyTree Dependency tree to filter
     * @return Same dependency tree as given but without any optional dependencies
     */
    public DependencyTree filterOptionalDependencies(DependencyTree dependencyTree) {
        var filteredDependencies = new ArrayList<DependencyTree>();
        for (var childTree : dependencyTree.dependencies) {
            if (!childTree.artifact.optional) {
                filteredDependencies.add(filterOptionalDependencies(childTree));
            }
        }
        return new DependencyTree(dependencyTree.artifact, filteredDependencies);
    }

    /**
     * Filters a dependency tree by dependency scope.
     *
     * @param dependencyTree Dependency tree to filter
     * @param scopes         List of scopes with which the dependencies will be left in tree.
     *                       If dependency scope is not in the list of scopes,
     *                       it will be removed from the dependency tree tree
     * @return Same dependency tree as given
     * but with only those dependencies whose scopes are in the list
     */
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

    /**
     * Filters out excluded dependencies from the dependency tree.
     * If some dependency is in the list of exclusions of its (transitive) parent,
     * it will be removed from the dependency tree.
     *
     * @param dependencyTree Dependency tree to filter.
     * @return Same dependency tree as given but without excluded dependencies
     */
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

    /**
     * Transforms a dependency tree into set.
     *
     * @param dependencyTree Dependency tree to transform
     * @return A set of all dependencies from the given dependency tree
     */
    public Set<Dependency> collectDependencyTree(DependencyTree dependencyTree) {
        var dependencySet = new HashSet<Dependency>();
        dependencySet.add(dependencyTree.artifact);
        for (var childTree : dependencyTree.dependencies) {
            dependencySet.addAll(collectDependencyTree(childTree));
        }
        return dependencySet;
    }

    /**
     * Retrieves a parent Maven artifact from the database.
     *
     * @param groupId    groupId of the artifact to find its parent
     * @param artifactId artifactId of the artifact to find its parent
     * @param version    version of the artifact to find its parent
     * @param context    Database connection context
     * @return a dependency/maven coordinate which is parent coordinate of the given one
     */
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
            e.printStackTrace(System.err);
            return null;
        }
        return new Dependency(parentCoordinate);
    }

    /**
     * Retrieve all direct dependencies of certain Maven artifact from the database.
     *
     * @param groupId    groupId of the artifact to find its dependencies
     * @param artifactId artifactId of the artifact to find its dependencies
     * @param version    version of the artifact to find its dependencies
     * @param context    Database connection context
     * @return A list of direct dependencies of the given Maven artifact
     */
    public List<Dependency> getArtifactDependenciesFromDatabase(String groupId, String artifactId,
                                                                String version, DSLContext context) {
        var packageName = groupId + Constants.mvnCoordinateSeparator + artifactId;
        var result = context.select(Dependencies.DEPENDENCIES.METADATA)
                .from(Dependencies.DEPENDENCIES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID
                        .eq(PackageVersions.PACKAGE_VERSIONS.ID))
                .join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                .fetch();
        return result.map(r -> Dependency.fromJSON(new JSONObject(r.component1().data())));
    }

    /**
     * Filters dependency versions by timestamp.
     * If the version of some dependency in the given dependency set was released later
     * than the provided timestamp then this dependency version will be downgraded
     * to use latest version which was release earlier or at the given timestamp.
     *
     * @param dependencies Set of dependencies to check
     * @param timestamp    Timestamp to filter by
     * @param context      Database connection context
     * @return same dependency set as given one
     * but with dependency versions released no later than the given timestamp
     */
    public Set<Dependency> filterDependenciesByTimestamp(Set<Dependency> dependencies,
                                                         Timestamp timestamp, DSLContext context) {
        var filteredDependencies = new HashSet<Dependency>(dependencies.size());
        for (var dependency : dependencies) {
            var packageName = dependency.groupId + Constants.mvnCoordinateSeparator + dependency.artifactId;
            var result = context.select(PackageVersions.PACKAGE_VERSIONS.VERSION)
                    .from(PackageVersions.PACKAGE_VERSIONS)
                    .join(Packages.PACKAGES)
                    .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                    .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                    .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                    .and(PackageVersions.PACKAGE_VERSIONS.CREATED_AT.lessOrEqual(timestamp))
                    .orderBy(PackageVersions.PACKAGE_VERSIONS.CREATED_AT.desc())
                    .limit(1)
                    .fetchOne();
            String suitableVersion = null;
            if (result != null) {
                suitableVersion = result.value1();
            }
            if (suitableVersion == null) {
                filteredDependencies.add(dependency);
            } else {
                filteredDependencies.add(
                        new Dependency(dependency.groupId, dependency.artifactId, suitableVersion)
                );
            }
        }
        return filteredDependencies;
    }

    /**
     * Resolves full dependency set online using ShrinkWrap's MavenResolver.
     *
     * @param mavenCoordinate Maven coordinate to resolve in the form of groupId:artifactId:version
     * @param timestamp       Timestamp for filtering dependency versions (-1 in order not to filter)
     * @param dbContext       Database connection context (needed only for filtering by timestamp)
     * @return A dependency set (including all transitive dependencies) of given Maven coordinate
     */
    public Set<Dependency> resolveFullDependencySetOnline(String mavenCoordinate, long timestamp,
                                                          DSLContext dbContext) {
        var artifacts = Arrays.stream(
                Maven.resolver()
                        .resolve(mavenCoordinate)
                        .withTransitivity()
                        .asResolvedArtifact()
        ).collect(Collectors.toList());
        if (artifacts.size() < 1) {
            throw new RuntimeException("Could not resolve artifact " + mavenCoordinate);
        }
        var dependencySet = Arrays.stream(artifacts.get(0).getDependencies())
                .map(d -> new Dependency(
                        d.getCoordinate().getGroupId(),
                        d.getCoordinate().getArtifactId(),
                        d.getResolvedVersion(),
                        new ArrayList<>(),
                        d.getScope().name(),
                        d.isOptional(),
                        d.getCoordinate().getType().toString(),
                        d.getCoordinate().getClassifier()))
                .collect(Collectors.toSet());
        if (timestamp != -1) {
            dependencySet = this.filterDependenciesByTimestamp(
                    dependencySet, new Timestamp(timestamp), dbContext);
        }
        return dependencySet;
    }

    /**
     * Resolves full dependency set online using ShrinkWrap's MavenResolver.
     *
     * @param mavenCoordinate Maven coordinate to resolve in the form of groupId:artifactId:version
     * @return A dependency set (including all transitive dependencies) of given Maven coordinate
     */
    public Set<Dependency> resolveFullDependencySetOnline(String mavenCoordinate) {
        return resolveFullDependencySetOnline(mavenCoordinate, -1, null);
    }
}
