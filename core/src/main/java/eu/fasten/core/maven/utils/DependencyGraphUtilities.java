/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.maven.utils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import eu.fasten.core.maven.data.DependencyEdge;
import eu.fasten.core.maven.data.Revision;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Set;

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

    private static Kryo setupKryo() throws Exception {
        var kryo = new Kryo();

        kryo.register(Revision.class);
        kryo.register(DependencyEdge.class);
        kryo.register(Class.forName("eu.fasten.core.maven.data.Dependency$Exclusion"));
        kryo.register(java.sql.Timestamp.class);
        kryo.register(java.util.ArrayList.class);
        kryo.register(Class.forName("java.util.Collections$UnmodifiableSet"));
        kryo.register(org.apache.maven.artifact.versioning.DefaultArtifactVersion.class);
        kryo.register(org.apache.maven.artifact.versioning.ComparableVersion.class);
        kryo.register(Class.forName("org.apache.maven.artifact.versioning.ComparableVersion$ListItem"));
        kryo.register(Class.forName("org.apache.maven.artifact.versioning.ComparableVersion$IntItem"));
        kryo.register(Class.forName("org.apache.maven.artifact.versioning.ComparableVersion$StringItem"));
        kryo.register(Class.forName("org.apache.maven.artifact.versioning.ComparableVersion$LongItem"));

        return kryo;
    }

    public static void serializeDependencyGraph(Graph<Revision, DependencyEdge> graph, String path) throws Exception {
        var kryo = setupKryo();

        var nodes = new Output(new FileOutputStream(path + ".nodes"));
        var edges = new Output(new FileOutputStream(path + ".edges"));

        kryo.writeObject(nodes, graph.vertexSet());
        nodes.close();

        kryo.writeObject(edges, graph.edgeSet());
        edges.close();
    }

    public static Graph<Revision, DependencyEdge> loadDependencyGraph(String path) throws Exception {
        var kryo = setupKryo();

        var nodesInput = new Input(new FileInputStream(path + ".nodes"));
        var edgesInput = new Input(new FileInputStream(path + ".edges"));

        Set<Revision> nodes = kryo.readObject(nodesInput, Set.class);
        Set<DependencyEdge> edges = kryo.readObject(edgesInput, Set.class);

        var dependencyGraph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);

        nodes.forEach(dependencyGraph::addVertex);
        // TODO: Fix this when DependencyEdge contains source and target nodes
        //edges.forEach(e -> dependencyGraph.addEdge(e.source, e.target));

        return dependencyGraph;
    }
}
