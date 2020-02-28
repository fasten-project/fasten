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

package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.analyzer.javacgopal.data.callgraph.PartialCallGraph;
import eu.fasten.core.data.FastenJavaURI;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;

import scala.collection.JavaConversions;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MethodTest {

    static PartialCallGraph singleSourceToTargetcallGraph, classInitCallGraph, lambdaCallGraph, arrayCallGraph;
    static String lambdaNumber;

    @BeforeClass
    public static void generateCallGraph() {

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
        singleSourceToTargetcallGraph = new PartialCallGraph(
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
        classInitCallGraph = new PartialCallGraph(
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
         *                  {public Object apply(Object) of class ""/Lambda$79:0,
         *                   private static Object lambda$new$0(Object) of current class}
         *
         *                  {public static ""/Lambda$79:0 $newInstance() of class ""/Lambda$79:0,
         *                   public void <init>() of class ""/Lambda$79:0}
         *
         *                  {public void <init>() of current class,
         *                   public static ""/Lambda$79:0 $newInstance() of class ""/Lambda$79:0}
         *              ]
         *
         *  Unresolved: [
         *                  {public void <init>() of current class,
         *                   public void <init>() of Object class}
         *
         *                  {public void <init>() of class ""/Lambda$79:0
         *                   public void <init>() of Object class}
         *              ]
         */
        lambdaCallGraph = new PartialCallGraph(
                new File(Thread.currentThread().getContextClassLoader().getResource("LambdaExample.class").getFile())
        );
//        var lambdaFullName = lambdaCallGraph.getResolvedCalls().stream().filter(i -> i.getSource().toString().contains("apply")).findFirst().get().getSource().declaringClassFile().thisType().fqn();
//        lambdaNumber = lambdaFullName.split("[$]")[1].split(":")[0];

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
        arrayCallGraph = new PartialCallGraph(
                new File(Thread.currentThread().getContextClassLoader().getResource("ArrayExample.class").getFile())
        );
    }
//
//    @Test
//    public void testToCanonicalSchemelessURI() {
//
//        var method = singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource();
//        assertEquals(
//                new FastenJavaURI("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid"),
//                OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//        method = singleSourceToTargetcallGraph.getResolvedCalls().get(0).getTarget();
//        assertEquals(
//                new FastenJavaURI("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid"),
//                OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//        method = singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).caller();
//        assertEquals(
//                new FastenJavaURI("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid"),
//                OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//        var unresolvedMethod = singleSourceToTargetcallGraph.getUnresolvedCalls().get(0);
//        assertEquals(
//                new FastenJavaURI("/java.lang/Object.Object()Void"),
//                OPALMethod.toCanonicalSchemelessURI(null, unresolvedMethod.calleeClass(), unresolvedMethod.calleeName(), unresolvedMethod.calleeDescriptor())
//        );
//
//        method = arrayCallGraph.getResolvedCalls().get(0).getTarget();
//        assertEquals(
//                //  [] is pctEncoded three times, It should stay encoded during creating  1- type's URI, 2- OPALMethod's URI and 3- Canonicalization.
//                new FastenJavaURI("/name.space/ArrayExample.targetMethod(%2Fjava.lang%2FObject%25255B%25255D)%2Fjava.lang%2FObject%25255B%25255D"),
//                OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//        method = classInitCallGraph.getResolvedCalls().get(0).getSource();
//        assertEquals(
//                new FastenJavaURI("/name.space/ClassInit.%3Cinit%3E()%2Fjava.lang%2FVoid"),
//                OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//        method = lambdaCallGraph.getResolvedCalls().stream().filter(i -> i.getSource().toString().contains("apply")).findFirst().get().getSource();
//        assertEquals(
//                new FastenJavaURI("/null/Lambda$"+lambdaNumber+"%3A0.apply(%2Fjava.lang%2FObject)%2Fjava.lang%2FObject"),
//                OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//        method = lambdaCallGraph.getResolvedCalls().stream().filter(i -> i.getSource().toString().contains("apply")).findFirst().get().getTarget();
//        assertEquals(
//                new FastenJavaURI("/name.space/LambdaExample.lambda$new$0(%2Fjava.lang%2FObject)%2Fjava.lang%2FObject"),
//                OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//        method = lambdaCallGraph.getResolvedCalls().stream().filter(i -> i.getSource().toString().contains("$newInstance")).findFirst().get().getSource();
//        assertEquals(
//                new FastenJavaURI("/null/Lambda$"+lambdaNumber+"%3A0.$newInstance()Lambda$"+lambdaNumber+"%25253A0"),
//                OPALMethod.toCanonicalSchemelessURI( null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//        method = lambdaCallGraph.getUnresolvedCalls().stream().filter(i -> i.caller().declaringClassFile().thisType().packageName().equals("")).findFirst().get().caller();
//        assertEquals(
//                new FastenJavaURI("/null/Lambda$"+lambdaNumber+"%3A0.Lambda$"+lambdaNumber+"%3A0()%2Fjava.lang%2FVoid"),
//                OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(), method.name(), method.descriptor())
//        );
//
//    }
//
//    @Test
//    public void testGetMethodName() {
//
//        var method = singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).caller();
//        assertEquals("SingleSourceToTarget", OPALMethod.getMethodName(method.declaringClassFile().thisType().simpleName(), method.name()));
//
//        method = classInitCallGraph.getResolvedCalls().get(0).getSource();
//        //Three times pctEncoding! It should stay encoded during creating FastenJavaURI and Canonicalization otherwise it throws exception!
//        assertEquals("%25253Cinit%25253E", OPALMethod.getMethodName(method.declaringClassFile().thisType().simpleName(), method.name()));
//
//        method = singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource();
//        assertEquals("sourceMethod", OPALMethod.getMethodName(method.declaringClassFile().thisType().simpleName(), method.name()));
//
//        method = singleSourceToTargetcallGraph.getResolvedCalls().get(0).getTarget();
//        assertEquals("targetMethod", OPALMethod.getMethodName(method.declaringClassFile().thisType().simpleName(), method.name()));
//
//        var unresolvedMethod = singleSourceToTargetcallGraph.getUnresolvedCalls().get(0);
//        assertEquals("Object", OPALMethod.getMethodName(unresolvedMethod.calleeClass().asObjectType().simpleName(), unresolvedMethod.calleeName()));
//    }
//
//    @Test
//    public void testGetReturnTypeURI() {
//
//        assertEquals(
//                new FastenJavaURI("/java.lang/Void"),
//                OPALMethod.getTypeURI(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource()
//                        .returnType())
//        );
//
//        assertEquals(
//                new FastenJavaURI("/java.lang/Object%25255B%25255D"),
//                OPALMethod.getTypeURI(arrayCallGraph.getResolvedCalls().get(0).getTarget().returnType())
//        );
//    }
//
//    @Test
//    public void testGetParametersURI() {
//
//        assertArrayEquals(
//                new FastenJavaURI[0],
//                OPALMethod.getParametersURI(JavaConversions.seqAsJavaList(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().parameterTypes()))
//        );
//
//        assertArrayEquals(
//                new FastenJavaURI[]{new FastenJavaURI("/java.lang/Object%25255B%25255D")},
//                OPALMethod.getParametersURI(JavaConversions.seqAsJavaList(arrayCallGraph.getResolvedCalls().get(0).getTarget().parameterTypes()))
//        );
//
//    }
//
//    @Test
//    public void testGetPackageName() {
//
//        assertEquals(
//                "java.lang",
//                OPALMethod.getPackageName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().returnType())
//        );
//
//        assertEquals(
//                "name.space",
//                OPALMethod.getPackageName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().declaringClassFile().thisType())
//        );
//    }
//
//    @Test
//    public void testGetClassName() {
//
//        assertEquals(
//                "Void",
//                OPALMethod.getClassName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().returnType())
//        );
//
//        assertEquals(
//                "SingleSourceToTarget",
//                OPALMethod.getClassName(singleSourceToTargetcallGraph.getResolvedCalls().get(0).getSource().declaringClassFile().thisType())
//        );
//
//        assertEquals(
//                "Object",
//                OPALMethod.getClassName(singleSourceToTargetcallGraph.getUnresolvedCalls().get(0).calleeClass())
//        );
//
//        assertEquals(
//                "ClassInit",
//                OPALMethod.getClassName(classInitCallGraph.getResolvedCalls().get(0).getSource().declaringClassFile().thisType())
//        );
//
//        assertEquals(
//                "Object%25255B%25255D",
//                OPALMethod.getClassName(arrayCallGraph.getResolvedCalls().get(0).getTarget().returnType())
//        );
//
//    }
//
}