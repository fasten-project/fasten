package eu.fasten.core.maven.utils;

import eu.fasten.core.maven.data.graph.DependencyEdge;
import eu.fasten.core.maven.data.graph.DependencyNode;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

public class DependencyGraphUtilities {

    public static Graph<DependencyNode, DependencyEdge> invertDependencyGraph(Graph<DependencyNode, DependencyEdge> dependencyGraph) {
        var graph = new DefaultDirectedGraph<DependencyNode, DependencyEdge>(DependencyEdge.class);
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

    public static Graph<DependencyNode, DependencyEdge> cloneDependencyGraph(
            Graph<DependencyNode, DependencyEdge> dependencyGraph) {
        var graph = new DefaultDirectedGraph<DependencyNode, DependencyEdge>(DependencyEdge.class);
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
