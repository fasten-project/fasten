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

package eu.fasten.analyzer.javacgwala.data.core;

import com.ibm.wala.ipa.callgraph.CallGraph;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

public class MethodTest {

    private static CallGraph ssttgraph, cigraph, lambdagraph, arraygraph;

    @BeforeAll
    public static void setUp() {
        /**
         * SingleSourceToTarget:
         *
         * package name.space;
         *
         * public class SingleSourceToTarget{
         *
         *     public static void sourceMethod() { targetMethod(); }
         *
         *     public static void targetMethod() {}
         * }
         */
        var ssttpath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        ssttgraph = CallGraphConstructor.generateCallGraph(ssttpath);

        /**
         * ClassInit:
         *
         * package name.space;
         *
         * public class ClassInit{
         *
         * public static void targetMethod(){}
         *
         *     static{
         *         targetMethod();
         *     }
         * }
         *
         */
        var cipath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("ClassInit.jar")
                .getFile()).getAbsolutePath();

        cigraph = CallGraphConstructor.generateCallGraph(cipath);

        /**
         * LambdaExample:
         *
         * package name.space;
         *
         * import java.util.function.Function;
         *
         * public class LambdaExample {
         *     Function f = (it) -> "Hello";
         * }
         */
        var lambdapath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("LambdaExample.jar")
                .getFile()).getAbsolutePath();

        lambdagraph = CallGraphConstructor.generateCallGraph(lambdapath);

        /**
         * ArrayExample:
         *
         *package name.space;
         *
         * public class ArrayExample{
         *     public static void sourceMethod() {
         *         Object[] object = new Object[1];
         *         targetMethod(object);
         *     }
         *
         *     public static Object[] targetMethod(Object[] obj) {
         *         return obj;
         *     }
         * }
         */
        var arraypath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("ArrayExample.jar")
                .getFile()).getAbsolutePath();

        arraygraph = CallGraphConstructor.generateCallGraph(arraypath);
    }

    @Test
    public void toCanonicalJSONSingleSourceToTargetTest() {

        var wrapped = WalaResultAnalyzer.wrap(ssttgraph);

        assertEquals(1, wrapped.getResolvedCalls().size());
        assertEquals(1, wrapped.getUnresolvedCalls().size());

        // Actual URIs
        var actualSourceURI = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid";
        var actualSourceUnresolvedURI =
                "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid";
        var actualTargetUnresolvedURI = "/java.lang/Object.Object()Void";

        var callMetadata = wrapped.getUnresolvedCalls().values().iterator().next();
        var callValues = wrapped.getUnresolvedCalls().keySet().iterator().next();

        var type = wrapped.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/SingleSourceToTarget"));


        assertEquals(actualSourceUnresolvedURI, type.getMethods().get(callValues.getKey()).toString());
        assertEquals(actualTargetUnresolvedURI, callValues.getValue().toString());
        assertEquals("invokespecial", callMetadata.keySet().iterator().next());
        assertEquals("1", callMetadata.values().iterator().next());

        var resolvedCall = wrapped.getResolvedCalls().get(0);

        assertEquals(actualSourceURI, type.getMethods().get(resolvedCall[0]).toString());
        assertEquals(actualTargetURI, type.getMethods().get(resolvedCall[1]).toString());
    }

    @Test
    public void toCanonicalJSONClassInitTest() {

        var wrapped = WalaResultAnalyzer.wrap(cigraph);

        assertEquals(1, wrapped.getResolvedCalls().size());

        // Actual URIs
        var actualSourceURI = "/name.space/ClassInit.%3Cinit%3E()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/ClassInit.targetMethod()%2Fjava.lang%2FVoid";

        var type = wrapped.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/ClassInit"));

        var resolvedCall = wrapped.getResolvedCalls().get(0);

        assertEquals(actualSourceURI, type.getMethods().get(resolvedCall[0]).toString());
        assertEquals(actualTargetURI, type.getMethods().get(resolvedCall[1]).toString());
    }

    @Test
    public void toCanonicalJSONLambdaTest() {

        var wrapped = WalaResultAnalyzer.wrap(lambdagraph);

        assertEquals(2, wrapped.getUnresolvedCalls().size());

        Pair<Integer, FastenURI> call = null;
        Map<String, String> callMetadata = null;

        for (var entry : wrapped.getUnresolvedCalls().entrySet()) {
            if (entry.getKey().getValue().toString().contains("invoke")) {
                call = entry.getKey();
                callMetadata = entry.getValue();
            }
        }

        // Actual URIs
        var actualSourceURI = "/name.space/LambdaExample.LambdaExample()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/java.lang.invoke/LambdaMetafactory.apply()%2Fjava"
                + ".util.function%2FFunction";

        assertNotNull(call);
        assertNotNull(callMetadata);

        var type = wrapped.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/LambdaExample"));

        assertEquals(actualSourceURI, type.getMethods().get(call.getKey()).toString());
        assertEquals(actualTargetURI, call.getValue().toString());
        assertEquals("invokestatic", callMetadata.keySet().iterator().next());
        assertEquals("1", callMetadata.get("invokestatic"));
    }

    @Test
    public void toCanonicalJSONArrayTest() {

        var wrapped = WalaResultAnalyzer.wrap(arraygraph);

        assertEquals(1, wrapped.getResolvedCalls().size());

        // Actual URIs
        var actualSourceURI = "/name.space/ArrayExample.sourceMethod()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/ArrayExample.targetMethod(%2Fjava"
                + ".lang%2FObject%25255B%25255D)%2Fjava.lang%2FObject%25255B%25255D";

        var type = wrapped.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/ArrayExample"));

        var resolvedCall = wrapped.getResolvedCalls().get(0);

        assertEquals(actualSourceURI, type.getMethods().get(resolvedCall[0]).toString());
        assertEquals(actualTargetURI, type.getMethods().get(resolvedCall[1]).toString());
    }
}