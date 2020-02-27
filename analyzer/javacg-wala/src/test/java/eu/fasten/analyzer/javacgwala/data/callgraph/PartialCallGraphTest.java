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

import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.analyzer.javacgwala.data.core.CallType;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.io.File;
import java.nio.file.Paths;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartialCallGraphTest {

    private static PartialCallGraph graph;
    private static ExtendedRevisionCallGraph.Type type;


    @BeforeAll
    static void setUp() {
        var path = Paths.get(new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath());

        graph = WalaResultAnalyzer.wrap(CallGraphConstructor.generateCallGraph(path.toString()));

        type = graph.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/SingleSourceToTarget"));
    }

    @Test
    void getUnresolvedCalls() {
        var source = "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid";
        var target = "/java.lang/Object.Object()Void";

        var call = graph.getGraph().getUnresolvedCalls().keySet().iterator().next();

        assertEquals(source, type.getMethods().get(call.getKey()).toString());
        assertEquals(target, call.getValue().toString());
    }

    @Test
    void getResolvedCalls() {
        var source = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid";
        var target = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid";
        var call = graph.getGraph().getResolvedCalls().get(0);

        assertEquals(source, type.getMethods().get(call[0]).toString());
        assertEquals(target, type.getMethods().get(call[1]).toString());
    }

    @Test
    void addResolvedCall() {
        assertEquals(1, graph.getResolvedCalls().size());

        graph.addResolvedCall(100, 200);

        assertEquals(2, graph.getResolvedCalls().size());
        assertEquals(100, graph.getResolvedCalls().get(1)[0]);
        assertEquals(200, graph.getResolvedCalls().get(1)[1]);

        graph.addResolvedCall(100, 200);
        assertEquals(2, graph.getResolvedCalls().size());
    }

    @Test
    void addUnresolvedCall() {
        assertEquals(1, graph.getUnresolvedCalls().size());

        graph.addUnresolvedCall(300, new FastenJavaURI("/name.space/Class"), CallType.STATIC);

        Pair<Integer, FastenURI> selectKey = new MutablePair<>(300, new FastenJavaURI("/name.space/Class"));

        assertEquals(2, graph.getUnresolvedCalls().size());
        assertEquals(1, Integer.parseInt(
                graph.getUnresolvedCalls().get(selectKey).get("invokestatic")));

        graph.addUnresolvedCall(300, new FastenJavaURI("/name.space/Class"), CallType.STATIC);
        assertEquals(2, graph.getUnresolvedCalls().size());
        assertEquals(2,
                Integer.parseInt(graph.getUnresolvedCalls().get(selectKey).get("invokestatic")));
    }
}