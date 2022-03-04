/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.maven.graph;

import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.slf4j.Logger;

import eu.fasten.core.maven.data.Revision;

public class MavenGraph implements Cloneable {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MavenGraph.class);

    private Graph<Revision, MavenEdge> dependencies = new DefaultDirectedGraph<>(MavenEdge.class);
    private Graph<Revision, MavenEdge> dependents = new DefaultDirectedGraph<>(MavenEdge.class);

    public void addNode(Revision r) {
        dependencies.addVertex(r);
        dependents.addVertex(r);
    }

    public void removeNode(Revision r) {
        dependencies.removeVertex(r);
        dependents.removeVertex(r);
    }

    public void addDependencyEdge(MavenEdge e) {
        if (!dependencies.containsVertex(e.source) || !dependencies.containsVertex(e.target)) {
            LOG.warn("Trying to add edge with non-existing source or target: {}", e);
            return;
        }
        dependencies.addEdge(e.source, e.target, e);
        dependents.addEdge(e.target, e.source, e);
    }

    public void removeDependencyEdge(MavenEdge e) {
        if (!dependencies.containsVertex(e.source) || !dependencies.containsVertex(e.target)) {
            LOG.warn("Trying to remove edge with non-existing source or target: {}", e);
            return;
        }
        dependencies.removeEdge(e);
        dependents.removeEdge(e.reverse());
    }

    @Override
    protected MavenGraph clone() {
        var graph = new MavenGraph();
        for (var node : dependencies.vertexSet()) {
            graph.addNode(node);
        }
        for (var edge : dependencies.edgeSet()) {
            graph.addDependencyEdge(edge);
        }
        return graph;
    }

    public int numVertices() {
        return dependencies.vertexSet().size();
    }

    public int numEdges() {
        return dependencies.edgeSet().size();
    }

    public Set<Revision> vertexSet() {
        return dependencies.vertexSet();
    }

    public Set<MavenEdge> edgeSet() {
        return dependencies.edgeSet();
    }

    public boolean containsVertex(Revision r) {
        return dependencies.containsVertex(r);
    }

    public Set<MavenEdge> dependencyEdges(Revision r) {
        return dependencies.outgoingEdgesOf(r);
    }

    public List<Revision> directDependencies(Revision r) {
        return Graphs.successorListOf(dependencies, r);
    }

    public List<Revision> directDependents(Revision r) {
        return Graphs.successorListOf(dependents, r);
    }

}