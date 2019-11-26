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

import eu.fasten.core.data.FastenJavaURI;

import scala.collection.JavaConversions;

import java.io.File;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

class OPALMethodAnalyzerTest {

    PartialCallGraph singleSourceToTargetcallGraph, classInitCallGraph, lambdaCallGraph, arrayCallGraph;

    @BeforeEach
    void generateCallGraph() {

        /**
         * SingleSourceToTarget is a java8 compiled bytecode of:
         *<pre>
         * package name.space;
         *
         * public class SingleSourceToTarget{
         *
         *     public static void sourceMethod() { targetMethod(); }
         *
         *     public static void targetMethod() {}
         * }
         * </pre>
         * Including these edges:
         *  Resolved:[ public static void sourceMethod(),
         *             public static void targetMethod()]
         *  Unresolved:[ public void <init>() of current class,
         *               public void <init>() of Object class]
         */
        singleSourceToTargetcallGraph = CallGraphGenerator.generatePartialCallGraph(
                new File(Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class").getFile())
        );

        /**
         * ClassInit is a java8 compiled bytecode of:
         *<pre>
         * package name.space;
         *
         * public class ClassInit{
         *
         * public static void targetMethod(){}
         *     static{
         *         targetMethod();
         *     }
         * }
         * </pre>
         * Including these edges:
         *  Resolved:[ static void <clinit>(),
         *             public static void targetMethod()]
         *  Unresolved:[ public void <init>() of current class,
         *               public void <init>() of Object class]
         */
        classInitCallGraph = CallGraphGenerator.generatePartialCallGraph(
                new File(Thread.currentThread().getContextClassLoader().getResource("ClassInit.class").getFile())
        );

        /**
         * LambdaExample is a java8 compiled bytecode of:
         *<pre>
         * package name.space;
         *
         * import java.util.function.Function;
         *
         * public class LambdaExample {
         *     Function f = (it) -> "Hello";
         * }
         * </pre>
         * Including these edges:
         *  Resolved:   [
         *                  {public static ""/Lambda$79:0 $newInstance() of class ""/Lambda$79:0,
         *                   public void <init>() of class ""/Lambda$79:0}
         *
         *                  {public Object apply(Object) of class ""/Lambda$79:0,
         *                   private static Object lambda$new$0(Object) of current class}
         *
         *                  {public void <init>() of current class,
         *                   public static ""/Lambda$79:0 $newInstance() of class ""/Lambda$79:0}
         *              ]
         *
         *  Unresolved: [
         *                  {public void <init>() of current class,
         *                   public void <init>() of Object class}
         *
         *                  {public static void <init>() of class ""/Lambda$79:0
         *                   public void <init>() of Object class}
         *              ]
         */
        lambdaCallGraph = CallGraphGenerator.generatePartialCallGraph(
                new File(Thread.currentThread().getContextClassLoader().getResource("LambdaExample.class").getFile())
        );

        /**
         * SingleSourceToTarget is a java8 compiled bytecode of:
         *<pre>
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
         * </pre>
         * Including these edges:
         *  Resolved:[ public static void sourceMethod(),
         *             public static Object[] targetMethod(Object[])]
         *  Unresolved:[ public void <init>() of current class,
         *               public void <init>() of Object class]
         */
        arrayCallGraph = CallGraphGenerator.generatePartialCallGraph(
                new File(Thread.currentThread().getContextClassLoader().getResource("ArrayExample.class").getFile())
        );
    }

    @Test
    void testToFastenJavaURI() {

        assertEquals(
                new FastenJavaURI("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource())
        );

        assertEquals(
                new FastenJavaURI("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getTarget().get(0))
        );

        assertEquals(
                new FastenJavaURI("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).caller())
        );

        assertEquals(
                new FastenJavaURI("//SomeDependency/java.lang/Object.Object()Void"),
                //TODO change SomeDependency to $! when it's supported.
                OPALMethodAnalyzer.toCanonicalSchemelessURI(
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeClass(),
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeName(),
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeDescriptor()
                )
        );

        assertEquals(

                 //  [] is pctEncoded three times, It should stay encoded during creating  1- type's URI, 2- Method's URI and 3- Canonicalization.
                new FastenJavaURI("/name.space/ArrayExample.targetMethod(%2Fjava.lang%2FObject%25255B%25255D)%2Fjava.lang%2FObject%25255B%25255D"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(arrayCallGraph.getResolvedCalls().get(0).getTarget().get(0))
        );



        assertEquals(
                new FastenJavaURI("/name.space/ClassInit.%3Cinit%3E()%2Fjava.lang%2FVoid"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(classInitCallGraph.getResolvedCalls().get(0).getSource())
        );

    }

    @Test
    void testGetMethodName() {

        assertEquals("SingleSourceToTarget", OPALMethodAnalyzer.getMethodName(singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).caller()));

        //Three times pctEncoding! It should stay encoded during creating FastenJavaURI and Canonicalization otherwise it throws exception!
        assertEquals("%25253Cinit%25253E", OPALMethodAnalyzer.getMethodName(classInitCallGraph.getResolvedCalls().get(0).getSource()));

        assertEquals("sourceMethod", OPALMethodAnalyzer.getMethodName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()));

        assertEquals("targetMethod", OPALMethodAnalyzer.getMethodName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getTarget().get(0)));

        assertEquals("Object",
                OPALMethodAnalyzer.getMethodName(
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeClass(),
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeName()
                )
        );
    }

    @Test
    void testGetReturnTypeURI() {

        assertEquals(
                new FastenJavaURI("/java.lang/Void"),
                OPALMethodAnalyzer.getReturnTypeURI(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
                        .returnType())
        );

        assertEquals(
                new FastenJavaURI("/java.lang/Object%25255B%25255D"),
                OPALMethodAnalyzer.getReturnTypeURI(arrayCallGraph.getResolvedCalls().get(0).getTarget().get(0).returnType())
        );
    }

    @Test
    void testGetParametersURI() {

        assertArrayEquals(
                new FastenJavaURI[0],
                OPALMethodAnalyzer.getParametersURI(JavaConversions.seqAsJavaList(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().parameterTypes()))
        );

        assertArrayEquals(
                new FastenJavaURI[]{new FastenJavaURI("/java.lang/Object%25255B%25255D")},
                OPALMethodAnalyzer.getParametersURI(JavaConversions.seqAsJavaList(arrayCallGraph.getResolvedCalls().get(0).getTarget().get(0).parameterTypes()))
        );

    }

    @Test
    void testGetPackageName() {

        assertEquals(
                "java.lang",
                OPALMethodAnalyzer.getPackageName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().returnType())
        );

        assertEquals(
                "name.space",
                OPALMethodAnalyzer.getPackageName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().declaringClassFile().thisType())
        );
    }

    @Test
    void testGetClassName() {

        assertEquals(
                "Void",
                OPALMethodAnalyzer.getClassName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().returnType())
        );

        assertEquals(
                "SingleSourceToTarget",
                OPALMethodAnalyzer.getClassName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().declaringClassFile().thisType())
        );

        assertEquals(
                "Object",
                OPALMethodAnalyzer.getClassName(singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeClass())
        );

        assertEquals(
                "ClassInit",
                OPALMethodAnalyzer.getClassName(classInitCallGraph.getResolvedCalls().get(0).getSource().declaringClassFile().thisType())
        );

        assertEquals(
                "Object%25255B%25255D",
                OPALMethodAnalyzer.getClassName(arrayCallGraph.getResolvedCalls().get(0).getTarget().get(0).returnType())
        );

    }

}