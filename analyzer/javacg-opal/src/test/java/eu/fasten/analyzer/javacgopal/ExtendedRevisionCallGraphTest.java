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

package eu.fasten.analyzer.javacgopal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.fasten.core.data.ExtendedRevisionCallGraph;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExtendedRevisionCallGraphTest {

    private static ExtendedRevisionCallGraph graph;

    @BeforeAll
    static void setUp() throws IOException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("revision-call-graph/testRCG.json"))
                .getFile());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        graph = new ExtendedRevisionCallGraph(new JSONObject(tokener));
    }

    @Test
    void getNodeCount() {
        assertEquals(5, graph.getNodeCount());
    }

    @Test
    void toJSON() throws FileNotFoundException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("revision-call-graph/testRCG.json"))
                .getFile());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        JSONObject jsonGraph = new JSONObject(tokener);

        assertEquals(jsonGraph.getJSONObject("cha").getJSONObject("externalTypes").toString(),
                graph.toJSON().getJSONObject("cha").getJSONObject("externalTypes").toString());

        assertEquals(jsonGraph.getJSONObject("graph").toMap(),
                graph.toJSON().getJSONObject("graph").toMap());

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
    void isCallGraphEmptyEmptyInternal() throws IOException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("revision-call-graph/testRCGEmptyInternal.json"))
                .getFile());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new ExtendedRevisionCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyExternal() throws IOException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("revision-call-graph/testRCGEmptyExternal.json"))
                .getFile());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new ExtendedRevisionCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyResolved() throws IOException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("revision-call-graph/testRCGEmptyResolved.json"))
                .getFile());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new ExtendedRevisionCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyAll() throws IOException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("revision-call-graph/testRCGEmptyAll.json"))
                .getFile());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new ExtendedRevisionCallGraph(new JSONObject(tokener));

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
}