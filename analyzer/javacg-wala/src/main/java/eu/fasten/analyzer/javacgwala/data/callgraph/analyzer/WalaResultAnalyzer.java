package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import eu.fasten.analyzer.javacgwala.data.MavenResolvedCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import java.util.List;

public class WalaResultAnalyzer {

    private final CallGraph rawCallGraph;

    private final IClassHierarchy cha;

    private final PartialCallGraph partialCallGraph;

    /**
     * Analyze result produced by Wal plugin.
     *
     * @param rawCallGraph Raw call graph in Wala format
     * @param coordinates  List of {@link MavenResolvedCoordinate}
     */
    private WalaResultAnalyzer(CallGraph rawCallGraph, List<MavenResolvedCoordinate> coordinates) {
        this.rawCallGraph = rawCallGraph;
        this.cha = rawCallGraph.getClassHierarchy();
        this.partialCallGraph = new PartialCallGraph(coordinates);
    }

    /**
     * Convert raw Wala call graph to {@link PartialCallGraph}.
     *
     * @param rawCallGraph Raw call graph in Wala format
     * @param coordinates  List of {@link MavenResolvedCoordinate}
     * @return Partial call graph
     */
    public static PartialCallGraph wrap(CallGraph rawCallGraph,
                                        List<MavenResolvedCoordinate> coordinates) {
        if (rawCallGraph == null) {
            return new PartialCallGraph(coordinates);
        }

        WalaResultAnalyzer walaResultAnalyzer = new WalaResultAnalyzer(rawCallGraph, coordinates);

        CallGraphAnalyzer callGraphAnalyzer = new CallGraphAnalyzer(walaResultAnalyzer.rawCallGraph,
                walaResultAnalyzer.cha, walaResultAnalyzer.partialCallGraph);
        callGraphAnalyzer.resolveCalls();

        return walaResultAnalyzer.partialCallGraph;
    }
}
