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

package eu.fasten.core.merge;

import ch.qos.logback.classic.Level;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "MavenMergerBenchmark", mixinStandardHelpOptions = true)
public class Benchmark implements Runnable {

    @CommandLine.Option(names = {"-a", "--artifact"},
            paramLabel = "ARTIFACT",
            description = "Maven coordinate of an artifact")
    String artifact;

    @CommandLine.Option(names = {"-d", "--dependencies"},
            paramLabel = "DEPS",
            description = "Maven coordinates of dependencies",
            split = ",")
    List<String> dependencies;

    @CommandLine.Option(names = {"-md", "--database"},
            paramLabel = "dbURL",
            description = "Metadata database URL for connection")
    String dbUrl;

    @CommandLine.Option(names = {"-du", "--user"},
            paramLabel = "dbUser",
            description = "Metadata database user name")
    String dbUser;

    @CommandLine.Option(names = {"-gd", "--graphdb_dir"},
            paramLabel = "dir",
            description = "Path to directory with RocksDB database")
    String graphDbDir;

    @CommandLine.Option(names = {"-p", "--path"},
            paramLabel = "path",
            description = "Path to merged call graph file")
    String path;

    private static final Logger logger = LoggerFactory.getLogger(Benchmark.class);

    private DSLContext dbContext;
    private RocksDao rocksDao;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Benchmark()).execute(args));
    }

    @Override
    public void run() {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);

        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
            rocksDao = RocksDBConnector.createReadOnlyRocksDBAccessObject(graphDbDir);
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Could not connect to the metadata database: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            logger.error("Could not connect to the graph database: " + e.getMessage());
            System.exit(1);
        }

        logger.info("Loading and generating call graphs...");
        var callgraph = getLocalCallGraph();
        var directedGraph = getDatabaseCallGraph();

        System.out.println("==========================");
        System.out.println("+        BENCHMARK       +");
        System.out.println("==========================");

        var localResolvedGraph = ERCGToSet(callgraph);
        var databaseResolvedGraph = DirectedGraphToSet(directedGraph);

        System.out.format("%14s%4s%12s%4s%12s\n", "", "|", "DATABASE", "|", "LOCAL");
        System.out.println("--------------------------------------------------");
//        System.out.format("%14s%4s%12d%4s%12d\n", "Edges", "|",
//                directedGraph.numArcs(), "|", callgraph.getGraph().getResolvedCalls().size());
        System.out.format("%14s%4s%12d%4s%12d\n", "Unique edges", "|",
                databaseResolvedGraph.size(), "|", localResolvedGraph.size());

        var missedEdgesInDatabaseMerge =
                getMissedEdges(localResolvedGraph, databaseResolvedGraph);
        var missedEdgesInLocalMerge =
                getMissedEdges(databaseResolvedGraph, localResolvedGraph);

        System.out.println("--------------------------------------------------");
        System.out.println("Missing in database call graph: " + missedEdgesInDatabaseMerge.size());
        System.out.println("Missing in local call graph: " + missedEdgesInLocalMerge.size());
        System.out.println("Database - Local total: "
                + ((databaseResolvedGraph.size() > localResolvedGraph.size()) ? "+" : "-")
                + Math.abs(databaseResolvedGraph.size() - localResolvedGraph.size()));
        System.out.println("--------------------------------------------------");

        System.out.println("==========================");
    }

    private Set<Pair<String, String>> getMissedEdges(final Set<Pair<String, String>> referenceGraph,
                                                     final Set<Pair<String, String>> callgraph) {
        var missedEdges = new HashSet<Pair<String, String>>();
        for (var arc : referenceGraph) {
            if (!callgraph.contains(arc)) {
                missedEdges.add(arc);
            }
        }
        return missedEdges;
    }

    private ExtendedRevisionJavaCallGraph getLocalCallGraph() {
        ExtendedRevisionJavaCallGraph callgraph = null;
        try {
            var tokener = new JSONTokener(new FileReader(path));
            callgraph = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));
        } catch (FileNotFoundException e) {
            logger.error("Couldn't read merged call graph file", e);
            System.exit(1);
        }
        return callgraph;
    }

    private Set<Pair<String, String>> ERCGToSet(final ExtendedRevisionJavaCallGraph callgraph) {
        var localMethodsMap = callgraph.mapOfAllMethods();
        var localResolvedGraph = new HashSet<Pair<String, String>>();

//        for (var arc : callgraph.getGraph().getResolvedCalls().keySet()) {
//            var newArc = ImmutablePair
//                    .of(localMethodsMap.get(arc.firstInt()).getUri().toString(),
//                            localMethodsMap.get(arc.secondInt()).getUri().toString());
//            localResolvedGraph.add(newArc);
//        }
        return localResolvedGraph;
    }

    private Set<Pair<String, String>> DirectedGraphToSet(final DirectedGraph mergedDirectedGraph) {
        var databaseMethodsMap = getMethodsMap(dbContext, mergedDirectedGraph.nodes());
        var databaseResolvedGraph = new HashSet<Pair<String, String>>();

        for (var source : mergedDirectedGraph.nodes()) {
            for (var target : mergedDirectedGraph.successors(source)) {
                var newArc = ImmutablePair.of(databaseMethodsMap.get(source),
                        databaseMethodsMap.get(target));
                databaseResolvedGraph.add(newArc);
            }
        }
        return databaseResolvedGraph;
    }

    private DirectedGraph getDatabaseCallGraph() {
        final var depSet = dependencies;
        var merger = new CGMerger(depSet, dbContext, rocksDao);
        return merger.mergeWithCHA(artifact);
    }

    private Map<Long, String> getMethodsMap(final DSLContext dslContext, final LongSet nodes) {
        return dslContext
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(nodes))
                .fetch()
                .stream().collect(Collectors.toMap(Record2::value1, Record2::value2));
    }
}
