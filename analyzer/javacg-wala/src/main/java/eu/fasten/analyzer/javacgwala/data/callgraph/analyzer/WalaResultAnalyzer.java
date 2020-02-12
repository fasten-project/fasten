package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import eu.fasten.analyzer.javacgwala.data.ArtifactResolver;
import eu.fasten.analyzer.javacgwala.data.MavenResolvedCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import eu.fasten.analyzer.javacgwala.data.core.ResolvedMethod;
import eu.fasten.analyzer.javacgwala.data.core.UnresolvedMethod;

import java.util.HashMap;
import java.util.List;

public class WalaResultAnalyzer {

    private final CallGraph rawCallGraph;

    private final IClassHierarchy cha;

    private final PartialCallGraph partialCallGraph;

    private WalaResultAnalyzer(CallGraph rawCallGraph, List<MavenResolvedCoordinate> coordinates) {
        this.rawCallGraph = rawCallGraph;
        this.cha = rawCallGraph.getClassHierarchy();
        this.partialCallGraph = new PartialCallGraph(coordinates);
    }

    public static PartialCallGraph wrap(CallGraph rawCallGraph,
                                        List<MavenResolvedCoordinate> coordinates) {
        WalaResultAnalyzer walaResultAnalyzer = new WalaResultAnalyzer(rawCallGraph, coordinates);

        CallGraphAnalyzer callGraphAnalyzer = new CallGraphAnalyzer(walaResultAnalyzer.rawCallGraph,
                walaResultAnalyzer.cha, walaResultAnalyzer.partialCallGraph);
        callGraphAnalyzer.resolveCalls();

        ClassHierarchyAnalyzer classHierarchyAnalyzer = new ClassHierarchyAnalyzer();
        classHierarchyAnalyzer.resolveClassHierarchy();

        return walaResultAnalyzer.partialCallGraph;
    }
}
