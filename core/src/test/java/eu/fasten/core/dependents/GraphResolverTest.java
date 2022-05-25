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

package eu.fasten.core.dependents;

import eu.fasten.core.dependents.data.*;
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

public class GraphResolverTest {

    private GraphResolver graphResolver;

    @BeforeEach
    public void setup() {
        graphResolver = new GraphResolver();
    }

    @Test
    public void filterDependentsByTimestampTest() {
        var successors = List.of(
                new Revision("a", "1", new Timestamp(1)),
                new Revision("a", "2", new Timestamp(2)),
                new Revision("a", "3", new Timestamp(3)),
                new Revision("b", "2", new Timestamp(2)),
                new Revision("b", "3", new Timestamp(3)),
                new Revision("b", "4", new Timestamp(4))
        );
        var expected = List.of(
                new Revision("a", "3", new Timestamp(3)),
                new Revision("b", "3", new Timestamp(3)),
                new Revision("b", "4", new Timestamp(4))
        );
        var actual = graphResolver.filterDependentsByTimestamp(successors, 3);
        assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }
}
