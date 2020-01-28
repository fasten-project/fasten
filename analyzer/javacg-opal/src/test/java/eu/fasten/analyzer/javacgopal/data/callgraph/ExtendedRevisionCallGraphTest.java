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

package eu.fasten.analyzer.javacgopal.data.callgraph;

import java.io.File;
import java.util.Arrays;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.core.data.FastenJavaURI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;

public class ExtendedRevisionCallGraphTest {

    @Test
    public void toJSON() {

        var importerGraph = new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("DiffExampleFirst.class").getFile()));

        var firstGraph = new ExtendedRevisionCallGraph("mvn",
                "DiffExample",
                "1.7.29",
                1574072773,
                Arrays.asList(),
                importerGraph.toURIGraph(),
                PartialCallGraph.toURIHierarchy(importerGraph.getClassHierarchy()));

        assertEquals(
                firstGraph.toJSON().get("cha").toString(),
                "{\"/name.space/DiffExampleFirst\":" +
                        "{\"methods\":" +
                        "[\"/name.space/DiffExampleFirst.DiffExampleFirst()%2Fjava.lang%2FVoid\"," +
                        "\"/name.space/DiffExampleFirst.a()%2Fjava.lang%2FVoid\"," +
                        "\"/name.space/DiffExampleFirst.b()%2Fjava.lang%2FVoid\"," +
                        "\"/name.space/DiffExampleFirst.c()%2Fjava.lang%2FVoid\"," +
                        "\"/name.space/DiffExampleFirst.d()%2Fjava.lang%2FVoid\"]," +
                        "\"superInterfaces\":[]," +
                        "\"superClasses\":[\"/java.lang/Object\"]}}"
        );

//        var g = new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("CallBack").getFile()));
//        var s = new ExtendedRevisionCallGraph("mvn",
//                "DiffExample",
//                "1.7.29",
//                1574072773,
//                Arrays.asList(),
//                g.toURIGraph(),
//                PartialCallGraph.toURIHierarchy(g.getClassHierarchy()));

    }

    @Test
    public void create() {

        var extendedRevisionCallGraph = ExtendedRevisionCallGraph.create("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29"),
                1574072773,
                new PartialCallGraph(
                        MavenCoordinate.MavenResolver.downloadJar("org.slf4j:slf4j-api:1.7.29").orElseThrow(RuntimeException::new)
                )
        );

        extendedRevisionCallGraph.toJSON();
        assertNotNull(extendedRevisionCallGraph);
        assertEquals("mvn", extendedRevisionCallGraph.forge);
        assertEquals("1.7.29", extendedRevisionCallGraph.version);
        assertEquals(1574072773, extendedRevisionCallGraph.timestamp);
        assertEquals(new FastenJavaURI("fasten://mvn!org.slf4j.slf4j-api$1.7.29"), extendedRevisionCallGraph.uri);
        assertEquals(new FastenJavaURI("fasten://org.slf4j.slf4j-api$1.7.29"), extendedRevisionCallGraph.forgelessUri);
        assertEquals("org.slf4j.slf4j-api", extendedRevisionCallGraph.product);
        assertNotEquals(0, extendedRevisionCallGraph.graph.size());


        var RevisionCallGraphForZarbosoft = ExtendedRevisionCallGraph.create("mvn",
                new MavenCoordinate("com.zarbosoft", "coroutines-core", "0.0.3"),
                1574072773,
                new PartialCallGraph(
                        MavenCoordinate.MavenResolver.downloadJar("com.zarbosoft:coroutines-core:0.0.3").orElseThrow(RuntimeException::new)
                )
        ).toJSON();

    }

}