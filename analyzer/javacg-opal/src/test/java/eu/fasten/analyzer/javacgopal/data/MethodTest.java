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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import eu.fasten.core.data.FastenJavaURI;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opalj.ai.analyses.cg.ComputedCallGraph;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import scala.collection.JavaConverters;

public class MethodTest {

    static ComputedCallGraph oneEdgeCG;
    static ComputedCallGraph classInitCG;
    static ComputedCallGraph arrayCG;
    static ComputedCallGraph lambdaCG;
    static ComputedCallGraph typesCG;
    static Map<ObjectType, OPALType> oneEdgeCHA;
    static Map<ObjectType, OPALType> classInitCHA;
    static Map<ObjectType, OPALType> arrayCHA;
    static Map<ObjectType, OPALType> lambdaCHA;
    static Map<ObjectType, OPALType> typesCHA;
    static List<Method> oneEdgeMethods;
    static List<Method> classInitMethods;
    static List<Method> arrayMethods;
    static List<Method> lambdaMethods;
    static List<Method> typesMethods;
    static String lambdaNumber;

    /**
     * Creates all the mock objects needed for this class.
     */
    @BeforeClass
    public static void generateCallGraph() {

        /*
         * package resources;
         *
         * public class PremetivesWrappers{
         *     public static void allTypes(byte b, char c, short s, int i, long l, float f, double d,
         *                                 boolean bool, Byte b1, Character c1, Short s1, Integer i1, Long l1,
         *                                 Float f1, Double d1, Boolean bool1, Void v){
         *         target();
         *     }
         *     public static void target(){}
         * }
         */
        typesCG = PartialCallGraph.generateCallGraph(new File(
            Thread.currentThread().getContextClassLoader().getResource("PremetivesWrappers.class")
                .getFile()));
        typesCHA = PartialCallGraph.createCHA(typesCG);
        typesMethods =
            typesCHA.values().stream()
                .flatMap(opalType -> opalType.getMethods().keySet().stream())
                .collect(Collectors.toList());

        /*
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
        oneEdgeCG = PartialCallGraph.generateCallGraph(new File(
            Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class")
                .getFile()));
        oneEdgeCHA = PartialCallGraph.createCHA(oneEdgeCG);
        oneEdgeMethods =
            oneEdgeCHA.values().stream()
                .flatMap(opalType -> opalType.getMethods().keySet().stream())
                .collect(Collectors.toList());

        /*
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
        classInitCG = PartialCallGraph.generateCallGraph(new File(
            Thread.currentThread().getContextClassLoader().getResource("ClassInit.class")
                .getFile()));
        classInitCHA = PartialCallGraph.createCHA(classInitCG);
        classInitMethods =
            classInitCHA.values().stream()
                .flatMap(opalType -> opalType.getMethods().keySet().stream())
                .collect(Collectors.toList());

        /*
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
        lambdaCG = PartialCallGraph.generateCallGraph(new File(
            Thread.currentThread().getContextClassLoader().getResource("LambdaExample.class")
                .getFile()));
        lambdaCHA = PartialCallGraph.createCHA(lambdaCG);
        lambdaMethods =
            lambdaCHA.values().stream().flatMap(opalType -> opalType.getMethods().keySet().stream())
                .collect(Collectors.toList());
        lambdaNumber =
            lambdaMethods.stream().filter(method -> method.toString().contains("apply")).findAny()
                .orElseThrow().declaringClassFile().thisType().fqn().split("[$]")[1].split(":")[0];


        /*
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
        arrayCG = PartialCallGraph.generateCallGraph(new File(
            Thread.currentThread().getContextClassLoader().getResource("ArrayExample.class")
                .getFile()));
        arrayCHA = PartialCallGraph.createCHA(arrayCG);
        arrayMethods =
            arrayCHA.values().stream().flatMap(opalType -> opalType.getMethods().keySet().stream())
                .collect(Collectors.toList());
    }

    @Test
    public void testToCanonicalSchemelessURI() {

        var method =
            oneEdgeMethods.stream().filter(i -> i.name().equals("sourceMethod")).findFirst()
                .orElseThrow();
        assertEquals(
            new FastenJavaURI("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava" +
                ".lang%2FVoidType"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

        method =
            oneEdgeMethods.stream().filter(i -> i.name().equals("targetMethod")).findFirst()
                .orElseThrow();
        assertEquals(
            new FastenJavaURI("/name.space/SingleSourceToTarget.targetMethod()%2Fjava" +
                ".lang%2FVoidType"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

        method =
            oneEdgeMethods.stream().filter(i -> i.name().equals("<init>")).findFirst()
                .orElseThrow();
        assertEquals(
            new FastenJavaURI(
                "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

        var unresolvedMethod = oneEdgeCG.unresolvedMethodCalls().apply(0);
        assertEquals(
            new FastenJavaURI("/java.lang/Object.Object()VoidType"),
            OPALMethod.toCanonicalSchemelessURI(null, unresolvedMethod.calleeClass(),
                unresolvedMethod.calleeName(), unresolvedMethod.calleeDescriptor())
        );

        method =
            arrayMethods.stream().filter(i -> i.name().equals("targetMethod")).findFirst()
                .orElseThrow();
        assertEquals(
            //  [] is pctEncoded three times, It should stay encoded during creating  1- type's
            //  URI, 2- OPALMethod's URI and 3- Canonicalization.
            new FastenJavaURI(
                "/name.space/ArrayExample.targetMethod(%2Fjava.lang%2FObject%2525255B%2525255D)"
                    + "%2Fjava.lang%2FObject%2525255B%2525255D"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

        method =
            classInitMethods.stream().filter(i -> i.name().equals("<clinit>")).findFirst()
                .orElseThrow();
        assertEquals(
            new FastenJavaURI("/name.space/ClassInit.%2525253Cinit%2525253E()%2Fjava.lang%2FVoidType"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

        method =
            lambdaMethods.stream().filter(i -> i.name().equals("apply")).findFirst().orElseThrow();
        assertEquals(
            new FastenJavaURI("/null/Lambda$" + lambdaNumber
                + "%25253A0.apply(%2Fjava.lang%2FObject)%2Fjava.lang%2FObject"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

        method =
            lambdaMethods.stream().filter(i -> i.name().contains("lambda$new")).findFirst()
                .orElseThrow();
        assertEquals(
            new FastenJavaURI(
                "/name.space/LambdaExample"
                    + ".lambda$new$0(%2Fjava.lang%2FObject)%2Fjava.lang%2FObject"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

        method =
            lambdaMethods.stream().filter(i -> i.name().contains("$newInstance")).findFirst()
                .orElseThrow();

        assertEquals(
            new FastenJavaURI(
                "/null/Lambda$" + lambdaNumber + "%25253A0.$newInstance()Lambda$" + lambdaNumber
                    + "%2525253A0"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

        method =
            lambdaMethods.stream().filter(i -> i.name().equals("<init>")
                && i.declaringClassFile().thisType().simpleName().contains("Lambda$")).findFirst()
                .orElseThrow();
        assertEquals(
            new FastenJavaURI("/null/Lambda$" + lambdaNumber + "%25253A0.Lambda$" + lambdaNumber
                + "%2525253A0()%2Fjava.lang%2FVoidType"),
            OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor())
        );

    }

    @Test
    public void testGetMethodName() {

        var method =
            oneEdgeMethods.stream().filter(i -> i.name().equals("<init>")).findFirst()
                .orElseThrow();
        assertEquals("SingleSourceToTarget", OPALMethod
            .getMethodName(method.declaringClassFile().thisType().simpleName(), method.name()));

        method =
            classInitMethods.stream().filter(i -> i.name().equals("<clinit>")).findFirst()
                .orElseThrow();
        //Three times pctEncoding! It should stay encoded during creating FastenJavaURI and
        // Canonicalization otherwise it throws exception!
        assertEquals("%25253Cinit%25253E", OPALMethod
            .getMethodName(method.declaringClassFile().thisType().simpleName(), method.name()));

        method =
            oneEdgeMethods.stream().filter(i -> i.name().equals("sourceMethod")).findFirst()
                .orElseThrow();
        assertEquals("sourceMethod", OPALMethod
            .getMethodName(method.declaringClassFile().thisType().simpleName(), method.name()));

        method =
            classInitMethods.stream().filter(i -> i.name().equals("targetMethod")).findFirst()
                .orElseThrow();
        assertEquals("targetMethod", OPALMethod
            .getMethodName(method.declaringClassFile().thisType().simpleName(), method.name()));

        var unresolvedMethod = oneEdgeCG.unresolvedMethodCalls().apply(0);
        assertEquals("Object",
            OPALMethod.getMethodName(unresolvedMethod.calleeClass().asObjectType().simpleName(),
                unresolvedMethod.calleeName()));
    }

    @Test
    public void testGetReturnTypeURI() {

        assertEquals(
            new FastenJavaURI("/java.lang/VoidType"),
            OPALMethod.getTypeURI(oneEdgeMethods.get(0)
                .returnType())
        );

        assertEquals(
            new FastenJavaURI("/java.lang/Object%25255B%25255D"),
            OPALMethod.getTypeURI(arrayMethods.stream().filter(i -> i.name().equals(
                "targetMethod")).findFirst().orElseThrow().returnType()));
    }

    @Test
    public void testGetParametersURI() {

        assertArrayEquals(
            new FastenJavaURI[0],
            OPALMethod.getParametersURI(JavaConverters.seqAsJavaList(
                oneEdgeMethods.stream().filter(i -> i.name().equals("sourceMethod")).findFirst()
                    .orElseThrow().parameterTypes())));


        assertArrayEquals(
            new FastenJavaURI[] {new FastenJavaURI("/java.lang/Object%25255B%25255D")},
            OPALMethod.getParametersURI(
                JavaConverters.seqAsJavaList(arrayMethods.stream().filter(i -> i.name().equals(
                    "targetMethod")).findFirst().orElseThrow().parameterTypes())));

    }

    @Test
    public void testGetPackageName() {

        assertEquals(
            "java.lang",
            OPALMethod.getPackageName(arrayMethods.stream().filter(i -> i.name().equals(
                "targetMethod")).findFirst().orElseThrow().returnType()));

        assertEquals(
            "name.space",
            OPALMethod.getPackageName(arrayMethods.stream().filter(i -> i.name().equals(
                "targetMethod")).findFirst().orElseThrow().declaringClassFile().thisType())
        );
    }

    @Test
    public void testGetClassName() {



        assertEquals("ByteType", getType(0));
        assertEquals("CharType", getType(1));
        assertEquals("ShortType", getType(2));
        assertEquals("IntegerType", getType(3));
        assertEquals("LongType", getType(4));
        assertEquals("FloatType", getType(5));
        assertEquals("DoubleType", getType(6));
        assertEquals("BooleanType", getType(7));
        assertEquals("Byte", getType(8));
        assertEquals("Character", getType(9));
        assertEquals("Short", getType(10));
        assertEquals("Integer", getType(11));
        assertEquals("Long", getType(12));
        assertEquals("Float", getType(13));
        assertEquals("Double", getType(14));
        assertEquals("Boolean", getType(15));
        assertEquals("Void", getType(16));

        assertEquals("VoidType",
            OPALMethod.getClassName(typesMethods.stream().filter(i -> i.name().equals(
                "allTypes")).findFirst().orElseThrow().returnType()));

        assertEquals(
            "VoidType",
            OPALMethod.getClassName(oneEdgeMethods.stream().filter(i -> i.name().equals(
                "targetMethod")).findFirst().orElseThrow().returnType()));

        assertEquals(
            "SingleSourceToTarget",
            OPALMethod.getClassName(oneEdgeMethods.stream().filter(i -> i.name().equals(
                "targetMethod")).findFirst().orElseThrow().declaringClassFile().thisType()));

        assertEquals(
            "Object",
            OPALMethod.getClassName(oneEdgeCG.unresolvedMethodCalls().apply(0).calleeClass()));

        assertEquals(
            "ClassInit",
            OPALMethod.getClassName(classInitMethods.stream().filter(i -> i.name().equals(
                "targetMethod")).findFirst().orElseThrow().declaringClassFile().thisType()));

        assertEquals(
            "Object%25255B%25255D",
            OPALMethod.getClassName(arrayMethods.stream().filter(i -> i.name().equals(
                "targetMethod")).findFirst().orElseThrow().returnType()));

    }

    private String getType(int paremeterIndex) {
        return OPALMethod.getClassName(typesMethods.stream().filter(i -> i.name().equals(
            "allTypes")).findFirst().orElseThrow().parameterTypes().apply(paremeterIndex));
    }

}