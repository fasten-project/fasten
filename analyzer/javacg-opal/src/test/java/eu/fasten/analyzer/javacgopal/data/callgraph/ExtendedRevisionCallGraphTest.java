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
import java.io.IOException;
import java.net.URISyntaxException;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.core.data.FastenJavaURI;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExtendedRevisionCallGraphTest {
    static ExtendedRevisionCallGraph cg;
    static String cgString;

    @BeforeClass
    public static void generateCallGraph() {
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
        cg = ExtendedRevisionCallGraph.createWithOPAL("mvn",
                new MavenCoordinate("diff", "example", "0.0.1"),
                1574072773,
                new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("DiffExampleFirst.class").getFile())));

        cgString = "{\"product\":\"diff.example\"," +
                "\"forge\":\"mvn\"," +
                "\"generator\":\"OPAL\"," +
                "\"depset\":[]," +
                "\"version\":\"0.0.1\"," +
                "\"cha\":{" +
                "\"/name.space/DiffExampleFirst\":{" +
                "\"methods\":{" +
                "\"0\":\"/name.space/DiffExampleFirst.DiffExampleFirst()%2Fjava.lang%2FVoid\"," +
                "\"1\":\"/name.space/DiffExampleFirst.a()%2Fjava.lang%2FVoid\"," +
                "\"2\":\"/name.space/DiffExampleFirst.b()%2Fjava.lang%2FVoid\"," +
                "\"3\":\"/name.space/DiffExampleFirst.c()%2Fjava.lang%2FVoid\"," +
                "\"4\":\"/name.space/DiffExampleFirst.d()%2Fjava.lang%2FVoid\"" +
                "}," +
                "\"superInterfaces\":[]," +
                "\"sourceFile\":\"DiffExampleFirst.java\"," +
                "\"superClasses\":[\"/java.lang/Object\"]}" +
                "}," +
                "\"graph\":{" +
                "\"resolvedCalls\":[" +
                "[1,2]," +
                "[2,3]," +
                "[3,4]" +
                "]," +
                "\"unresolvedCalls\":[[\"0\",\"///java.lang/Object.Object()Void\",{\"invokespecial\":1}]]}," +
                "\"timestamp\":1574072773}";
    }

    @Test
    public void testToJSON() {

        assertEquals(cgString, cg.toJSON().toString());

    }

    @Test
    public void testCreateWithOPAL() throws FileNotFoundException {

        //tests both signature of method create of Extended revision call graph.
        final var ercg1 = ExtendedRevisionCallGraph.createWithOPAL("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29"),
                1574072773,
                new PartialCallGraph(
                        MavenCoordinate.MavenResolver.downloadJar("org.slf4j:slf4j-api:1.7.29").orElseThrow(RuntimeException::new)
                )
        );

        assertSLF4j(ercg1);

        final var ercg2 = ExtendedRevisionCallGraph.createWithOPAL("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29"), 1574072773);

        assertSLF4j(ercg2);

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

    @Test
    public void testExtendedRevisionCallGraph() throws URISyntaxException, IOException {

        final var cgFromJSON = new ExtendedRevisionCallGraph(new JSONObject(cgString));

        assertEquals("mvn", cgFromJSON.forge);
        assertEquals("diff.example", cgFromJSON.product);
        assertEquals("0.0.1", cgFromJSON.version);
        assertEquals(1574072773, cgFromJSON.timestamp);
        assertEquals("OPAL", cgFromJSON.getCgGenerator());
        assertEquals(0, cgFromJSON.depset.size());
        assertArrayEquals(new int[]{1, 2}, cgFromJSON.getGraph().getResolvedCalls().get(0));
        assertArrayEquals(new int[]{2, 3}, cgFromJSON.getGraph().getResolvedCalls().get(1));
        assertArrayEquals(new int[]{3, 4}, cgFromJSON.getGraph().getResolvedCalls().get(2));

        assertTrue(cgFromJSON.getGraph().getUnresolvedCalls().equals(cg.getGraph().getUnresolvedCalls()));

        for (final var entry : cgFromJSON.getClassHierarchy().entrySet()) {
            assertTrue(cg.getClassHierarchy().containsKey(entry.getKey()));
            assertTrue(cg.getClassHierarchy().get(entry.getKey()).getMethods().equals(entry.getValue().getMethods()));
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSourceFileName(), entry.getValue().getSourceFileName());
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSuperClasses(), (entry.getValue().getSuperClasses()));
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSuperInterfaces(), (entry.getValue().getSuperInterfaces()));
        }
    }
}