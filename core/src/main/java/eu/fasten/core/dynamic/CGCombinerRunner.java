package eu.fasten.core.dynamic;

import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.dynamic.data.DynamicJavaCG;
import eu.fasten.core.dynamic.data.HybridDirectedGraph;
import eu.fasten.core.merge.CGMerger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "CGCombinerRunner")
public class CGCombinerRunner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CGCombinerRunner.class);

    @CommandLine.Option(names = {"-df", "--dynamic-cg-file"},
            paramLabel = "JSON_FILE",
            required = true,
            description = "Path to JSON file which dynamic CG")
    String dynamicCgPath;

    @CommandLine.Option(names = {"-sf", "--static-cg-files"},
            paramLabel = "JSON_FILE1,JSON_FILE2,...",
            required = true,
            description = "List of paths to static ERCG JSON files of the dependency set",
            split = ",")
    List<String> staticCgsPaths;

    @CommandLine.Option(names = {"-o", "--output-path"},
            paramLabel = "JSON_FILE",
            description = "Path to output JSON file which will contained combined CG")
    String outputPath;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new CGCombinerRunner()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        logger.info("Reading dynamic CG");
        DynamicJavaCG dynamicCg;
        try {
            dynamicCg = new DynamicJavaCG(new JSONObject(new JSONTokener(new FileReader(dynamicCgPath))));
        } catch (FileNotFoundException e) {
            logger.error("Could not read dynamic CG file", e);
            return;
        }
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
        var stitchedStaticCg = merger.mergeAllDeps();
        var staticUrisMap = merger.getAllUris();

        logger.info("Combining dynamic CG and stitched static CG");
        var combiner = new StaticDynamicCGCombiner(stitchedStaticCg, staticUrisMap, dynamicCg);
        var combinedCg = combiner.combineCGs();
        var uriMap = combiner.getAllUrisMap();

        logger.info("Writing combined CG to JSON format");
        var json = new JSONObject();
        var methods = new JSONObject(combinedCg.numNodes());
        for (var node : combinedCg.nodes()) {
            methods.put(String.valueOf(node), uriMap.get(node.longValue()));
        }
        json.put("methods", methods);
        var edges = new JSONArray();
        for (var edge : combinedCg.edgeSet()) {
            var jsonEdge = new JSONObject();
            jsonEdge.put("call", List.of(edge.firstLong(), edge.secondLong()));
            var origin = combinedCg.getCallOrigin(edge);
            jsonEdge.put("static", origin.equals(HybridDirectedGraph.CallOrigin.staticCg) || origin.equals(HybridDirectedGraph.CallOrigin.staticAndDynamicCgs));
            jsonEdge.put("dynamic", origin.equals(HybridDirectedGraph.CallOrigin.dynamicCg) || origin.equals(HybridDirectedGraph.CallOrigin.staticAndDynamicCgs));
            edges.put(jsonEdge);
        }
        json.put("calls", edges);
        logger.info("Done");
        if (outputPath != null) {
            try {
                Files.writeString(Path.of(outputPath), json.toString());
            } catch (IOException e) {
                logger.error("Error writing combined CG to {}", outputPath, e);
            }
        } else {
            System.out.println(json);
        }
    }
}
