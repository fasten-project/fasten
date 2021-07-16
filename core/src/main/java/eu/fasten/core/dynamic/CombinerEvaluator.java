package eu.fasten.core.dynamic;

import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.dynamic.data.DynamicJavaCG;
import eu.fasten.core.dynamic.data.HybridDirectedGraph;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@CommandLine.Command(name = "CombinerEvaluator")
public class CombinerEvaluator implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CombinerEvaluator.class);

    @CommandLine.Option(names = {"-c", "--coordinate"},
            paramLabel = "MVN_COORDINATE",
            required = true,
            description = "Maven coordinate of the project under analysis")
    String coordinate;

    @CommandLine.Option(names = {"-sf", "--static-cg-files"},
            paramLabel = "JSON_FILE1,JSON_FILE2,...",
            required = true,
            description = "List of paths to static ERCG JSON files of the dependency set",
            split = ",")
    List<String> staticCgsPaths;

    @CommandLine.Option(names = {"-rp", "--repo-path"},
            paramLabel = "REPO_PATH",
            required = true,
            description = "Path to the repository of the project under analysis")
    String repoPath;

    @CommandLine.Option(names = {"-dgp", "--dyn-generator-path"},
            paramLabel = "GENERATOR_PATH",
            required = true,
            description = "Path to the JAR file of the dynamic CG generator")
    String dynCgGeneratorPath;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new CombinerEvaluator()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        logger.info("Reading static CGs");
        var staticCGs = new ArrayList<ExtendedRevisionJavaCallGraph>(this.staticCgsPaths.size());
        for (var path : this.staticCgsPaths) {
            try {
                staticCGs.add(new ExtendedRevisionJavaCallGraph(new JSONObject(new JSONTokener(new FileReader(path)))));
            } catch (FileNotFoundException e) {
                logger.error("Could not read static CG file: {}", path, e);
                return;
            }
        }
        logger.info("Stitching static CGs together");
        var merger = new CGMerger(staticCGs);
        var stitchedStaticCg = (MergedDirectedGraph) merger.mergeAllDeps();
        var staticUrisMap = merger.getAllUris();
        logger.info("Static CG has {} nodes and {} edges", stitchedStaticCg.numNodes(), stitchedStaticCg.numArcs());

        logger.info("Generating dynamic CG");
        var dynamicCgPath = Path.of(repoPath, "javacg-dyn.json");
        var cmd = new String[] {
                "bash",
                "-c",
                "mvn clean test -DargLine=-javaagent:" + dynCgGeneratorPath + "='output=" + dynamicCgPath.toAbsolutePath() + "'"
        };
        try {
            var process = new ProcessBuilder(cmd).directory(new File(repoPath)).start();
            var testSucceeded = process.waitFor(3, TimeUnit.MINUTES);
            if (!testSucceeded) {
                logger.error("'mvn test' failed");
                return;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("Reading dynamic CG");
        DynamicJavaCG dynamicCg;
        try {
            dynamicCg = new DynamicJavaCG(new JSONObject(new JSONTokener(new FileReader(dynamicCgPath.toFile()))));
        } catch (FileNotFoundException e) {
            logger.error("Could not read dynamic CG file", e);
            return;
        }
        MavenUtilities.forceDeleteFile(dynamicCgPath.toFile());
        logger.info("Dynamic CG has {} nodes and {} edges", dynamicCg.getMethods().size(), dynamicCg.getCalls().size());

        logger.info("Combining dynamic CG and stitched static CG");
        var combiner = new StaticDynamicCGCombiner(stitchedStaticCg, staticUrisMap, dynamicCg);
        var combinedCg = combiner.combineCGs();
        logger.info("Successfully combined the CGs");
        System.out.println("Combined CG has " + combinedCg.numNodes() + " nodes and " + combinedCg.numArcs() + " calls");
        var staticCalls = combinedCg.edgeSet().stream()
                .filter(c -> combinedCg.getCallOrigin(c).equals(HybridDirectedGraph.CallOrigin.staticCg)).collect(Collectors.toSet());
        System.out.println("Number of calls from static CG: " + staticCalls.size());
        var dynamicCalls = combinedCg.edgeSet().stream()
                .filter(c -> combinedCg.getCallOrigin(c).equals(HybridDirectedGraph.CallOrigin.dynamicCg)).collect(Collectors.toSet());
        System.out.println("Number of calls from dynamic CG: " + dynamicCalls.size());
        var staticAndDynamicCalls = combinedCg.edgeSet().stream()
                .filter(c -> combinedCg.getCallOrigin(c).equals(HybridDirectedGraph.CallOrigin.staticAndDynamicCgs)).collect(Collectors.toSet());
        System.out.println("Number of calls from both CGs: " + staticAndDynamicCalls.size());

        logger.info("Filtering out external calls");
        var uriMap = combiner.getAllUrisMap();
        var uriStart = "fasten://mvn!" + this.coordinate.split(":")[0] + ":" + this.coordinate.split(":")[1] + "$" + this.coordinate.split(":")[2];
        var filteredCalls = combinedCg.edgeSet().stream()
                .filter(c -> uriMap.get(c.firstLong()).startsWith(uriStart) && uriMap.get(c.secondLong()).startsWith(uriStart))
                .collect(Collectors.toSet());
        System.out.println("Total number of internal calls: " + filteredCalls.size());
        var filteredStaticCalls = staticCalls.stream()
                .filter(c -> uriMap.get(c.firstLong()).startsWith(uriStart) && uriMap.get(c.secondLong()).startsWith(uriStart))
                .collect(Collectors.toSet());
        System.out.println("Number of internal calls from static CG: " + filteredStaticCalls.size());
        var filteredDynamicCalls = dynamicCalls.stream()
                .filter(c -> uriMap.get(c.firstLong()).startsWith(uriStart) && uriMap.get(c.secondLong()).startsWith(uriStart))
                .collect(Collectors.toSet());
        System.out.println("Number of internal calls from dynamic CG: " + filteredDynamicCalls.size());
        var filteredStaticAndDynamicCalls = staticAndDynamicCalls.stream()
                .filter(c -> uriMap.get(c.firstLong()).startsWith(uriStart) && uriMap.get(c.secondLong()).startsWith(uriStart))
                .collect(Collectors.toSet());
        System.out.println("Number of internal calls from both CGs: " + filteredStaticAndDynamicCalls.size());
    }
}
