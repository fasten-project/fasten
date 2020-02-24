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
import java.io.FileNotFoundException;
import java.util.Arrays;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.core.data.FastenJavaURI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;

public class ExtendedRevisionCallGraphTest {

    @Test
    public void testToJSON() {

        /**
         * package name.space;
         *
         * public class DiffExampleFirst {
         *     public DiffExampleFirst() {
         *     }
         *
         *     public static void d() {
         *     }
         *
         *     public static void c() {
         *         d();
         *     }
         *
         *     public static void b() {
         *         c();
         *     }
         *
         *     public static void a() {
         *         b();
         *     }
         * }
         */
        final var partialCG = new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("DiffExampleFirst.class").getFile()));

        final var cg = ExtendedRevisionCallGraph.createWithOPAL("mvn",
                new MavenCoordinate("diff","example","0.0.1"),
                1574072773,
                partialCG);

        cg.sortResolvedCalls();
        assertEquals(
                cg.toJSON().toString(),
                "{\"product\":\"diff.example\"," +
                        "\"forge\":\"mvn\"," +
                        "\"depset\":[]," +
                        "\"version\":\"0.0.1\"," +
                        "\"cha\":{" +
                            "\"/name.space/DiffExampleFirst\":{" +
                                "\"methods\":[" +
                                    "[\"0\",\"/name.space/DiffExampleFirst.DiffExampleFirst()%2Fjava.lang%2FVoid\"]," +
                                    "[\"1\",\"/name.space/DiffExampleFirst.a()%2Fjava.lang%2FVoid\"]," +
                                    "[\"2\",\"/name.space/DiffExampleFirst.b()%2Fjava.lang%2FVoid\"]," +
                                    "[\"3\",\"/name.space/DiffExampleFirst.c()%2Fjava.lang%2FVoid\"]," +
                                    "[\"4\",\"/name.space/DiffExampleFirst.d()%2Fjava.lang%2FVoid\"]" +
                                "]," +
                        "\"superInterfaces\":[]," +
                        "\"superClasses\":[\"/java.lang/Object\"]}}," +
                        "\"graph\":{" +
                            "\"resolvedCalls\":[" +
                                "[1,2]," +
                                "[2,3]," +
                                "[3,4]" +
                            "]," +
                            "\"unrisolvedCalls\":{" +
                                "\"[0, ///java.lang/Object.Object()Void]\":{\"invokespecial\":1}}" +
                            "}," +
                        "\"timestamp\":1574072773," +
                        "\"Generator\":\"OPAL\"}"

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
    public void create() throws FileNotFoundException {

        //tests both signature of method create of Extended revision call graph.
        var extendedRevisionCallGraph = ExtendedRevisionCallGraph.createWithOPAL("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29"),
                1574072773,
                new PartialCallGraph(
                        MavenCoordinate.MavenResolver.downloadJar("org.slf4j:slf4j-api:1.7.29").orElseThrow(RuntimeException::new)
                )
        );

        assertSLF4j(extendedRevisionCallGraph);

        var cg = ExtendedRevisionCallGraph.createWithOPAL("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29"), 1574072773);

        assertSLF4j(cg);


    }

    private void assertSLF4j(ExtendedRevisionCallGraph cg) {
        assertNotNull(cg);
        assertEquals("mvn", cg.forge);
        assertEquals("1.7.29", cg.version);
        assertEquals(1574072773, cg.timestamp);
        assertEquals(new FastenJavaURI("fasten://mvn!org.slf4j.slf4j-api$1.7.29"), cg.uri);
        assertEquals(new FastenJavaURI("fasten://org.slf4j.slf4j-api$1.7.29"), cg.forgelessUri);
        assertEquals("org.slf4j.slf4j-api", cg.product);
        assertNotEquals(0, cg.getGraph().size());
    }

}