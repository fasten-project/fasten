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
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
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
        var actual = graphMavenResolver.filterDependenciesByTimestamp(successors, 3);
        assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void filterDependencyGraphByOptionalTest() {
        var nodeA = new Revision("a", "a", "1", new Timestamp(1));
        var nodeB = new Revision("b", "b", "2", new Timestamp(1));
        var edgeAB = new DependencyEdge(nodeA, nodeB, "", false, emptyList(), "");
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        var nodeB2 = new Revision("b", "b", "2", new Timestamp(2));
        var nodeC = new Revision("c", "c", "3", new Timestamp(3));
        var edgeAB2 = new DependencyEdge(nodeA, nodeB2, "", true, emptyList(), "");
        var edgeB2C = new DependencyEdge(nodeB2, nodeC, "", false, emptyList(), "");
        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addEdge(nodeA, nodeB, edgeAB);
        graph.addVertex(nodeB2);
        graph.addEdge(nodeA, nodeB2, edgeAB2);
        graph.addVertex(nodeC);
        graph.addEdge(nodeB2, nodeC, edgeB2C);
        var expected = List.of(nodeB);
        var actual = graphMavenResolver.filterOptionalSuccessors(new ObjectLinkedOpenHashSet<>(graph.outgoingEdgesOf(nodeA))).stream().map(e -> e.target).collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    @Test
    public void filterDependencyGraphByScopeTest() {
        var nodeA = new Revision("a", "a", "1", new Timestamp(1));
        var nodeB = new Revision("b", "b", "2", new Timestamp(1));
        var edgeAB = new DependencyEdge(nodeA, nodeB, "", false, emptyList(), "");
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        var nodeB2 = new Revision("b", "b", "2", new Timestamp(2));
        var nodeC = new Revision("c", "c", "3", new Timestamp(3));
        var edgeAB2 = new DependencyEdge(nodeA, nodeB2, "test", false, emptyList(), "");
        var edgeB2C = new DependencyEdge(nodeB2, nodeC, "compile", false, emptyList(), "");
        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addEdge(nodeA, nodeB, edgeAB);
        graph.addVertex(nodeB2);
        graph.addEdge(nodeA, nodeB2, edgeAB2);
        graph.addVertex(nodeC);
        graph.addEdge(nodeB2, nodeC, edgeB2C);
        var expected = List.of(nodeB);
        var actual = graphMavenResolver.filterSuccessorsByScope(new ObjectLinkedOpenHashSet<>(graph.outgoingEdgesOf(nodeA)), List.of("compile")).stream().map(e -> e.target).collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    @Test
    public void filterDependencyGraphByTypeTest() {
        var nodeA = new Revision("a", "a", "1", new Timestamp(1));
        var nodeB = new Revision("b", "b", "2", new Timestamp(1));
        var edgeAB = new DependencyEdge(nodeA, nodeB, "", false, emptyList(), "");
        var graph = new DefaultDirectedGraph<Revision, DependencyEdge>(DependencyEdge.class);
        var nodeB2 = new Revision("b", "b", "2", new Timestamp(2));
        var nodeC = new Revision("c", "c", "3", new Timestamp(3));
        var edgeAB2 = new DependencyEdge(nodeA, nodeB2, "compile", false, emptyList(), "pom");
        var edgeB2C = new DependencyEdge(nodeB2, nodeC, "compile", false, emptyList(), "");
        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addEdge(nodeA, nodeB, edgeAB);
        graph.addVertex(nodeB2);
        graph.addEdge(nodeA, nodeB2, edgeAB2);
        graph.addVertex(nodeC);
        graph.addEdge(nodeB2, nodeC, edgeB2C);
        var expected = List.of(nodeB);
        var actual = graphMavenResolver.filterSuccessorsByScope(new ObjectLinkedOpenHashSet<>(graph.outgoingEdgesOf(nodeA)), List.of("compile")).stream().map(e -> e.target).collect(Collectors.toList());
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
        var actual = graphMavenResolver.resolveConflicts(new ObjectLinkedOpenHashSet<>(depthRevision));
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
