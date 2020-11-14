package eu.fasten.core.maven.utils;

import eu.fasten.core.maven.data.DependencyEdge;
import eu.fasten.core.maven.data.Revision;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

public class DependencyGraphUtilities {

    public static Graph<Revision, DependencyEdge> invertDependencyGraph(Graph<Revision, DependencyEdge> dependencyGraph) {
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        for (var node : dependencyGraph.vertexSet()) {
            graph.addVertex(node);
        }
        for (var edge : dependencyGraph.edgeSet()) {
            var source = dependencyGraph.getEdgeSource(edge);
            var target = dependencyGraph.getEdgeTarget(edge);
            graph.addEdge(target, source, edge);    // Reverse edges
        }
        return graph;
    }

    public static Graph<Revision, DependencyEdge> cloneDependencyGraph(
            Graph<Revision, DependencyEdge> dependencyGraph) {
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        for (var node : dependencyGraph.vertexSet()) {
            graph.addVertex(node);
        }
        for (var edge : dependencyGraph.edgeSet()) {
            var source = dependencyGraph.getEdgeSource(edge);
            var target = dependencyGraph.getEdgeTarget(edge);
            graph.addEdge(source, target, edge);
        }
        return graph;
    }
}
