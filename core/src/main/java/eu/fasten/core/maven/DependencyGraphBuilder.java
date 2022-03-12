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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyEdge;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.data.VersionConstraint;
import eu.fasten.core.maven.utils.DependencyGraphUtilities;
import picocli.CommandLine;

@CommandLine.Command(name = "DependencyGraphBuilder")
public class DependencyGraphBuilder implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    @CommandLine.Option(names = {"-p", "--serializedPath"},
            paramLabel = "PATH",
            description = "Path to load a serialized Maven dependency graph from",
            required = true)
    protected String serializedPath;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            required = true)
    protected String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            required = true)
    protected String dbUser;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new DependencyGraphBuilder()).execute(args);
        System.exit(exitCode);
    }

     @Override
    public void run()  {

        DSLContext dbContext;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
        } catch (Exception e) {
            logger.warn("Could not connect to Database", e);
            return;
        }
        try {
            if (DependencyGraphUtilities.loadDependencyGraph(serializedPath).isEmpty()) {
                DependencyGraphUtilities.buildDependencyGraphFromScratch(dbContext, serializedPath);
            }
        } catch (Exception e) {
            logger.warn("Could not load serialized dependency graph from {}\n", serializedPath, e);
            return;
        }
    }

    public Map<Revision, List<Dependency>> getDependencyListByRevision(DSLContext dbContext) {
        var result = dbContext.select(PackageVersions.PACKAGE_VERSIONS.ID,
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
                .fetch();
        return result.parallelStream() //
                .map(x -> {
                    if (x.component2().split(":").length < 2) {
                        logger.warn("Skipping invalid coordinate: {}", x.component2());
                        return null;
                    }

                    var artifact = strip(x.component2().split(":")[0]);
                    var group = strip(x.component2().split(":")[1]);

                    if (x.component4() != null) {
                        return new AbstractMap.SimpleEntry<>(new Revision(x.component1(), artifact, group,
                                strip(x.component3()),
                                x.component5()), Dependency.fromJSON(new JSONObject(x.component4().data())));
                    } else {
                        return new AbstractMap.SimpleEntry<>(new Revision(x.component1(), artifact, group,
                                strip(x.component3()),
                                x.component5()), Dependency.empty);
                    }
                }) //
                .filter(Objects::nonNull)
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

    private static String strip(String s) {
        return s.replaceAll("[\\n\\t ]", "");
    }

    public List<Revision> findMatchingRevisions(List<Revision> revisions, Set<VersionConstraint> constraints) {
        if (revisions == null) {
            return Collections.emptyList();
        }
        return revisions.stream().filter(r -> {
            for (var constraint : constraints) {
                var spec = constraint.toString();
                if ((spec.startsWith("[") || spec.startsWith("(")) && (spec.endsWith("]") || spec.endsWith(")"))) {
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

    private boolean checkVersionLowerBound(VersionConstraint constraint, DefaultArtifactVersion version) {
        if (constraint.lowerBound.isEmpty()) {
            return true;
        }
        if (constraint.isLowerHardRequirement) {
            return version.compareTo(new DefaultArtifactVersion(constraint.lowerBound)) >= 0;
        } else {
            return version.compareTo(new DefaultArtifactVersion(constraint.lowerBound)) > 0;
        }
    }

    private boolean checkVersionUpperBound(VersionConstraint constraint, DefaultArtifactVersion version) {
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

        var dependencies = getDependencyListByRevision(dbContext);
        logger.info("Retrieved {} package versions: {} ms", dependencies.size(), msSince(startDepRet));

        var startIdx = System.currentTimeMillis();
        var productRevisionMap = dependencies.keySet().stream().collect(Collectors.toConcurrentMap(
                r -> r.product(), // key map
                r -> List.of(r), // value map
                (x, y) -> { // merge values with same key
                    var z = new ArrayList<Revision>();
                    z.addAll(x);
                    z.addAll(y);
                    return z;
                })
        );
        logger.debug("Indexed {} products: {} ms", productRevisionMap.size(), msSince(startIdx));

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
        logger.debug("Generated {} edges: {} ms", allEdges.size(), msSince(startGenEdgesTs));

        var startAddEdgesTs = System.currentTimeMillis();
        allEdges.forEach(e -> dependencyGraph.addEdge(e.source, e.target, e));
        logger.debug("Added {} edges to the graph: {} ms", allEdges.size(),
                msSince(startAddEdgesTs));

        logger.info("Maven dependency graph generated: {} ms", msSince(startTs));
        return dependencyGraph;
    }

    private long msSince(long startDepRet) {
        return System.currentTimeMillis() - startDepRet;
    }
}
