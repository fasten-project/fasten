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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraphMavenResolverTest {

    private GraphMavenResolver graphMavenResolver;

    @BeforeEach
    public void setup() {
        graphMavenResolver = new GraphMavenResolver();
    }

    @Test
    public void filterDependencyGraphByTimestampTest() {
        var expected = new HashMap<Dependency, List<Pair<Dependency, Timestamp>>>();
        expected.put(new Dependency("a:a:1"), List.of(
                new ImmutablePair<>(new Dependency("b:b:1"), new Timestamp(1))
        ));
        var graph = new HashMap<Dependency, List<Pair<Dependency, Timestamp>>>();
        graph.put(new Dependency("a:a:1"), List.of(
                new ImmutablePair<>(new Dependency("b:b:1"), new Timestamp(1)),
                new ImmutablePair<>(new Dependency("b:b:2"), new Timestamp(2))
        ));
        graph.put(new Dependency("b:b:2"), List.of(
                new ImmutablePair<>(new Dependency("c:c:3"), new Timestamp(3))
        ));
        var actual = graphMavenResolver.filterDependencyGraphByTimestamp(graph, new Timestamp(1));
        assertEquals(expected, actual);
    }
}
