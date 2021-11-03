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

package eu.fasten.core.pypi;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.pypi.data.DependencyEdge;
import eu.fasten.core.pypi.data.PyPiProduct;
import eu.fasten.core.pypi.data.Revision;
import eu.fasten.core.pypi.utils.DependencyGraphUtilities;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
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
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(name = "GraphPypiResolver")
public class GraphPypiResolver implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GraphPypiResolver.class);

    @CommandLine.Option(names = {"-p", "--serializedPath"},
            paramLabel = "PATH",
            description = "Path to load a serialized dependency graph from",
            required = true)
    protected String serializedPath;

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


    static Graph<Revision, DependencyEdge> dependencyGraph;
    static Graph<Revision, DependencyEdge> dependentGraph;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new GraphPypiResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {

        try {
            var optDependencyGraph = DependencyGraphUtilities.loadDependencyGraph(serializedPath);
            if (optDependencyGraph.isPresent()) {
                dependencyGraph = optDependencyGraph.get();
                dependentGraph = DependencyGraphUtilities.invertDependencyGraph(dependencyGraph);
            }
        } catch (Exception e) {
            logger.warn("Could not load serialized dependency graph from {}\n", serializedPath, e);
        }

        DSLContext dbContext;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
        } catch (SQLException e) {
            logger.error("Could not connect to the database", e);
            return;
        }

        repl(dbContext);
    }

    public void repl(DSLContext db) {
        System.out.println("Query format: package:version");
        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                var input = scanner.nextLine();

                if (input.equals("")) continue;
                if (input.equals("quit") || input.equals("exit")) break;

                var parts = input.split(":");
                if (parts.length < 2) {
                    System.out.println("Wrong input: " + input + ". Format is: package:version");
                    continue;
                }

                ObjectLinkedOpenHashSet<Revision> revisions;
                var startTS = System.currentTimeMillis();
                try {
                    revisions = resolveDependents(parts[0], parts[1], getCreatedAt(parts[0], parts[1], db), false);
                } catch (Exception e) {
                    System.err.println("Error retrieving revisions: " + e.getMessage());
                    e.printStackTrace(System.err);
                    continue;
                }

                for (var rev : revisions.stream().sorted(Comparator.comparing(Revision::toString)).
                        collect(Collectors.toList())) {
                    System.out.println(rev.toString());
                }
                System.err.println(revisions.size() + " revisions, " + (System.currentTimeMillis() - startTS) + " ms");
            }
        }
    }


    /**
     * Resolves the dependents of the provided {@link Revision}, as specified by the provided revision details. The
     * provided timestamp determines which nodes will be ignored when traversing dependent nodes. Effectively, the
     * returned dependent set only includes nodes that where released AFTER the provided timestamp.
     */
    public ObjectLinkedOpenHashSet<Revision> resolveDependents(String packageName, String version, long timestamp,
                                                               boolean transitive) {
        return dependentBFS(packageName, version, timestamp, transitive);
    }

    /**
     * Performs a Breadth-First Search on the {@param dependentGraph} to determine the revisions that depend on
     * the revision indicated by the first 3 parameters, at the indicated {@param timestamp}.
     *
     * @param timestamp  - The cut-off timestamp. The returned dependents have been released after the provided timestamp
     * @param transitive - Whether the BFS should recurse into the graph
     */
    public ObjectLinkedOpenHashSet<Revision> dependentBFS(String packageName, String version, long timestamp,
                                                          boolean transitive) {
        var revision = new Revision(packageName, version, new Timestamp(timestamp));

        if (!dependentGraph.containsVertex(revision)) {
            throw new RuntimeException("Revision " + packageName + " is not in the dependents graph. Probably it is missing in the database");
        }

        var workQueue = new ArrayDeque<>(filterDependentsByTimestamp(Graphs.successorListOf(dependentGraph, revision), timestamp));

        var result = new ObjectLinkedOpenHashSet<>(workQueue);

        if (!transitive) {
            return new ObjectLinkedOpenHashSet<>(workQueue);
        }

        while (!workQueue.isEmpty()) {
            var rev = workQueue.poll();
            if (rev != null) {
                result.add(rev);
                logger.debug("Successors for {}:{}: deps: {}, queue: {} items",
                        rev.packageName, rev.version,
                        workQueue.size(), workQueue.size());
            }
            if (!dependentGraph.containsVertex(rev)) {
                throw new RuntimeException("Revision " + rev + " is not in the dependents graph. Probably it is missing in the database");
            }
            var dependents = filterDependentsByTimestamp(Graphs.successorListOf(dependentGraph, rev), timestamp);
            for (var dependent : dependents) {
                if (!result.contains(dependent)) {
                    workQueue.add(dependent);
                }
            }
        }
        return result;
    }

    protected List<Revision> filterDependentsByTimestamp(List<Revision> successors, long timestamp) {
        return successors.stream().
                filter(revision -> revision.createdAt.getTime() >= timestamp).
                collect(Collectors.toList());
    }

    public void buildDependencyGraph(DSLContext dbContext, String serializedGraphPath) throws Exception {
        var graphOpt = DependencyGraphUtilities.loadDependencyGraph(serializedGraphPath);
        if (graphOpt.isEmpty()) {
            dependencyGraph = DependencyGraphUtilities.buildDependencyGraphFromScratch(dbContext, serializedGraphPath);
        } else {
            dependencyGraph = graphOpt.get();
        }
        dependentGraph = DependencyGraphUtilities.invertDependencyGraph(dependencyGraph);
    }
    
    private long getCreatedAt(String packageName, String version, DSLContext context) {
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
}
