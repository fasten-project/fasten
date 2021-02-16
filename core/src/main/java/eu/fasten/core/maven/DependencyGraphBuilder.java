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
import eu.fasten.core.maven.utils.DependencyGraphUtilities;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DependencyGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    public static void main(String[] args) throws Exception {
        var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_java", "fastenro", true);

        String path = "mavengraph";
        if (args.length > 0 && args[0] != null) {
            path = args[0];
        }

        if (DependencyGraphUtilities.loadDependencyGraph(path).isEmpty()) {
            DependencyGraphUtilities.buildDependencyGraphFromScratch(dbContext, path);
        }
    }

    public Map<Revision, List<Dependency>> getDependencyList(DSLContext dbContext) {
        return dbContext.select(PackageVersions.PACKAGE_VERSIONS.ID,
                Packages.PACKAGES.PACKAGE_NAME,
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
                    if (x.component2().split(Constants.mvnCoordinateSeparator).length < 2) {
                        logger.warn("Skipping invalid coordinate: {}", x.component2());
                        return null;
                    }

                    var artifact = x.component2().split(Constants.mvnCoordinateSeparator)[0].replaceAll("[\\n\\t ]", "");
                    var group = x.component2().split(Constants.mvnCoordinateSeparator)[1].replaceAll("[\\n\\t ]", "");

                    if (x.component4() != null) {
                        return new AbstractMap.SimpleEntry<>(new Revision(x.component1(), artifact, group,
                                x.component3().replaceAll("[\\n\\t ]", ""),
                                x.component5()), Dependency.fromJSON(new JSONObject(x.component4().data())));
                    } else {
                        return new AbstractMap.SimpleEntry<>(new Revision(x.component1(), artifact, group,
                                x.component3().replaceAll("[\\n\\t ]", ""),
                                x.component5()), Dependency.empty);
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
                if ((constraint.toString().startsWith("[") || constraint.toString().startsWith("("))
                        && (constraint.toString().endsWith("]") || constraint.toString().endsWith(")"))) {
                    if (checkVersionLowerBound(constraint, r.version) &&
                            checkVersionUpperBound(constraint, r.version)) {
                        return true;
                    }
                } else {
                    if (constraint.lowerBound.equals(constraint.upperBound) &&
                            new DefaultArtifactVersion(constraint.lowerBound).equals(r.version)) {
                        return true;
                    }
                }
            }
            return false;
        }).collect(Collectors.toList());
    }

    private boolean checkVersionLowerBound(Dependency.VersionConstraint constraint, DefaultArtifactVersion version) {
        if (constraint.lowerBound.isEmpty()) {
            return true;
        }
        if (constraint.isLowerHardRequirement) {
            return version.compareTo(new DefaultArtifactVersion(constraint.lowerBound)) >= 0;
        } else {
            return version.compareTo(new DefaultArtifactVersion(constraint.lowerBound)) > 0;
        }
    }

    private boolean checkVersionUpperBound(Dependency.VersionConstraint constraint, DefaultArtifactVersion version) {
        if (constraint.upperBound.isEmpty()) {
            return true;
        }
        if (constraint.isUpperHardRequirement) {
            return version.compareTo(new DefaultArtifactVersion(constraint.upperBound)) <= 0;
        } else {
            return version.compareTo(new DefaultArtifactVersion(constraint.upperBound)) < 0;
        }
    }

    public Graph<Revision, DependencyEdge> buildDependencyGraph(DSLContext dbContext) {
        var startTs = System.currentTimeMillis();
        var startDepRet = System.currentTimeMillis();

        var dependencies = getDependencyList(dbContext);
        logger.info("Retrieved {} package versions: {} ms", dependencies.size(),
                System.currentTimeMillis() - startDepRet);

        var startIdx = System.currentTimeMillis();
        var productRevisionMap = dependencies.keySet().stream().collect(Collectors.toConcurrentMap(
                Revision::product,
                List::of,
                (x, y) -> {
                    var z = new ArrayList<Revision>();
                    z.addAll(x);
                    z.addAll(y);
                    return z;
                })
        );
        logger.debug("Indexed {} products: {} ms", productRevisionMap.size(), System.currentTimeMillis() - startIdx);

        logger.info("Creating dependency graph");
        var dependencyGraph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);

        logger.info("Adding dependency graph nodes");
        dependencies.keySet().forEach(dependencyGraph::addVertex);

        logger.info("Generating graph edges");
        var startGenEdgesTs = System.currentTimeMillis();
        var allEdges = dependencies.entrySet().parallelStream().map(e -> {
            var source = e.getKey();
            var edges = new ArrayList<DependencyEdge>();
            for (var dependency : e.getValue()) {
                if (dependency.equals(Dependency.empty)) {
                    continue;
                }
                var potentialRevisions = productRevisionMap.get(dependency.product());
                var matchingRevisions = findMatchingRevisions(potentialRevisions, dependency.versionConstraints);
                for (var target : matchingRevisions) {
                    var edge = new DependencyEdge(source, target, dependency.scope, dependency.optional,
                            dependency.exclusions, dependency.type);
                    edges.add(edge);
                }
            }
            return edges;
        }).flatMap(Collection::stream).collect(Collectors.toList());
        logger.debug("Generated {} edges: {} ms", allEdges.size(), System.currentTimeMillis() - startGenEdgesTs);

        var startAddEdgesTs = System.currentTimeMillis();
        allEdges.forEach(e -> dependencyGraph.addEdge(e.source, e.target, e));
        logger.debug("Added {} edges to the graph: {} ms", allEdges.size(),
                System.currentTimeMillis() - startAddEdgesTs);

        logger.info("Maven dependency graph generated: {} ms", System.currentTimeMillis() - startTs);
        return dependencyGraph;
    }
}
