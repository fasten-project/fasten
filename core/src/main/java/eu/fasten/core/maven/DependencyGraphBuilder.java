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
import eu.fasten.core.maven.data.graph.DependencyEdge;
import eu.fasten.core.maven.data.graph.DependencyNode;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DependencyGraphBuilder {

    public static void main(String[] args) throws SQLException {
        var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5433/fasten_java", "fasten");
        var graphBuilder = new DependencyGraphBuilder();
        graphBuilder.buildDependencyGraph(dbContext);
    }

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    public Graph<DependencyNode, DependencyEdge> buildDependencyGraph(DSLContext dbContext) {
        logger.info("Obtaining timestamps of artifacts for generating dependency graph");
        var timestampsResult = dbContext
                .select(
                        Packages.PACKAGES.PACKAGE_NAME,
                        PackageVersions.PACKAGE_VERSIONS.VERSION,
                        PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .and(Packages.PACKAGES.ID.greaterThan(0L))
                .fetch();
        var timestampedArtifacts = new HashMap<Dependency, Timestamp>();
        for (var record : timestampsResult) {
            var mavenCoordinate = record.component1() + Constants.mvnCoordinateSeparator + record.component2();
            var timestamp = record.component3();
            if (timestamp == null) {
                timestamp = new Timestamp(-1);
            }
            try {
                timestampedArtifacts.put(new Dependency(mavenCoordinate), timestamp);
            } catch (IllegalArgumentException e) {
                logger.error("Error parsing Maven coordinate '" + mavenCoordinate + "'", e);
            }
        }
        timestampsResult = null;
        logger.info("Obtaining dependency data for generating dependency graph");
        var dependenciesResult = dbContext
                .select(Packages.PACKAGES.PACKAGE_NAME,
                        PackageVersions.PACKAGE_VERSIONS.VERSION,
                        Dependencies.DEPENDENCIES.METADATA)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .join(Dependencies.DEPENDENCIES)
                .on(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
                .where(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetch();
        if (dependenciesResult == null || dependenciesResult.isEmpty()) {
            return null;
        }
        var dependencies = new HashMap<Dependency, List<Dependency>>();
        for (var record : dependenciesResult) {
            var mavenCoordinate = record.component1() + Constants.mvnCoordinateSeparator + record.component2();
            var artifact = new Dependency(mavenCoordinate);
            var dependency = Dependency.fromJSON(new JSONObject(record.component3().data()));
            var depList = dependencies.get(artifact);
            if (depList == null) {
                dependencies.put(artifact, List.of(dependency));
            } else {
                var newDepList = new ArrayList<>(depList);
                newDepList.add(dependency);
                dependencies.put(artifact, newDepList);
            }
        }
        dependenciesResult = null;
        logger.info("Generating global dependency graph");
        var dependencyGraph = new DefaultDirectedGraph<DependencyNode, DependencyEdge>(DependencyEdge.class);
        for (var entry : timestampedArtifacts.entrySet()) {
            dependencyGraph.addVertex(new DependencyNode(entry.getKey(), entry.getValue()));
        }
        long id = 0;
        for (var entry : dependencies.entrySet()) {
            var source = new DependencyNode(entry.getKey(), timestampedArtifacts.get(entry.getKey()));
            if (!dependencyGraph.containsVertex(source)) {
                dependencyGraph.addVertex(source);
            }
            var dependencyList = entry.getValue();
            for (var dependency : dependencyList) {
                var targetDependency = new Dependency(dependency.toMavenCoordinate());
                var targetTimestamp = timestampedArtifacts.get(targetDependency) != null
                        ? timestampedArtifacts.get(targetDependency) : new Timestamp(-1);
                var target = new DependencyNode(targetDependency, targetTimestamp);
                if (!dependencyGraph.containsVertex(target)) {
                    dependencyGraph.addVertex(target);
                }
                var edge = new DependencyEdge(id++, dependency.scope, dependency.optional, dependency.exclusions);
                dependencyGraph.addEdge(source, target, edge);
            }
        }
        logger.info("Successfully generated ecosystem-wide dependency graph");
        return dependencyGraph;
    }
}