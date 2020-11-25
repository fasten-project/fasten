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

package eu.fasten.core.maven;

import eu.fasten.core.maven.data.*;
import org.apache.commons.math3.util.Pair;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

public class GraphMavenResolverTest {

    private GraphMavenResolver graphMavenResolver;

    @BeforeEach
    public void setup() {
        graphMavenResolver = new GraphMavenResolver();
    }

    @Test
    public void buildDependencyTreeFromGraphTest() {
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        var nodeA = new Revision("a", "a", "1", new Timestamp(1));
        var nodeB = new Revision("b", "b", "2", new Timestamp(2));
        var nodeC = new Revision("c", "c", "3", new Timestamp(3));
        var nodeD = new Revision("d", "d", "4", new Timestamp(4));
        var edgeAB = new DependencyEdge(nodeA, nodeB, "", false, emptyList());
        var edgeAC = new DependencyEdge(nodeA, nodeC, "", false, emptyList());
        var edgeBD = new DependencyEdge(nodeB, nodeD, "", false, emptyList());
        assertTrue(graph.addVertex(nodeA));
        assertTrue(graph.addVertex(nodeB));
        assertTrue(graph.addVertex(nodeC));
        assertTrue(graph.addVertex(nodeD));
        assertTrue(graph.addEdge(nodeA, nodeB, edgeAB));
        assertTrue(graph.addEdge(nodeA, nodeC, edgeAC));
        assertTrue(graph.addEdge(nodeB, nodeD, edgeBD));
        var expected = new DependencyTree(new Dependency("a", "a", "1"), List.of(
                new DependencyTree(new Dependency("b", "b", "2"), List.of(
                        new DependencyTree(new Dependency("d", "d", "4"), emptyList())
                )),
                new DependencyTree(new Dependency("c", "c", "3"), emptyList())
        ));
        var actual = graphMavenResolver.buildDependencyTreeFromGraph(graph, nodeA);
        assertEquals(expected, actual);
    }

    @Test
    public void filterDependencyGraphByTimestampTest() {
        var expected = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        var nodeA = new Revision("a", "a", "1", new Timestamp(-1));
        var nodeB = new Revision("b", "b", "2", new Timestamp(1));
        var edgeAB = new DependencyEdge(nodeA, nodeB, "", false, emptyList());
        expected.addVertex(nodeA);
        expected.addVertex(nodeB);
        expected.addEdge(nodeA, nodeB, edgeAB);
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        var nodeB2 = new Revision("b", "b", "2", new Timestamp(2));
        var nodeC = new Revision("c", "c", "3", new Timestamp(3));
        var edgeAB2 = new DependencyEdge(nodeA, nodeB2, "", false, emptyList());
        var edgeB2C = new DependencyEdge(nodeB2, nodeC, "", false, emptyList());
        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addEdge(nodeA, nodeB, edgeAB);
        graph.addVertex(nodeB2);
        graph.addEdge(nodeA, nodeB2, edgeAB2);
        graph.addVertex(nodeC);
        graph.addEdge(nodeB2, nodeC, edgeB2C);
        var actual = graphMavenResolver.filterDependencyGraphByTimestamp(graph, new Timestamp(1));
        assertEquals(expected, actual);
    }

    @Test
    public void filterSuccessorsByTimestampTest() {
        var successors = List.of(
                new Revision("a", "a", "1", new Timestamp(1)),
                new Revision("a", "a", "2", new Timestamp(2)),
                new Revision("a", "a", "3", new Timestamp(3)),
                new Revision("b", "b", "2", new Timestamp(2)),
                new Revision("b", "b", "3", new Timestamp(3)),
                new Revision("b", "b", "4", new Timestamp(4))
        );
        var expected = List.of(
                new Revision("a", "a", "3", new Timestamp(3)),
                new Revision("b", "b", "3", new Timestamp(3))
        );
        var actual = graphMavenResolver.filterSuccessorsByTimestamp(successors, 3, false);
        assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void filterDependencyGraphByOptionalTest() {
        var nodeA = new Revision("a", "a", "1", new Timestamp(1));
        var nodeB = new Revision("b", "b", "2", new Timestamp(1));
        var edgeAB = new DependencyEdge(nodeA, nodeB, "", false, emptyList());
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        var nodeB2 = new Revision("b", "b", "2", new Timestamp(2));
        var nodeC = new Revision("c", "c", "3", new Timestamp(3));
        var edgeAB2 = new DependencyEdge(nodeA, nodeB2, "", true, emptyList());
        var edgeB2C = new DependencyEdge(nodeB2, nodeC, "", false, emptyList());
        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addEdge(nodeA, nodeB, edgeAB);
        graph.addVertex(nodeB2);
        graph.addEdge(nodeA, nodeB2, edgeAB2);
        graph.addVertex(nodeC);
        graph.addEdge(nodeB2, nodeC, edgeB2C);
        var expected = List.of(nodeB);
        var actual = graphMavenResolver.filterOptionalSuccessors(graph.outgoingEdgesOf(nodeA)).stream().map(e -> e.target).collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    @Test
    public void filterDependencyGraphByScopeTest() {
        var nodeA = new Revision("a", "a", "1", new Timestamp(1));
        var nodeB = new Revision("b", "b", "2", new Timestamp(1));
        var edgeAB = new DependencyEdge(nodeA, nodeB, "", false, emptyList());
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        var nodeB2 = new Revision("b", "b", "2", new Timestamp(2));
        var nodeC = new Revision("c", "c", "3", new Timestamp(3));
        var edgeAB2 = new DependencyEdge(nodeA, nodeB2, "test", false, emptyList());
        var edgeB2C = new DependencyEdge(nodeB2, nodeC, "compile", false, emptyList());
        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addEdge(nodeA, nodeB, edgeAB);
        graph.addVertex(nodeB2);
        graph.addEdge(nodeA, nodeB2, edgeAB2);
        graph.addVertex(nodeC);
        graph.addEdge(nodeB2, nodeC, edgeB2C);
        var expected = List.of(nodeB);
        var actual = graphMavenResolver.filterSuccessorsByScope(graph.outgoingEdgesOf(nodeA), List.of("compile")).stream().map(e -> e.target).collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    @Test
    public void filterDuplicateProducts() {
        var depthRevision = List.of(
                new Pair<>(new Revision("a", "a", "1", new Timestamp(1)), 1),
                new Pair<>(new Revision("a", "a", "2", new Timestamp(1)), 2),
                new Pair<>(new Revision("b", "b", "2", new Timestamp(1)), 2),
                new Pair<>(new Revision("b", "b", "1", new Timestamp(1)), 3)
        );
        var expected = List.of(
                new Revision("a", "a", "1", new Timestamp(1)),
                new Revision("b", "b", "2", new Timestamp(1))
        );
        var actual = graphMavenResolver.resolveConflicts(new HashSet<>(depthRevision));
        assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void descendantTest() {
        // A -> B -> C
        //  \-> D -> E
        Map<Revision, Revision> descendants = new HashMap<>();
        var A = new Revision("a", "a", "a", null);
        var B = new Revision("b", "b", "b", null);
        var C = new Revision("c", "c", "c", null);
        var D = new Revision("d", "d", "d", null);
        var E = new Revision("e", "e", "e", null);
        descendants.put(B, A);
        descendants.put(C, B);
        descendants.put(D, A);
        descendants.put(E, D);
        assertTrue(graphMavenResolver.isDescendantOf(B, A, descendants));
        assertTrue(graphMavenResolver.isDescendantOf(C, B, descendants));
        assertTrue(graphMavenResolver.isDescendantOf(C, A, descendants));
        assertTrue(graphMavenResolver.isDescendantOf(D, A, descendants));
        assertTrue(graphMavenResolver.isDescendantOf(E, A, descendants));
        assertTrue(graphMavenResolver.isDescendantOf(E, D, descendants));
        assertFalse(graphMavenResolver.isDescendantOf(E, B, descendants));
        assertFalse(graphMavenResolver.isDescendantOf(C, D, descendants));
        assertFalse(graphMavenResolver.isDescendantOf(C, E, descendants));
        assertFalse(graphMavenResolver.isDescendantOf(E, C, descendants));
        assertFalse(graphMavenResolver.isDescendantOf(B, D, descendants));
        assertFalse(graphMavenResolver.isDescendantOf(B, D, descendants));
    }

    @Test
    public void filterExclusionsTest() {
        // A -> B -> C:1
        //  \-> D -> E -> C:2
        //      D excludes C
        Map<Revision, Revision> descendants = new HashMap<>();
        var A = new Revision("a", "a", "a", null);
        var B = new Revision("b", "b", "b", null);
        var C1 = new Revision("c", "c", "c1", null);
        var C2 = new Revision("c", "c", "c2", null);
        var D = new Revision("d", "d", "d", null);
        var E = new Revision("e", "e", "e", null);
        descendants.put(B, A);
        descendants.put(C1, B);
        descendants.put(D, A);
        descendants.put(E, D);
        descendants.put(C2, E);
        var exclusions = List.of(new Pair<>(D, new MavenProduct("c", "c")));
        var dependencies = Set.of(A, B, C1, C2, D);
        var expected = Set.of(A, B, C1, D);
        var actual = graphMavenResolver.filterDependenciesByExclusions(dependencies, exclusions, descendants);
        assertEquals(expected, actual);
    }
}
