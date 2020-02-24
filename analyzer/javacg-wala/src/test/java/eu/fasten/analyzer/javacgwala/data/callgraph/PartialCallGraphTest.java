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

import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.analyzer.javacgwala.data.core.Call;
import eu.fasten.analyzer.javacgwala.data.core.ResolvedMethod;
import eu.fasten.analyzer.javacgwala.data.core.UnresolvedMethod;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartialCallGraphTest {

    private static PartialCallGraph graph;
    private static Call call;


    @BeforeAll
    static void setUp() {
        var path = Paths.get(new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath());

        graph = WalaResultAnalyzer.wrap(CallGraphConstructor.generateCallGraph(path.toString()), null);
//
//        var source = new ResolvedMethod("name.space",
//                Selector.make("<init>()V"), null);
//        var target = new ResolvedMethod("name.space",
//                Selector.make("<init>()V"), null);
//        call = new Call(source, target, Call.CallType.STATIC);
//
//
//        var initialSource = new ResolvedMethod("name.space.SingleSourceToTarget",
//                Selector.make("sourceMethod()V"), null);
//        var initialTarget = new ResolvedMethod("name.space.SingleSourceToTarget",
//                Selector.make("targetMethod()V"), null);
//
//        var initialUnresolvedSource =
//                new ResolvedMethod("name.space.SingleSourceToTarget",
//                        Selector.make("<init>()V"), null);
//        var initialUnresolvedTarget =
//                new UnresolvedMethod("java.lang.Object",
//                        Selector.make("<init>()V"));

        MavenCoordinate coordinate =
                new MavenCoordinate("group", "artifact", "1.0");

        //graph = new PartialCallGraph(coordinate);

        //graph.addUnresolvedCall(new Call(initialUnresolvedSource, initialUnresolvedTarget,
                //Call.CallType.SPECIAL));
        //graph.addResolvedCall(new Call(initialSource, initialTarget, Call.CallType.STATIC));
    }

    @Test
    void getUnresolvedCalls() {
        var source = "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid";
        var target = "/java.lang/Object.Object()Void";
        var call = graph.getUnresolvedCalls().get(0);

        assertEquals(source, call.getSource().toCanonicalSchemalessURI().toString());
        assertEquals(target, call.getTarget().toCanonicalSchemalessURI().toString());
    }

    @Test
    void getResolvedCalls() {
        var source = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid";
        var target = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid";
        var call = graph.getResolvedCalls().get(0);

        assertEquals(source, call.getSource().toCanonicalSchemalessURI().toString());
        assertEquals(target, call.getTarget().toCanonicalSchemalessURI().toString());
    }

    @Test
    void addResolvedCall() {
        assertEquals(1, graph.getResolvedCalls().size());

        graph.addResolvedCall(call);

        assertEquals(2, graph.getResolvedCalls().size());
        assertEquals(call, graph.getResolvedCalls().get(1));

        graph.addResolvedCall(call);
        assertEquals(2, graph.getResolvedCalls().size());
    }

    @Test
    void addUnresolvedCall() {
        assertEquals(1, graph.getUnresolvedCalls().size());

        graph.addUnresolvedCall(call);

        assertEquals(2, graph.getUnresolvedCalls().size());
        assertEquals(call, graph.getUnresolvedCalls().get(1));

        graph.addUnresolvedCall(call);
        assertEquals(2, graph.getUnresolvedCalls().size());
    }

//    @Test
//    void toRevisionCallGraph() {
//        var rcg = graph.toRevisionCallGraph(-1);
//
//        assertEquals(-1, rcg.timestamp);
//        assertEquals("group.artifact", rcg.product);
//        assertEquals("mvn", rcg.forge);
//        assertEquals("1.0", rcg.version);
//
//        assertEquals("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid",
//                rcg.graph.get(0)[0].toString());
//        assertEquals("///name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid",
//                rcg.graph.get(0)[1].toString());
//
//        assertEquals(0, rcg.depset.size());
//    }
}