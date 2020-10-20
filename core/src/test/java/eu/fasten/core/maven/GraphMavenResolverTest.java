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

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.graph.DependencyEdge;
import eu.fasten.core.maven.data.graph.DependencyNode;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraphMavenResolverTest {

    private GraphMavenResolver graphMavenResolver;

    @BeforeEach
    public void setup() {
        graphMavenResolver = new GraphMavenResolver();
    }

    @Test
    public void filterDependencyGraphByTimestampTest() {
        var expected = new DefaultDirectedGraph<DependencyNode, DependencyEdge>(DependencyEdge.class);
        var nodeA = new DependencyNode(new Dependency("a:a:1"), new Timestamp(-1));
        var nodeB = new DependencyNode(new Dependency("b:b:2"), new Timestamp(1));
        var edgeAB = new DependencyEdge("", false, Collections.emptyList());
        expected.addVertex(nodeA);
        expected.addVertex(nodeB);
        expected.addEdge(nodeA, nodeB, edgeAB);
        var graph = new DefaultDirectedGraph<DependencyNode, DependencyEdge>(DependencyEdge.class);
        var nodeB2 = new DependencyNode(new Dependency("b:b:2"), new Timestamp(2));
        var nodeC = new DependencyNode(new Dependency("c:c:3"), new Timestamp(3));
        var edgeAB2 = new DependencyEdge("", false, Collections.emptyList());
        var edgeB2C = new DependencyEdge("", false, Collections.emptyList());
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
}
