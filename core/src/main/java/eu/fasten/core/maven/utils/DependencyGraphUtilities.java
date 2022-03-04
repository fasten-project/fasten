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

import static eu.fasten.core.utils.Asserts.assertNotNullOrEmpty;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.InvalidParameterException;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import eu.fasten.core.maven.DependencyGraphBuilder;
import eu.fasten.core.maven.data.Exclusion;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.graph.MavenEdge;
import eu.fasten.core.maven.graph.MavenGraph;

/**
 * Utility functions to construct and (de-)serialize Maven dependency graphs
 */
public final class DependencyGraphUtilities {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphUtilities.class);

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
        kryo.register(MavenEdge.class);
        kryo.register(Exclusion.class);
        kryo.register(Class.forName("eu.fasten.core.maven.data.Exclusion"));
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
    public static void serializeDependencyGraph(MavenGraph graph, String path) throws Exception {
        var kryo = setupKryo();

        try(var nodes = new Output(new FileOutputStream(path + ".nodes"))) {
            kryo.writeObject(nodes, graph.vertexSet());
        }
        try(var edges = new Output(new FileOutputStream(path + ".edges"))) {
            kryo.writeObject(edges, graph.edgeSet());
        }
    }

    /**
     * Deserialize a Maven dependency graph from the indicated file.
     *
     * @throws Exception When the files that hold the serialized graph cannot be opened.
     */
    public static MavenGraph deserializeDependencyGraph(String path) throws Exception {
        var startTs = System.currentTimeMillis();
        var kryo = setupKryo();

        var nodesInput = new Input(new FileInputStream(path + ".nodes"));
        var edgesInput = new Input(new FileInputStream(path + ".edges"));

        Set<Revision> nodes = kryo.readObject(nodesInput, HashSet.class);//SetOfRevision.class);
        Set<MavenEdge> edges = kryo.readObject(edgesInput, HashSet.class);//SetOfMavenEdge.class);

        logger.debug("Loaded {} nodes and {} edges", nodes.size(), edges.size());

        var dependencyGraph = new MavenGraph();

        nodes.forEach(dependencyGraph::addNode);
        edges.forEach(e -> dependencyGraph.addDependencyEdge(e));

        logger.info("Deserialized graph at {}: {} ms", path, System.currentTimeMillis() - startTs);
        return dependencyGraph;
    }
    
    public static boolean doesDependencyGraphExist(String path) {
        return fileNodes(path).exists() && fileEdges(path).exists();
    }
    
    @SuppressWarnings("serial")
    private static class SetOfRevision extends HashSet<Revision> {}
    @SuppressWarnings("serial")
    private static class SetOfMavenEdge extends HashSet<MavenEdge> {}
    

    /**
     * Load a dependency graph from a path. Both the nodes and edges files need to be present.
     *
     * @throws Exception When deserialization fails.
     */
    public static MavenGraph loadDependencyGraph(String path) throws Exception {
        if (!doesDependencyGraphExist(path)) {
            throw new InvalidParameterException("graph does not exist or is incomplete: " + path);
        }
        logger.info("Found serialized dependency graph at {}. Deserializing.", path);
        return DependencyGraphUtilities.deserializeDependencyGraph(path);
    }

    private static File fileEdges(String path) {
        return new File(path + ".edges");
    }

    private static File fileNodes(String path) {
        return new File(path + ".nodes");
    }

    /**
     * Builds a new Maven dependency graph by connecting to the database and then serializes it to the provided path.
     *
     * @throws Exception When serialization fails.
     */
    public static MavenGraph buildDependencyGraphFromScratch(DSLContext dbContext, String path)
            throws Exception {
        assertNotNullOrEmpty(path);
        var tsStart = System.currentTimeMillis();
        var graphBuilder = new DependencyGraphBuilder();
        var graph = graphBuilder.buildDependencyGraph(dbContext);
        var tsEnd = System.currentTimeMillis();
        logger.info("Graph has {} nodes and {} edges ({} ms)", graph.numVertices(),
                graph.numEdges(), tsEnd - tsStart);

        tsStart = System.currentTimeMillis();
        logger.info("Serializing graph to {}", path);
        DependencyGraphUtilities.serializeDependencyGraph(graph, path);
        logger.info("Finished serializing graph ({} ms)", System.currentTimeMillis() - tsStart);

        return graph;
    }
}
