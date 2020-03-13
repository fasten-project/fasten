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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.AnalysisContext;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MethodTest {

    private static CallGraph ssttgraph, cigraph, lambdagraph, arraygraph, aegraph;

    @BeforeAll
    public static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
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

        /**
         * Contains arrays of all primitive types including two arrays of types Integer and Object.
         */
        var aepath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("ArrayExtensiveTest.jar")
                .getFile()).getAbsolutePath();

        aegraph = CallGraphConstructor.generateCallGraph(aepath);
    }

    @Test
    public void toCanonicalJSONSingleSourceToTargetTest() {

        var wrapped = WalaResultAnalyzer.wrap(ssttgraph);

        assertEquals(1, wrapped.getInternalCalls().size());
        assertEquals(1, wrapped.getExternalCalls().size());

        // Actual URIs
        var actualSourceURI = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoidType";
        var actualTargetURI = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType";
        var actualSourceUnresolvedURI =
                "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType";
        var actualTargetUnresolvedURI = "///java.lang/Object.Object()VoidType";

        var callMetadata = wrapped.getExternalCalls().values().iterator().next();
        var callValues = wrapped.getExternalCalls().keySet().iterator().next();

        var type = wrapped.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/SingleSourceToTarget"));


        assertEquals(actualSourceUnresolvedURI, type.getMethods().get(callValues.getKey()).toString());
        assertEquals(actualTargetUnresolvedURI, callValues.getValue().toString());
        assertEquals("invokespecial", callMetadata.keySet().iterator().next());
        assertEquals("1", callMetadata.values().iterator().next());

        var resolvedCall = wrapped.getInternalCalls().get(0);

        assertEquals(actualSourceURI, type.getMethods().get(resolvedCall.get(0)).toString());
        assertEquals(actualTargetURI, type.getMethods().get(resolvedCall.get(1)).toString());
    }

    @Test
    public void toCanonicalJSONClassInitTest() {

        var wrapped = WalaResultAnalyzer.wrap(cigraph);

        assertEquals(1, wrapped.getInternalCalls().size());

        // Actual URIs
        var actualSourceURI = "/name.space/ClassInit.%3Cinit%3E()%2Fjava.lang%2FVoidType";
        var actualTargetURI = "/name.space/ClassInit.targetMethod()%2Fjava.lang%2FVoidType";

        var type = wrapped.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/ClassInit"));

        var resolvedCall = wrapped.getInternalCalls().get(0);

        assertEquals(actualSourceURI, type.getMethods().get(resolvedCall.get(0)).toString());
        assertEquals(actualTargetURI, type.getMethods().get(resolvedCall.get(1)).toString());
    }

    @Test
    public void toCanonicalJSONLambdaTest() {

        var wrapped = WalaResultAnalyzer.wrap(lambdagraph);

        assertEquals(2, wrapped.getExternalCalls().size());

        Pair<Integer, FastenURI> call = null;
        Map<String, String> callMetadata = null;

        for (var entry : wrapped.getExternalCalls().entrySet()) {
            if (entry.getKey().getValue().toString().contains("invoke")) {
                call = entry.getKey();
                callMetadata = entry.getValue();
            }
        }

        // Actual URIs
        var actualSourceURI = "/name.space/LambdaExample.LambdaExample()%2Fjava.lang%2FVoidType";
        var actualTargetURI = "///java.lang.invoke/LambdaMetafactory.apply()%2Fjava"
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

        assertEquals(1, wrapped.getInternalCalls().size());

        // Actual URIs
        var actualSourceURI = "/name.space/ArrayExample.sourceMethod()%2Fjava.lang%2FVoidType";
        var actualTargetURI = "/name.space/ArrayExample.targetMethod(%2Fjava"
                + ".lang%2FObject%25255B%25255D)%2Fjava.lang%2FObject%25255B%25255D";

        var type = wrapped.getClassHierarchy()
                .get(new FastenJavaURI("/name.space/ArrayExample"));

        var resolvedCall = wrapped.getInternalCalls().get(0);

        assertEquals(actualSourceURI, type.getMethods().get(resolvedCall.get(0)).toString());
        assertEquals(actualTargetURI, type.getMethods().get(resolvedCall.get(1)).toString());
    }

    @Test
    public void toCanonicalJSONArrayExtensiveTest() {
        var wrapped = WalaResultAnalyzer.wrap(aegraph);

        List<String> listOfMethodNames = new ArrayList<>();
        listOfMethodNames.add("short");
        listOfMethodNames.add("integer");
        listOfMethodNames.add("int");
        listOfMethodNames.add("object");
        listOfMethodNames.add("bool");
        listOfMethodNames.add("long");
        listOfMethodNames.add("double");
        listOfMethodNames.add("float");
        listOfMethodNames.add("char");
        listOfMethodNames.add("byte");

        List<String> listOfMethodTypes = new ArrayList<>();
        listOfMethodTypes.add("ShortType");
        listOfMethodTypes.add("Integer");
        listOfMethodTypes.add("IntegerType");
        listOfMethodTypes.add("Object");
        listOfMethodTypes.add("BooleanType");
        listOfMethodTypes.add("LongType");
        listOfMethodTypes.add("DoubleType");
        listOfMethodTypes.add("FloatType");
        listOfMethodTypes.add("CharacterType");
        listOfMethodTypes.add("ByteType");


        var methods = wrapped.getClassHierarchy().values().iterator().next().getMethods();
        for (int pos = 0; pos < listOfMethodNames.size(); pos++) {
            var method = methods.get(pos + 1).toString();

            if (method.contains(listOfMethodNames.get(pos))) {
                assertTrue(method.contains(
                        "%2Fjava.lang%2F" + listOfMethodTypes.get(pos) + "%25255B%25255D"));
            }
        }
    }

    @Test
    public void equalsTest() {
        final var analysisContext = new AnalysisContext(ssttgraph.getClassHierarchy());

        List<Method> methods = new ArrayList<>();

        for (final CGNode node : ssttgraph) {
            final var nodeReference = node.getMethod().getReference();
            methods.add(analysisContext.findOrCreate(nodeReference));
        }


        final var refMethod = methods.get(3);
        final var methodSameNamespaceDiffSymbol = methods.get(5);
        final var methodDiffNamespaceDiffSymbol = methods.get(0);
        final var methodDiffNamespaceSameSymbol = methods.get(7);
        final var methodSameReference = new InternalMethod(refMethod.getReference(), null);


        assertEquals(refMethod, refMethod);
        assertEquals(refMethod, methodSameReference);
        assertNotEquals(refMethod, null);
        assertNotEquals(refMethod, new Object());
        assertNotEquals(refMethod, methodSameNamespaceDiffSymbol);
        assertNotEquals(refMethod, methodDiffNamespaceDiffSymbol);
        assertNotEquals(refMethod, methodDiffNamespaceSameSymbol);
    }
}