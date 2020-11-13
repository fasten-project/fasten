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

import com.github.zafarkhaja.semver.Version;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.MavenProduct;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.data.graph.DependencyEdge;
import eu.fasten.core.maven.data.graph.DependencyNode;
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

    private final boolean usePagination;

    public DependencyGraphBuilder() {
        this(false);
    }

    public DependencyGraphBuilder(boolean usePagination) {
        this.usePagination = usePagination;
    }

    public static void main(String[] args) throws SQLException {
        var tsStart = System.currentTimeMillis();
        var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_java", "fastenro");
        var graphBuilder = new DependencyGraphBuilder();
        var graph = graphBuilder.buildDependencyGraph(dbContext);
        var tsEnd = System.currentTimeMillis();
        System.out.println("____________________________________________________________________");
        System.out.println("Graph has " + graph.vertexSet().size() + " nodes and "
                + graph.edgeSet().size() + " edges (" + (tsEnd - tsStart) +" ms)");
    }

    public Map<Revision, List<Dependency>> getDependencyList(DSLContext dbContext) {

        return dbContext
                .select(Packages.PACKAGES.PACKAGE_NAME,
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
//                .limit(10000)
                .fetch()
                .stream()
//                .parallel()
                .map(x -> {
                    var artifact = x.component1().split(":")[0];
                    var group = x.component1().split(":")[1];
                    return new AbstractMap.SimpleEntry<>(new Revision(artifact, group, x.component2(), x.component4()),
                            Dependency.fromJSON(new JSONObject(x.component3().data())));
                }).collect(Collectors.toConcurrentMap(
                  x -> x.getKey(),
                  x -> List.of(x.getValue()),
                 (x, y) -> {var z = new ArrayList<Dependency>(); z.addAll(x); z.addAll(y); return z;}
                ));
    }


    /**
     * Find all revisions that match a product in the set of revisions
     */
    public List<Revision> findByProduct(Set<Revision> revisions, MavenProduct product) {
        // TODO This does too many linear scans; we need some other data structure to store the revision set
        return revisions.stream().filter(x -> ((MavenProduct) x).equals(product)).collect(Collectors.toList());
    }

    public static List<Revision> findMatchingRevisions(List<Revision> revisions,
                                                       List<Dependency.VersionConstraint> constraints) {

        // TODO Unify constraints into a single semver expression
        var semver1 = constraints.get(0).toSemVer();

        revisions.stream().filter(x -> semver1.interpret(x.version)).collect(Collectors.toList());

        return revisions;
    }


    public Graph<DependencyNode, DependencyEdge> buildDependencyGraph(DSLContext dbContext) {
        var dependencies = getDependencyList(dbContext);

        long startTs;

        startTs = System.currentTimeMillis();
        logger.info("Indexing dependency pairs");

        var dependencyGraph = new DefaultDirectedGraph<DependencyNode, DependencyEdge>(DependencyEdge.class);
        dependencies.keySet().forEach(x -> new DependencyNode(x, x.createdAt));

        long idx = 0;
        for (var entry : dependencies.entrySet()) {
            var source = new DependencyNode(entry.getKey(), entry.getKey().createdAt);

            for (var dependency : entry.getValue()) {
                var potentialRevisions = findByProduct(dependencies.keySet(),
                        new MavenProduct(dependency.getGroupId(), dependency.artifactId));

                var matching= findMatchingRevisions(potentialRevisions, dependency.versionConstraints);
                for (var m : matching) {
                    var target = new DependencyNode(m, m.createdAt);
                    var edge = new DependencyEdge(idx++, dependency.scope, dependency.optional, dependency.exclusions);
                    dependencyGraph.addEdge(source, target, edge);
                }

            }
        }
        logger.info(String.format("Created graph: %d ms",  System.currentTimeMillis() - startTs));
        logger.info("Successfully generated ecosystem-wide dependency graph");
        return dependencyGraph;
    }
}