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
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.data.DependencyEdge;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class DependencyGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    public static void main(String[] args) throws SQLException {
        var tsStart = System.currentTimeMillis();
        var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_java", "fastenro");
        var graphBuilder = new DependencyGraphBuilder();
        var graph = graphBuilder.buildDependencyGraph(dbContext);
        var tsEnd = System.currentTimeMillis();
        logger.info("____________________________________________________________________");
        logger.info("Graph has {} nodes and {} edges ({} ms)", graph.vertexSet().size(),
                graph.edgeSet().size(), tsEnd - tsStart);
    }

    public Map<Revision, List<Dependency>> getDependencyList(DSLContext dbContext) {
        return dbContext.select(Packages.PACKAGES.PACKAGE_NAME,
                PackageVersions.PACKAGE_VERSIONS.VERSION,
                Dependencies.DEPENDENCIES.METADATA,
                PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
                .from(Packages.PACKAGES)
                .rightJoin(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .leftJoin(Dependencies.DEPENDENCIES)
                .on(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
                .where(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .and(PackageVersions.PACKAGE_VERSIONS.CREATED_AT.isNotNull())
                .fetch()
                .parallelStream()
                .map(x -> {
                    if (x.component1().split(Constants.mvnCoordinateSeparator).length < 2) {
                        logger.warn("Skipping invalid coordinate: {}", x.component1());
                        return null;
                    }

                    var artifact = x.component1().split(Constants.mvnCoordinateSeparator)[0];
                    var group = x.component1().split(Constants.mvnCoordinateSeparator)[1];

                    if (x.component3() != null) {
                        return new AbstractMap.SimpleEntry<>(new Revision(artifact, group, x.component2(), x.component4()),
                                Dependency.fromJSON(new JSONObject(x.component3().data())));
                    } else {
                        return new AbstractMap.SimpleEntry<>(new Revision(artifact, group, x.component2(), x.component4()),
                                Dependency.empty);
                    }
                }).filter(Objects::nonNull)
                .collect(Collectors.toConcurrentMap(
                        AbstractMap.SimpleEntry::getKey,
                        x -> List.of(x.getValue()),
                        (x, y) -> {
                            var z = new ArrayList<Dependency>();
                            z.addAll(x);
                            z.addAll(y);
                            return z;
                        }
                ));
    }

    public List<Revision> findMatchingRevisions(List<Revision> revisions,
                                                List<Dependency.VersionConstraint> constraints) {
        if (revisions == null) {
            return Collections.emptyList();
        }
        return revisions.stream().filter(r -> {
            for (var constraint : constraints) {
                if (checkVersionLowerBound(constraint, r.version) && checkVersionUpperBound(constraint, r.version)) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList());
    }

    private boolean checkVersionLowerBound(Dependency.VersionConstraint constraint, DefaultArtifactVersion version) {
        if (constraint.isLowerHardRequirement) {
            return version.compareTo(new DefaultArtifactVersion(constraint.lowerBound)) >= 0;
        } else {
            return version.compareTo(new DefaultArtifactVersion(constraint.lowerBound)) > 0;
        }
    }

    private boolean checkVersionUpperBound(Dependency.VersionConstraint constraint, DefaultArtifactVersion version) {
        if (constraint.isUpperHardRequirement) {
            return version.compareTo(new DefaultArtifactVersion(constraint.upperBound)) <= 0;
        } else {
            return version.compareTo(new DefaultArtifactVersion(constraint.upperBound)) < 0;
        }
    }

    public Graph<Revision, DependencyEdge> buildDependencyGraph(DSLContext dbContext) {
        var startTs = System.currentTimeMillis();

        var dependencies = getDependencyList(dbContext);
        logger.info("Retrieved {} package versions: {} ms", dependencies.size(), System.currentTimeMillis() - startTs);

        startTs = System.currentTimeMillis();
        var productRevisionMap = dependencies.keySet().stream().collect(Collectors.toMap(
                Revision::product,
                List::of,
                (x, y) -> {
                    var z = new ArrayList<Revision>();
                    z.addAll(x);
                    z.addAll(y);
                    return z;
                })
        );
        logger.info("Indexed {} products: {} ms", productRevisionMap.size(),
                System.currentTimeMillis() - startTs);

        startTs = System.currentTimeMillis();
        logger.info("Creating dependency graph");

        var dependencyGraph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);

        logger.info("Adding dependency graph nodes");
        dependencies.keySet().forEach(dependencyGraph::addVertex);

        logger.info("Adding dependency graph edges");
        long idx = 0;
        var edgelessNodes = new HashSet<String>();
        for (var entry : dependencies.entrySet()) {
            var source = entry.getKey();
            for (var dependency : entry.getValue()) {
                if (dependency.equals(Dependency.empty)) {
                    edgelessNodes.add(dependency.toMavenCoordinate());
                    continue;
                }
                var potentialRevisions = productRevisionMap.get(dependency.product());
                var matchingRevisions = findMatchingRevisions(potentialRevisions, dependency.versionConstraints);
                logger.debug("{} matched for {}", dependency.getVersion(), String.join("; ",
                        matchingRevisions.stream().map(Revision::toString).collect(Collectors.toSet())));
                for (var target : matchingRevisions) {
                    var edge = new DependencyEdge(idx++, dependency.scope, dependency.optional, dependency.exclusions);
                    dependencyGraph.addEdge(source, target, edge);
                }
            }
        }
        logger.debug("{} nodes do not have any edges/dependencies", edgelessNodes.size());
        logger.info("Created graph: {} ms", System.currentTimeMillis() - startTs);
        logger.info("Successfully generated ecosystem-wide dependency graph");
        return dependencyGraph;
    }
}