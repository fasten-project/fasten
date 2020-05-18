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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.analyzer.javacgwala.data.core.CallType;
import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Disabled until a way to make Wala platform independent found")
class PartialCallGraphTest {

    private static PartialCallGraph graph;
    private static RevisionCallGraph.Type type;


    @BeforeAll
    static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        var path = Paths.get(new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath());

        graph = WalaResultAnalyzer.wrap(CallGraphConstructor.generateCallGraph(path.toString()));

        type = graph.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/SingleSourceToTarget"));
    }

    @Test
    void getUnresolvedCalls() {
        var source = "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType";
        var target = "///java.lang/Object.Object()VoidType";

        var call = graph.getGraph().getExternalCalls().keySet().iterator().next();

        assertEquals(source, type.getMethods().get(call.getKey()).toString());
        assertEquals(target, call.getValue().toString());
    }

    @Test
    void getResolvedCalls() {
        var source = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoidType";
        var target = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType";
        var call = graph.getGraph().getInternalCalls().get(0);

        assertEquals(source, type.getMethods().get(call.get(0)).toString());
        assertEquals(target, type.getMethods().get(call.get(1)).toString());
    }

    @Test
    void addResolvedCall() {
        assertEquals(1, graph.getInternalCalls().size());

        graph.addInternalCall(100, 200);

        assertEquals(2, graph.getInternalCalls().size());
        assertEquals(100, graph.getInternalCalls().get(1).get(0));
        assertEquals(200, graph.getInternalCalls().get(1).get(1));

        graph.addInternalCall(100, 200);
        assertEquals(2, graph.getInternalCalls().size());
    }

    @Test
    void addUnresolvedCall() {
        assertEquals(1, graph.getExternalCalls().size());

        graph.addExternalCall(300, new FastenJavaURI("/name.space/Class"), CallType.STATIC);

        Pair<Integer, FastenURI> selectKey = new MutablePair<>(300, new FastenJavaURI("/name.space/Class"));

        assertEquals(2, graph.getExternalCalls().size());
        assertEquals(1, Integer.parseInt(
                graph.getExternalCalls().get(selectKey).get("invokestatic")));

        graph.addExternalCall(300, new FastenJavaURI("/name.space/Class"), CallType.STATIC);
        assertEquals(2, graph.getExternalCalls().size());
        assertEquals(2,
                Integer.parseInt(graph.getExternalCalls().get(selectKey).get("invokestatic")));
    }
}