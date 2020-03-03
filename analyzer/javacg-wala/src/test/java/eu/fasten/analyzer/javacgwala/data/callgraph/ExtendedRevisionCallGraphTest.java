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

package eu.fasten.analyzer.javacgwala.data.callgraph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.FastenJavaURI;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ExtendedRevisionCallGraphTest {
    static ExtendedRevisionCallGraph cg;
    static String cgString;

    @BeforeAll
    public static void generateCallGraph() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
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
        final var mavenCoordinate = new MavenCoordinate("diff", "example", "0.0.1");

        var file = new File(Thread.currentThread().getContextClassLoader().getResource(
                "DiffExampleFirst.jar").getFile());

        final var partialCG =
                WalaResultAnalyzer.wrap(CallGraphConstructor.generateCallGraph(file.getAbsolutePath()));

        cg = ExtendedRevisionCallGraph.extendedBuilder()
                .forge("mvn")
                .product(mavenCoordinate.getProduct())
                .cgGenerator("WALA")
                .version(mavenCoordinate.getVersionConstraint()).timestamp(1574072773)
                .depset(MavenCoordinate.MavenResolver.resolveDependencies(mavenCoordinate.getCoordinate()))
                .graph(partialCG.getGraph())
                .classHierarchy(partialCG.getClassHierarchy())
                .build();

        cg.sortResolvedCalls();

        cgString = "{\"product\":\"diff.example\"," +
                "\"forge\":\"mvn\"," +
                "\"generator\":\"WALA\"," +
                "\"depset\":[]," +
                "\"version\":\"0.0.1\"," +
                "\"cha\":{" +
                "\"/name.space/DiffExampleFirst\":{" +
                "\"methods\":{" +
                "\"0\":\"/name.space/DiffExampleFirst.DiffExampleFirst()%2Fjava.lang%2FVoidType\"," +
                "\"1\":\"/name.space/DiffExampleFirst.c()%2Fjava.lang%2FVoidType\"," +
                "\"2\":\"/name.space/DiffExampleFirst.d()%2Fjava.lang%2FVoidType\"," +
                "\"3\":\"/name.space/DiffExampleFirst.b()%2Fjava.lang%2FVoidType\"," +
                "\"4\":\"/name.space/DiffExampleFirst.a()%2Fjava.lang%2FVoidType\"" +
                "}," +
                "\"superInterfaces\":[]," +
                "\"sourceFile\":\"DiffExampleFirst.java\"," +
                "\"superClasses\":[\"/java.lang/Object\"]}" +
                "}," +
                "\"graph\":{" +
                "\"resolvedCalls\":[" +
                "[1,2]," +
                "[3,1]," +
                "[4,3]" +
                "]," +
                "\"unresolvedCalls\":[[\"0\",\"///java.lang/Object.Object()VoidType\"," +
                "{\"invokespecial\":\"1\"}]]}," +
                "\"timestamp\":1574072773}";
    }

    @Test
    public void testToJSON() {

        assertEquals(cgString, cg.toJSON().toString());

    }

    @Test
    public void testCreate() throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {

        final var rcg = PartialCallGraph.createExtendedRevisionCallGraph(
                new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29"),
                1574072773);

        assertSLF4j(rcg);

    }

    @Test
    public void testExtendedBuilder() throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {

        final var coord = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29");
        final var partialCG = CallGraphConstructor.build(coord);

        final var rcg = ExtendedRevisionCallGraph.extendedBuilder()
                .forge("mvn")
                .product(coord.getProduct())
                .version(coord.getVersionConstraint())
                .cgGenerator("WALA")
                .timestamp(1574072773)
                .depset(MavenCoordinate.MavenResolver.resolveDependencies(coord.getCoordinate()))
                .graph(partialCG.getGraph())
                .classHierarchy(partialCG.getClassHierarchy())
                .build();

        assertSLF4j(rcg);
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
    public void testExtendedRevisionCallGraph() {

        final var cgFromJSON = new ExtendedRevisionCallGraph(new JSONObject(cgString));

        assertEquals("mvn", cgFromJSON.forge);
        assertEquals("diff.example", cgFromJSON.product);
        assertEquals("0.0.1", cgFromJSON.version);
        assertEquals(1574072773, cgFromJSON.timestamp);
        assertEquals("WALA", cgFromJSON.getCgGenerator());
        assertEquals(0, cgFromJSON.depset.size());
        assertArrayEquals(new int[]{1, 2}, cgFromJSON.getGraph().getResolvedCalls().get(0));
        assertArrayEquals(new int[]{3, 1}, cgFromJSON.getGraph().getResolvedCalls().get(1));
        assertArrayEquals(new int[]{4, 3}, cgFromJSON.getGraph().getResolvedCalls().get(2));

        assertEquals(cgFromJSON.getGraph().getUnresolvedCalls(), cg.getGraph().getUnresolvedCalls());

        for (final var entry : cgFromJSON.getClassHierarchy().entrySet()) {
            assertTrue(cg.getClassHierarchy().containsKey(entry.getKey()));
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getMethods(), entry.getValue().getMethods());
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSourceFileName(),
                    entry.getValue().getSourceFileName());
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSuperClasses(),
                    (entry.getValue().getSuperClasses()));
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSuperInterfaces(),
                    (entry.getValue().getSuperInterfaces()));
        }
    }
}