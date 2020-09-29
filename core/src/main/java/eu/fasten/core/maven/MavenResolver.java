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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            DSLContext dbContext;
            try {
                dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
            } catch (SQLException e) {
                System.err.println("Could not connect to the database: " + e.getMessage());
                return;
            }
            var dependencySet = this.resolveFullDependencySet(group, artifact, version,
                    timestamp, onlineMode, dbContext);
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

    public Set<Dependency> resolveFullDependencySet(String groupId, String artifactId,
                                                    String version, long timestamp,
                                                    boolean onlineMode, DSLContext dbContext) {
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
                    parentArtifact.getArtifactId(), parentArtifact.getVersion(), onlineMode,
                    dbContext);
            dependencyTree = filterOptionalDependencies(dependencyTree);
            dependencyTree = filterDependencyTreeByScope(dependencyTree);
            dependencyTree = filterDependencyTreeByExclusions(dependencyTree);
            var currentDependencySet = collectDependencyTree(dependencyTree);
            if (timestamp != -1) {
                currentDependencySet = filterDependenciesByTimestamp(dependencySet,
                        new Timestamp(timestamp), dbContext);
            }
            dependencySet.addAll(currentDependencySet);
        }
        dependencySet.remove(new Dependency(groupId, artifactId, version));
        return dependencySet;
    }

    public DependencyTree buildFullDependencyTree(String groupId, String artifactId, String version,
                                                  boolean onlineMode, DSLContext dbContext) {
        var artifact = new Dependency(groupId, artifactId, version);
        List<Dependency> dependencies = new ArrayList<>();
        if (onlineMode) {
            var parentCoordinate = artifact.getGroupId()
                    + Constants.mvnCoordinateSeparator + artifact.getArtifactId()
                    + Constants.mvnCoordinateSeparator + artifact.getVersion();
            dependencies.addAll(this.getDependenciesOnline(parentCoordinate));
        } else {
            dependencies.addAll(this.getArtifactDependenciesFromDatabase(
                    artifact.getGroupId(), artifact.getArtifactId(),
                    artifact.getVersion(), dbContext
            ));
        }
        DependencyTree dependencyTree;
        if (dependencies.isEmpty()) {
            dependencyTree = new DependencyTree(artifact, new ArrayList<>());
        } else {
            var childTrees = new ArrayList<DependencyTree>();
            for (var dep : dependencies) {
                childTrees.add(this.buildFullDependencyTree(dep.getGroupId(), dep.getArtifactId(),
                        dep.getVersion(), onlineMode, dbContext));
            }
            dependencyTree = new DependencyTree(artifact, childTrees);
        }
        return dependencyTree;
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

    public DependencyTree filterDependencyTreeByScope(DependencyTree dependencyTree) {
        // TODO: Implement
        return dependencyTree;
    }

    public DependencyTree filterDependencyTreeByExclusions(DependencyTree dependencyTree) {
        // TODO: Implement
        return dependencyTree;
    }

    public Set<Dependency> collectDependencyTree(DependencyTree dependencyTree) {
        // TODO: Implement usage of distance
        //       If several versions are available, the closest one should be chosen
        var dependencySet = new HashSet<Dependency>();
        dependencySet.add(dependencyTree.artifact);
        for (var childTree : dependencyTree.dependencies) {
            dependencySet.addAll(collectDependencyTree(childTree));
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
            e.printStackTrace(System.err);
            return null;
        }
        return new Dependency(parentCoordinate);
    }

    public List<Dependency> getDependenciesOnline(String mavenCoordinate) {
        var artifacts = Arrays.stream(
                Maven.resolver()
                        .resolve(mavenCoordinate)
                        .withoutTransitivity()
                        .asResolvedArtifact()
        ).collect(Collectors.toList());
        if (artifacts.size() < 1) {
            throw new RuntimeException("Could not resolve artifact " + mavenCoordinate);
        }
        return Arrays.stream(artifacts.get(0).getDependencies())
                .map(d -> new Dependency(
                        d.getCoordinate().getGroupId(),
                        d.getCoordinate().getArtifactId(),
                        d.getResolvedVersion(),
                        new ArrayList<>(),
                        d.getScope().name(),
                        d.isOptional(),
                        d.getCoordinate().getType().toString(),
                        d.getCoordinate().getClassifier()))
                .collect(Collectors.toList());
    }

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
}
