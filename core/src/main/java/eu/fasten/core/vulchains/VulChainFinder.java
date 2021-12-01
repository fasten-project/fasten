package eu.fasten.core.vulchains;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.callableindex.utils.CallableIndexChecker;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CGMerger;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "VulChainFinder")
public class VulChainFinder implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(VulChainFinder.class);

    @CommandLine.Option(names = {"-c", "--callable-index-path"},
        paramLabel = "INDEX_PATH",
        required = true,
        description = "Path to the callable index")
    String callableIndexPath;

    @CommandLine.Option(names = {"-coord", "--maven-coordinate"},
        paramLabel = "MAVEN_COORDINATE",
        required = true,
        description = "Maven coordinate of the package to find vul path for")
    String coord;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new VulChainFinder()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
//
//    var rocksDb = CallableIndexChecker.connectToReadOnlyRocksDB(this.callableIndexPath);
//
//    int counter = 0;
//    for(final var vulDependents :dependentsMap.entrySet()) {
//
//        final var vulCallables = queryCallableVuls(metadataDb, vulDependents.getKey());
//        if (vulCallables.isEmpty()) {
//            counter++;
//            continue;
//        }
//
//        for (final var dependent : vulDependents.getValue()) {
//
//            final var ids =
//                resolveDepIds(metadataDb, graphResolver, vulDependents, dependent);
//            final var merger = new CGMerger(ids, metadataDb, rocksDb);
//            final var vulPaths = getVulPaths(rocksDb, merger, vulCallables, dependent);
//            if (vulPaths == null || vulPaths.isEmpty()) {
//                continue;
//            }
//            List<String[]> content = new ArrayList<>();
//            final var uris = merger.getAllUris();
//
//            content.add(new String[] {"source", "target", "paths"});
//
//            writeVulPathToFile(dependent, vulPaths, merger);
//        }
//    }
//            logger.info("No vulnerable callable for {} packages",counter);
    }


    private Map<Pair<Long, Long>, List<GraphPath<Long, LongLongPair>>> getVulPaths(RocksDao rocksDb,
                                                                                   CGMerger merger,
                                                                                   List<Long> vulCallables,
                                                                                   Revision dependent) {

        final var mergedGraph = merger.mergeAllDeps();
        final var inspector = new ConnectivityInspector<>(mergedGraph);
        Map<Pair<Long, Long>, List<GraphPath<Long, LongLongPair>>> vulPaths = new HashMap<>();
        DirectedGraph dependentCG = null;
        try {
            dependentCG = rocksDb.getGraphData(dependent.id);
        } catch (RocksDBException e) {
            logger.warn("Failed to fetch the data of" + dependent.id + "package", e);
        }
        if (dependentCG == null) {
            return null;
        }
        for (Long vulNode : vulCallables) {
            for (Long dependentNode : dependentCG.nodes()) {
                if (!hasExternalEdge(dependentCG, dependentNode)){
                    continue;
                }

                if (mergedGraph.containsVertex(dependentNode) &&
                    mergedGraph.containsVertex(vulNode)) {
                    logger.info("########### Source and target are in the merged graph ##########");
                    if (inspector.pathExists(dependentNode, vulNode)) {
                        logger.info("########### There is a path between source and target " +
                            "#########");
                        final var pathFinder = new AllDirectedPaths<>(mergedGraph);
                        vulPaths.put(Pair.of(dependentNode, vulNode),
                            pathFinder.getAllPaths(dependentNode,
                                vulNode, true, null));
                    }
                }
            }

        }
        return vulPaths;
    }

    private boolean hasExternalEdge(DirectedGraph dependentCG, Long dependentNode) {
        for (final var edge : dependentCG.edgesOf(dependentNode)) {
            if (dependentCG.isExternal(edge.leftLong()) || dependentCG.isExternal(edge.rightLong())) {
                return true;
            }
        }
        return false;
    }

}
