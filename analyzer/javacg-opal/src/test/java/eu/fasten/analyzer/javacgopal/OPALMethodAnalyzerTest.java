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

    PartialCallGraph singleSourceToTargetcallGraph,classInitCallGraph;

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

    }

    @Test
     void testToFastenJavaURI() {


//        PartialCallGraph CallGraph = CallGraphGenerator.generatePartialCallGraph(
//                new File("/Users/mehdi/Desktop/Fasten/lapp/lapp/lapp/src/test/resources/example_jars/mehdi/name/space/ClassInit.class")
//        );

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

        assertEquals(
                "%2Fjava.lang%2Fvoid",
                OPALMethodAnalyzer.getPctReturnType(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
                .returnType())
        );
    }

    @Test
    void testGetPctParameters() {

        assertEquals(
                "",
                OPALMethodAnalyzer.getPctParameters(JavaConversions.seqAsJavaList(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
                        .parameterTypes()))
        );

    }

    @Test
    void testGetPackageName() {

        assertEquals(
                "/java.lang",
                OPALMethodAnalyzer.getPackageName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
                        .returnType())
        );
    }

    @Test
    void testGetClassName() {

        assertEquals(
                "/void",
                OPALMethodAnalyzer.getClassName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
                        .returnType())
        );
    }

}