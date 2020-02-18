package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;

public class WalaResultAnalyzer {

    private final CallGraph rawCallGraph;

    private final IClassHierarchy cha;

    private final PartialCallGraph partialCallGraph;

    /**
     * Analyze result produced by Wal plugin.
     *
     * @param rawCallGraph Raw call graph in Wala format
     * @param coordinate   List of {@link MavenCoordinate}
     */
    private WalaResultAnalyzer(CallGraph rawCallGraph, MavenCoordinate coordinate) {
        this.rawCallGraph = rawCallGraph;
        this.cha = rawCallGraph.getClassHierarchy();
        this.partialCallGraph = new PartialCallGraph(coordinate);
    }

    /**
     * Convert raw Wala call graph to {@link PartialCallGraph}.
     *
     * @param rawCallGraph Raw call graph in Wala format
     * @param coordinate   List of {@link MavenCoordinate}
     * @return Partial call graph
     */
    public static PartialCallGraph wrap(CallGraph rawCallGraph,
                                        MavenCoordinate coordinate) {
        if (rawCallGraph == null) {
            return new PartialCallGraph(coordinate);
        }

        WalaResultAnalyzer walaResultAnalyzer = new WalaResultAnalyzer(rawCallGraph, coordinate);

        CallGraphAnalyzer callGraphAnalyzer = new CallGraphAnalyzer(walaResultAnalyzer.rawCallGraph,
                walaResultAnalyzer.cha, walaResultAnalyzer.partialCallGraph);
        callGraphAnalyzer.resolveCalls();

        ClassHierarchyAnalyzer classHierarchyAnalyzer =
                new ClassHierarchyAnalyzer(walaResultAnalyzer.cha,
                        walaResultAnalyzer.partialCallGraph);
        classHierarchyAnalyzer.resolveCHA();

        return walaResultAnalyzer.partialCallGraph;
    }
}
