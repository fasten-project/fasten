package eu.fasten.core.dynamic;

import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.dynamic.data.DynamicJavaCG;
import eu.fasten.core.dynamic.data.HybridDirectedGraph;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;
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

@CommandLine.Command(name = "CombinerEvaluator")
public class CombinerEvaluator implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CombinerEvaluator.class);

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
        System.out.println("Number of calls from static CG: " + combinedCg.getCallOriginMap().values().stream()
                .filter(origin -> origin.equals(HybridDirectedGraph.CallOrigin.staticCg)).count());
        System.out.println("Number of calls from dynamic CG: " + combinedCg.getCallOriginMap().values().stream()
                .filter(origin -> origin.equals(HybridDirectedGraph.CallOrigin.dynamicCg)).count());
        System.out.println("Number of calls from both CGs: " + combinedCg.getCallOriginMap().values().stream()
                .filter(origin -> origin.equals(HybridDirectedGraph.CallOrigin.staticAndDynamicCgs)).count());
    }
}
