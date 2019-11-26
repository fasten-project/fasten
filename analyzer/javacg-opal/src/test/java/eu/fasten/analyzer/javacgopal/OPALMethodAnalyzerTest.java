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

import static org.junit.Assert.assertEquals;

class OPALMethodAnalyzerTest {

    PartialCallGraph singleSourceToTargetcallGraph,classInitCallGraph,lambdaCallGraph,arrayCallGraph;

    @BeforeEach
    void generateCallGraph(){

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
                new FastenJavaURI("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2Fvoid"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource())
        );

        assertEquals(
                new FastenJavaURI("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2Fvoid"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getTarget().get(0))
        );

        assertEquals(
                new FastenJavaURI("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2Fvoid"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).caller())
        );

        assertEquals(
                new FastenJavaURI("//SomeDependency/java.lang/Object.Object()void"),
                OPALMethodAnalyzer.toCanonicalSchemelessURI(
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeClass(),
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeName(),
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeDescriptor()
                )
        );

//        assertEquals(
//                new FastenJavaURI("/name.space/ArrayExample.sourceMethod()%2Fjava.lang%2Fvoid"),
//                OPALMethodAnalyzer.toCanonicalSchemelessURI(arrayCallGraph.getResolvedCalls().get(0).getSource())
//        );
//
//        assertEquals(
//                new FastenJavaURI("/name.space/ArrayExample.targetMethod(%2Fjava.lang%2FObject)%2Fjava.lang%2FObject"),
//                OPALMethodAnalyzer.toCanonicalSchemelessURI(arrayCallGraph.getResolvedCalls().get(0).getTarget().get(0))
//        );

//        assertEquals(
//                new FastenJavaURI("/name.space/ClassInit.%3Cinit%3E()%2Fjava.lang%2Fvoid"),
//                OPALMethodAnalyzer.toFastenJavaURI(classInitCallGraph.getResolvedCalls().get(0).getSource())
//        );
//        TODO uncomment after canonicalization is supported for %3C and %3E.

    }

    @Test
    void testIsInit(){

        assertEquals("SingleSourceToTarget", OPALMethodAnalyzer.isInit(singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).caller()));

        assertEquals(
                "Object",
                OPALMethodAnalyzer.isInit(
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeClass(),
                        singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeName()
                )
        );

    }

    @Test
    void testGetPctReturnType() {

//        assertEquals(
//                "%2Fjava.lang%2Fvoid",
//                OPALMethodAnalyzer.getReturnType(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
//                .returnType())
//        );
    }

    @Test
    void testGetPctParameters() {

//        assertEquals(
//                "",
//                OPALMethodAnalyzer.getParameters(JavaConversions.seqAsJavaList(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
//                        .parameterTypes()))
//        );

    }

    @Test
    void testGetPackageName() {

//        assertEquals(
//                "/java.lang",
//                OPALMethodAnalyzer.getPackageName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
//                        .returnType())
//        );
    }

    @Test
    void testGetClassName() {

//        assertEquals(
//                "/void",
//                OPALMethodAnalyzer.getClassName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
//                        .returnType())
//        );
    }

}