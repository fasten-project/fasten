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

package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Objects;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExtendedRevisionJavaCallGraphTest {

    private static ExtendedRevisionJavaCallGraph graph;

    @BeforeAll
    static void setUp() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testRCG.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        graph = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));
    }

    @Test
    void getNodeCount() {
        assertEquals(5, graph.getNodeCount());
    }

    @Test
    void toJSON() throws FileNotFoundException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testRCG.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        JSONObject jsonGraph = new JSONObject(tokener);

        assertEquals(jsonGraph.getJSONObject("cha").getJSONObject("externalTypes").toString(),
                graph.toJSON().getJSONObject("cha").getJSONObject("externalTypes").toString());

        assertEquals(new HashSet<>(jsonGraph.getJSONArray("call-sites").toList()),
                new HashSet<>(graph.toJSON().getJSONArray("call-sites").toList()));

        assertEquals(jsonGraph.getJSONObject("cha").getJSONObject("internalTypes")
                        .getJSONObject("/internal.package/B")
                        .getJSONObject("methods").getJSONObject("3").toString(),
                graph.toJSON().getJSONObject("cha").getJSONObject("internalTypes")
                        .getJSONObject("/internal.package/B")
                        .getJSONObject("methods").getJSONObject("3").toString());

        assertEquals(jsonGraph.getJSONObject("cha").getJSONObject("internalTypes")
                        .getJSONObject("/internal.package/B")
                        .getJSONObject("methods").getJSONObject("4").toString(),
                graph.toJSON().getJSONObject("cha").getJSONObject("internalTypes")
                        .getJSONObject("/internal.package/B")
                        .getJSONObject("methods").getJSONObject("4").toString());
    }

    @Test
    void toJSONFromCHA() {
        assertEquals(graph.classHierarchyToJSON(graph.getClassHierarchy()).toString(),
                graph.toJSON().getJSONObject("cha").toString());
    }

    @Test
    void mapOfAllMethods() {
        var methodsMap = graph.mapOfAllMethods();

        assertEquals(6, methodsMap.size());

        assertEquals("/external.package/A.someMethod()%2Fjava.lang%2FObject",
                methodsMap.get(1).getUri().toString());
        assertEquals("/external.package/A.otherMethod()%2Fjava.lang%2FVoidType",
                methodsMap.get(2).getUri().toString());
        assertEquals("/internal.package/B.internalMethod(%2Fjava.lang%2FClass)%2Fjava.lang%2FVoidType",
                methodsMap.get(3).getUri().toString());
        assertEquals("/internal.package/B.newInstance()%2Fjava.lang%2FObject",
                methodsMap.get(4).getUri().toString());
    }

    @Test
    void isCallGraphEmptyEmptyInternal() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testRCGEmptyInternal.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyExternal() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testRCGEmptyExternal.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyResolved() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testRCGEmptyResolved.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyAll() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testRCGEmptyAll.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));

        assertTrue(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyAllExist() {
        assertFalse(graph.isCallGraphEmpty());
    }

    @Test
    void getCgGenerator() {
        assertEquals("OPAL", graph.getCgGenerator());
    }

    @Test
    void mapOfFullURIStrings() throws FileNotFoundException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/merged_cg_test.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));
        var cg = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));
        var mapURI = cg.mapOfFullURIStrings();

        // Internal types
        assertEquals("fasten://mvn!Importer$1/merge.staticInitializer/Importer.%3Cinit%3E()%2Fjava" +
                ".lang%2FVoidType",
                     mapURI.get(0));
        assertEquals("fasten://mvn!Importer$1/merge.staticInitializer/Importer.sourceMethod()%2Fjava" +
                ".lang%2FVoidType",
                mapURI.get(1));

        // Resolved types
        assertEquals("fasten://mvn!Imported$0/merge.staticInitializer/Imported.%3Cinit%3E()%2Fjava" +
                ".lang%2FVoidType",
                mapURI.get(4));
        assertEquals("fasten://mvn!Imported$0/merge.staticInitializer/Imported.%3Cclinit%3E()%2Fjava.lang%2FVoidType",
                mapURI.get(5));

    }
    
}
