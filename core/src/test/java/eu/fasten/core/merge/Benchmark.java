package eu.fasten.core.merge;

import eu.fasten.core.data.ExtendedRevisionCallGraph;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
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
        ExtendedRevisionCallGraph callgraph;
        try {
            var tokener = new JSONTokener(new FileReader(path));
            callgraph = new ExtendedRevisionCallGraph(new JSONObject(tokener));
        } catch (FileNotFoundException e) {
            logger.error("Couldn't read merged call graph file", e);
            return;
        }
        var methodMap = callgraph.mapOfAllMethods();
        for (var arc : callgraph.getGraph().getResolvedCalls().keySet()) {
            System.out.println(arc.get(0) + " -> " + arc.get(1));
            System.out.println(methodMap.get(arc.get(0)).getUri() + " -> " + methodMap.get(arc.get(1)).getUri());
        }
    }
}
