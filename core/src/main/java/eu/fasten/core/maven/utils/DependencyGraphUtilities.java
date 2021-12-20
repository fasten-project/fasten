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
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import eu.fasten.core.maven.DependencyGraphBuilder;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyEdge;
import eu.fasten.core.maven.data.Revision;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Utility functions to construct and (de-)serialize Maven dependency graphs
 */
public final class DependencyGraphUtilities {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphUtilities.class);

    public static Graph<Revision, DependencyEdge> invertDependencyGraph(Graph<Revision,
            DependencyEdge> dependencyGraph) {
        logger.debug("Calculating graph transpose");
        var startTs = System.currentTimeMillis();
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        for (var node : dependencyGraph.vertexSet()) {
            graph.addVertex(node);
        }
        for (var edge : dependencyGraph.edgeSet()) {
            var reversedEdges = new DependencyEdge(edge.target, edge.source, edge.scope, edge.optional, edge.exclusions, edge.type);
            graph.addEdge(reversedEdges.source, reversedEdges.target, reversedEdges);    // Reverse edges
        }
        logger.info("Graph transposed: {} ms", System.currentTimeMillis() - startTs);
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

    private static class DefaultArtifactVersionSerializer extends Serializer<DefaultArtifactVersion> {

        @Override
        public void write(Kryo kryo, Output output, DefaultArtifactVersion object) {
            output.writeString(object.toString());
        }

        @Override
        public DefaultArtifactVersion read(Kryo kryo, Input input, Class<? extends DefaultArtifactVersion> type) {
            var coord = input.readString();
            try {
                return type.getConstructor(String.class).newInstance(coord);
            } catch (Exception e) {
                logger.warn("Cannot deserialize DefaultArtifactVersion {}", coord);
                return null;
            }
        }
    }

    private static Kryo setupKryo() throws Exception {
        var kryo = new Kryo();

        kryo.register(Set.class);
        kryo.register(HashSet.class);
        kryo.register(Revision.class);
        kryo.register(DependencyEdge.class);
        kryo.register(Dependency.Exclusion.class);
        kryo.register(Class.forName("eu.fasten.core.maven.data.Dependency$Exclusion"));
        kryo.register(java.sql.Timestamp.class);
        kryo.register(java.util.ArrayList.class);
        kryo.register(Class.forName("java.util.Collections$UnmodifiableSet"));
        kryo.register(org.apache.maven.artifact.versioning.DefaultArtifactVersion.class,
                new DefaultArtifactVersionSerializer());
        kryo.register(org.apache.maven.artifact.versioning.ComparableVersion.class);
        kryo.register(Class.forName("org.apache.maven.artifact.versioning.ComparableVersion$ListItem"));
        kryo.register(Class.forName("org.apache.maven.artifact.versioning.ComparableVersion$IntItem"));
        kryo.register(Class.forName("org.apache.maven.artifact.versioning.ComparableVersion$StringItem"));
        kryo.register(Class.forName("org.apache.maven.artifact.versioning.ComparableVersion$LongItem"));

        return kryo;
    }

    /**
     * Serialize a Maven dependency graph to a file. Independently serializes nodes and edges.
     *
     * @throws Exception When the files that hold the serialized data cannot be created.
     */
    public static void serializeDependencyGraph(Graph<Revision, DependencyEdge> graph, String path) throws Exception {
        var kryo = setupKryo();

        var nodes = new Output(new FileOutputStream(path + ".nodes"));
        var edges = new Output(new FileOutputStream(path + ".edges"));

        kryo.writeObject(nodes, graph.vertexSet());
        nodes.close();

        kryo.writeObject(edges, graph.edgeSet());
        edges.close();
    }

    /**
     * Deserialize a Maven dependency graph from the indicated file.
     *
     * @throws Exception When the files that hold the serialized graph cannot be opened.
     */
    public static Graph<Revision, DependencyEdge> deserializeDependencyGraph(String path) throws Exception {
        var startTs = System.currentTimeMillis();
        var kryo = setupKryo();

        var nodesInput = new Input(new FileInputStream(path + ".nodes"));
        var edgesInput = new Input(new FileInputStream(path + ".edges"));

        Set<Revision> nodes = kryo.readObject(nodesInput, HashSet.class);
        Set<DependencyEdge> edges = kryo.readObject(edgesInput, HashSet.class);

        logger.debug("Loaded {} nodes and {} edges", nodes.size(), edges.size());

        var dependencyGraph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);

        nodes.forEach(dependencyGraph::addVertex);
        edges.forEach(e -> dependencyGraph.addEdge(e.source, e.target, e));

        logger.info("Deserialized graph at {}: {} ms", path, System.currentTimeMillis() - startTs);
        return dependencyGraph;
    }

    /**
     * Load a dependency graph from a path. Both the nodes and edges files need to be present.
     *
     * @throws Exception When deserialization fails.
     */
    public static Optional<Graph<Revision, DependencyEdge>> loadDependencyGraph(String path) throws Exception {
        if ((new File(path + ".nodes")).exists() &&
                (new File(path + ".edges")).exists()) {
            logger.info("Found serialized dependency graph at {}. Deserializing.", path);
            return Optional.of(DependencyGraphUtilities.deserializeDependencyGraph(path));
        } else {
            logger.warn("Graph at {} is incomplete", path);
            return Optional.empty();
        }
    }

    /**
     * Builds a new Maven dependency graph by connecting to the database and then serializes it to the provided path.
     *
     * @throws Exception When serialization fails.
     */
    public static Graph<Revision, DependencyEdge> buildDependencyGraphFromScratch(DSLContext dbContext, String path)
            throws Exception {
        var tsStart = System.currentTimeMillis();
        var graphBuilder = new DependencyGraphBuilder();
        var graph = graphBuilder.buildDependencyGraph(dbContext);
        var tsEnd = System.currentTimeMillis();
        logger.info("Graph has {} nodes and {} edges ({} ms)", graph.vertexSet().size(),
                graph.edgeSet().size(), tsEnd - tsStart);

        tsStart = System.currentTimeMillis();
        logger.info("Serializing graph to {}", path);
        DependencyGraphUtilities.serializeDependencyGraph(graph, path == null ? "mavengraph.bin" : path);
        logger.info("Finished serializing graph ({} ms)", System.currentTimeMillis() - tsStart);

        return graph;
    }
}
