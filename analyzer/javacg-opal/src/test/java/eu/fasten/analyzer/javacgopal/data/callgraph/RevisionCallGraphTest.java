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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import eu.fasten.analyzer.baseanalyzer.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

public class RevisionCallGraphTest {
    static RevisionCallGraph cg;
    static String cgString;

    /**
     * <code>
     * package name.space;
     *
     * public class DiffExampleFirst { public DiffExampleFirst() { }
     *
     * public static void d() { }
     *
     * public static void c() { d(); }
     *
     * public static void b() { c(); }
     *
     * public static void a() { b(); } }
     * </code>
     */
    @BeforeClass
    public static void generateCallGraph() {

        final var mavenCoordinate = new MavenCoordinate("diff", "example", "0.0.1");
        final var partialCG = new PartialCallGraph(new File(
            Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("DiffExampleFirst.class"))
                .getFile()));

        cg = RevisionCallGraph.extendedBuilder()
            .forge("mvn")
            .product(mavenCoordinate.getProduct())
            .cgGenerator(partialCG.getGENERATOR())
            .version(mavenCoordinate.getVersionConstraint()).timestamp(1574072773)
            .depset(
                MavenCoordinate.MavenResolver.resolveDependencies(mavenCoordinate.getCoordinate()))
            .graph(partialCG.getGraph())
            .classHierarchy(partialCG.getClassHierarchy())
            .build();

        cg.sortInternalCalls();

        cgString = "{\"product\":\"diff:example\","
            + "\"forge\":\"mvn\","
            + "\"generator\":\"OPAL\","
            + "\"depset\":[],"
            + "\"version\":\"0.0.1\","
            + "\"cha\":{"
            + "\"/name.space/DiffExampleFirst\":{"
            + "\"methods\":{"
            + "\"0\":\"/name.space/DiffExampleFirst.DiffExampleFirst()%2Fjava.lang%2FVoidType\","
            + "\"1\":\"/name.space/DiffExampleFirst.a()%2Fjava.lang%2FVoidType\","
            + "\"2\":\"/name.space/DiffExampleFirst.b()%2Fjava.lang%2FVoidType\","
            + "\"3\":\"/name.space/DiffExampleFirst.c()%2Fjava.lang%2FVoidType\","
            + "\"4\":\"/name.space/DiffExampleFirst.d()%2Fjava.lang%2FVoidType\""
            + "},"
            + "\"superInterfaces\":[],"
            + "\"sourceFile\":\"DiffExampleFirst.java\","
            + "\"superClasses\":[\"/java.lang/Object\"]}"
            + "},"
            + "\"graph\":{"
            + "\"internalCalls\":[[1,2],[2,3],[3,4]],"
            + "\"externalCalls\":[[\"0\",\"///java.lang/Object.Object()VoidType\","
            + "{\"invokespecial\":\"1\"}]]},"
            + "\"timestamp\":1574072773}";
    }

    public static void assertSLF4j(RevisionCallGraph cg) {
        assertNotNull(cg);
        assertEquals("mvn", cg.forge);
        assertEquals("1.7.29", cg.version);
        assertEquals(1574072773, cg.timestamp);
        assertEquals(new FastenJavaURI("fasten://mvn!org.slf4j:slf4j-api$1.7.29"), cg.uri);
        assertEquals(new FastenJavaURI("fasten://org.slf4j:slf4j-api$1.7.29"), cg.forgelessUri);
        assertEquals("org.slf4j:slf4j-api", cg.product);
        assertNotEquals(0, cg.getGraph().size());
    }

    @Test
    public void testToJSON() {

        assertEquals(cgString, cg.toJSON().toString());

    }

    @Test
    public void testExtendedBuilder() throws FileNotFoundException {

        final var coord = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29");
        final var partialCG = new PartialCallGraph(
            MavenCoordinate.MavenResolver.downloadJar("org.slf4j:slf4j-api:1.7.29")
                .orElseThrow(RuntimeException::new)
        );

        final var rcg = RevisionCallGraph.extendedBuilder()
            .forge("mvn")
            .product(coord.getProduct())
            .version(coord.getVersionConstraint())
            .cgGenerator(partialCG.getGENERATOR())
            .timestamp(1574072773)
            .depset(MavenCoordinate.MavenResolver.resolveDependencies(coord.getCoordinate()))
            .graph(partialCG.getGraph())
            .classHierarchy(partialCG.getClassHierarchy())
            .build();

        assertSLF4j(rcg);
    }

    @Test
    public void testExtendedRevisionCallGraph() throws JSONException {

        final var cgFromJSON = new RevisionCallGraph(new JSONObject(cgString));

        assertEquals("mvn", cgFromJSON.forge);
        assertEquals("diff:example", cgFromJSON.product);
        assertEquals("0.0.1", cgFromJSON.version);
        assertEquals(1574072773, cgFromJSON.timestamp);
        assertEquals("OPAL", cgFromJSON.getCgGenerator());
        assertEquals(0, cgFromJSON.depset.size());
        assertEquals(List.of(1, 2),
            cgFromJSON.getGraph().getInternalCalls().get(0));
        assertEquals(List.of(2, 3),
            cgFromJSON.getGraph().getInternalCalls().get(1));
        assertEquals(List.of(3, 4),
            cgFromJSON.getGraph().getInternalCalls().get(2));

        assertEquals(cgFromJSON.getGraph().getExternalCalls(),
            cg.getGraph().getExternalCalls());

        for (final var entry : cgFromJSON.getClassHierarchy().entrySet()) {
            assertTrue(cg.getClassHierarchy().containsKey(entry.getKey()));
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getMethods(),
                entry.getValue().getMethods());
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSourceFileName(),
                entry.getValue().getSourceFileName());
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSuperClasses(),
                (entry.getValue().getSuperClasses()));
            assertEquals(cg.getClassHierarchy().get(entry.getKey()).getSuperInterfaces(),
                (entry.getValue().getSuperInterfaces()));
        }
    }


    @Test
    public void testgetCHAFromJSON() throws JSONException {
        assertEquals("{/name.space/DiffExampleFirst="
                + "Type{sourceFileName='DiffExampleFirst.java',"
                + " methods={"
                + "0=/name.space/DiffExampleFirst.DiffExampleFirst()%2Fjava.lang%2FVoidType,"
                + " 1=/name.space/DiffExampleFirst.a()%2Fjava.lang%2FVoidType,"
                + " 2=/name.space/DiffExampleFirst.b()%2Fjava.lang%2FVoidType,"
                + " 3=/name.space/DiffExampleFirst.c()%2Fjava.lang%2FVoidType,"
                + " 4=/name.space/DiffExampleFirst.d()%2Fjava.lang%2FVoidType},"
                + " superClasses=[/java.lang/Object],"
                + " superInterfaces=[]}}",
            RevisionCallGraph.getCHAFromJSON(new JSONObject(cgString).getJSONObject("cha"))
                .toString());
    }

    @Test
    public void testSortInternalCalls() throws JSONException {
        final var unorderdCGString = cgString.replace("[[1,2],[2,3],[3,4]]", "[[2,3],[3,4],[1,2]]");
        final var unorderedCG = new RevisionCallGraph(new JSONObject(unorderdCGString));
        assertEquals("[[2, 3], [3, 4], [1, 2]]",
            unorderedCG.getGraph().getInternalCalls().toString());
        unorderedCG.sortInternalCalls();
        assertEquals("[[1, 2], [2, 3], [3, 4]]",
            unorderedCG.getGraph().getInternalCalls().toString());
    }

    @Test
    public void testIsCallGraphEmpty() throws FileNotFoundException {

        final var coord = new MavenCoordinate("activemq", "activemq", "release-1.5");
        final var partialCG = new PartialCallGraph(
            MavenCoordinate.MavenResolver.downloadJar("activemq:activemq:release-1.5")
                .orElseThrow(RuntimeException::new)
        );

        final var rcg = RevisionCallGraph.extendedBuilder()
            .forge("mvn")
            .product(coord.getProduct())
            .version(coord.getVersionConstraint())
            .cgGenerator(partialCG.getGENERATOR())
            .timestamp(1574072773)
            .depset(MavenCoordinate.MavenResolver.resolveDependencies(coord.getCoordinate()))
            .graph(partialCG.getGraph())
            .classHierarchy(partialCG.getClassHierarchy())
            .build();

        assertTrue(rcg.isCallGraphEmpty());
    }

    @Test
    public void testGetCgGenerator() {
        assertEquals("OPAL", cg.getCgGenerator());
    }

    @Test
    public void testGetClassHierarchy() {
        assertEquals("{/name.space/DiffExampleFirst="
                + "Type{sourceFileName='DiffExampleFirst.java',"
                + " methods={"
                + "0=/name.space/DiffExampleFirst.DiffExampleFirst()%2Fjava.lang%2FVoidType,"
                + " 1=/name.space/DiffExampleFirst.a()%2Fjava.lang%2FVoidType,"
                + " 2=/name.space/DiffExampleFirst.b()%2Fjava.lang%2FVoidType,"
                + " 3=/name.space/DiffExampleFirst.c()%2Fjava.lang%2FVoidType,"
                + " 4=/name.space/DiffExampleFirst.d()%2Fjava.lang%2FVoidType},"
                + " superClasses=[/java.lang/Object],"
                + " superInterfaces=[]}}",
            cg.getClassHierarchy().toString());
    }

    @Test
    public void getGraph() {
        assertEquals("{"
                + "\"internalCalls\":[[1,2],[2,3],[3,4]],"
                + "\"externalCalls\":[[\"0\",\"///java.lang/Object.Object()VoidType\","
                + "{\"invokespecial\":\"1\"}]]}",
            cg.getGraph().toJSON().toString());
    }
}