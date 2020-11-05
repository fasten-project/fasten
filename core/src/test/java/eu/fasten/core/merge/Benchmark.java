package eu.fasten.core.merge;

import ch.qos.logback.classic.Level;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    public static void main(String[] args) {
        System.exit(new CommandLine(new Benchmark()).execute(args));
    }

    @Override
    public void run() {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);
        logger.info("Loading and generating call graphs...");
        var callgraph = getLocalCallGraph();
        var directedGraph = getDatabaseCallGraph();

        System.out.println("==========================");
        System.out.println("+        BENCHMARK       +");
        System.out.println("==========================");


        logger.info("Local merged graph: {} edges", callgraph.getGraph().getResolvedCalls().size());
        logger.info("Database merged graph: {} edges", directedGraph.numArcs());

        var localResolvedGraph = ERCGToMap(callgraph);
        var databaseResolvedGraph = directedGraphToMap(directedGraph);

        var missedEdges = getMissedEdges(localResolvedGraph, databaseResolvedGraph);
        missedEdges.putAll(getMissedEdges(databaseResolvedGraph, localResolvedGraph));

        var count = 0;
        for (var edge : missedEdges.entrySet()) {
            count += edge.getValue().size();
            for (var target : edge.getValue()) {
                System.out.println(FastenURI.create(target));
            }
        }

        logger.info("Difference between graphs is {} edges", count);
        System.out.println("==========================");
    }

    private Map<String, Set<String>> getMissedEdges(final Map<String, Set<String>> referenceGraph,
                                                    final Map<String, Set<String>> callgraph) {
        var missedEdges = new HashMap<String, Set<String>>();
        for (var arc : referenceGraph.entrySet()) {
            if (!callgraph.containsKey(arc.getKey())) {
                for (var target : arc.getValue()) {
                    missedEdges.merge(arc.getKey(), new HashSet<>(Collections.singleton(target)), (old, neu) -> {
                        old.addAll(neu);
                        return old;
                    });
                }
            } else {
                for (var target : arc.getValue()) {
                    if (!callgraph.get(arc.getKey()).contains(target)) {
                        missedEdges.merge(arc.getKey(), new HashSet<>(Collections.singleton(target)), (old, neu) -> {
                            old.addAll(neu);
                            return old;
                        });
                    }
                }
            }
        }
        return missedEdges;
    }

    private ExtendedRevisionCallGraph getLocalCallGraph() {
        ExtendedRevisionCallGraph callgraph = null;
        try {
            var tokener = new JSONTokener(new FileReader(path));
            callgraph = new ExtendedRevisionCallGraph(new JSONObject(tokener));
        } catch (FileNotFoundException e) {
            logger.error("Couldn't read merged call graph file", e);
            System.exit(1);
        }
        return callgraph;
    }

    private Map<String, Set<String>> ERCGToMap(final ExtendedRevisionCallGraph callgraph) {
        var localMethodsMap = callgraph.mapOfAllMethods();
        var localResolvedGraph = new HashMap<String, Set<String>>();

        var set = new HashSet<>();
        for (var arc : callgraph.getGraph().getResolvedCalls().keySet()) {
            set.add(localMethodsMap.get(arc.get(0)).getUri().toString() + "->" + localMethodsMap.get(arc.get(1)).getUri().toString());
            localResolvedGraph.merge(localMethodsMap.get(arc.get(0)).getUri().toString(), new HashSet<>(Collections.singleton(localMethodsMap.get(arc.get(1)).getUri().toString())), (old, neu) -> {
                old.addAll(neu);
                return old;
            });
        }
        logger.info("Local merged graph: {} unique edges", set.size());
        return localResolvedGraph;
    }

    private Map<String, Set<String>> directedGraphToMap(final DirectedGraph mergedDirectedGraph) {
        DSLContext dbContext = null;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Could not connect to the metadata database: " + e.getMessage());
            System.exit(1);
        }
        var databaseMethodsMap = getMethodsMap(dbContext, mergedDirectedGraph.nodes());
        var databaseResolvedGraph = new HashMap<String, Set<String>>();

        var set = new HashSet<>();
        for (var source : mergedDirectedGraph.nodes()) {
            for (var target : mergedDirectedGraph.successors(source)) {
                set.add(databaseMethodsMap.get(source) + "->" + databaseMethodsMap.get(target));
                databaseResolvedGraph.merge(databaseMethodsMap.get(source), new HashSet<>(Collections.singleton(databaseMethodsMap.get(target))), (old, neu) -> {
                    old.addAll(neu);
                    return old;
                });
            }
        }
        logger.info("Database merged graph: {} unique edges", set.size());
        return databaseResolvedGraph;
    }

    private DirectedGraph getDatabaseCallGraph() {
        DSLContext dbContext = null;
        RocksDao rocksDao = null;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
            rocksDao = RocksDBConnector.createReadOnlyRocksDBAccessObject(graphDbDir);
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Could not connect to the metadata database: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            logger.error("Could not connect to the graph database: " + e.getMessage());
            System.exit(1);
        }

        var databaseMerger = new DatabaseMerger(artifact, dependencies, dbContext, rocksDao);

        return databaseMerger.mergeWithCHA();
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
