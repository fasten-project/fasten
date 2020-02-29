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

package eu.fasten.analyzer.javacgopal.data.callgraph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.OPALType;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opalj.ai.analyses.cg.ComputedCallGraph;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.br.analyses.Project;
import scala.collection.JavaConverters;

public class PartialCallGraphTest {

    static PartialCallGraph singleSourceToTarget;
    static File jarFile;
    static Project<?> artifactInOpalFormat;
    static ComputedCallGraph cg;
    static Map<ObjectType, OPALType> cha;
    static List<Method> methodsList;
    static Map<Integer, FastenURI> methodsMap;

    /**
     * SingleSourceToTarget is a java8 compiled bytecode.
     * <pre>
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
     *  Unresolved:[ public void "<"init">"() of current class,
     *               public void "<"init">"() of Object class]
     */
    @BeforeClass
    public static void generateCallGraph() {

        jarFile = new File(
            Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class")
                .getFile());
        singleSourceToTarget = new PartialCallGraph(jarFile);
        artifactInOpalFormat = Project.apply(jarFile);
        cg = PartialCallGraph.generateCallGraph(jarFile);
        cha = PartialCallGraph.createCHA(cg);
        methodsList =
            new ArrayList<>(JavaConverters.asJavaCollection(cg.callGraph().project().allMethods()));
        methodsMap = new HashMap<>(Map.of(
            0, FastenURI.create(
                "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid"),
            1,
            FastenURI.create("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid"),
            2, FastenURI
                .create("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid")));

    }

    @Test
    public void testGenerateCallGraph() {

        final var cg = PartialCallGraph.generateCallGraph(jarFile);
        final var allMethods =
            new ArrayList<>(JavaConverters.asJavaCollection(cg.callGraph().project().allMethods()));
        assertEquals("public void <init>()", allMethods.get(0).toString());
        assertEquals("public static void sourceMethod()", allMethods.get(1).toString());
        assertEquals("public static void targetMethod()", allMethods.get(2).toString());

        final var unresolvedCalls =
            new ArrayList<>(JavaConverters.asJavaCollection(cg.unresolvedMethodCalls()));
        assertEquals("public void <init>()", unresolvedCalls.get(0).caller().toString());
        assertEquals("java.lang.Object", unresolvedCalls.get(0).calleeClass().toJava());
        assertEquals("<init>", unresolvedCalls.get(0).calleeName());
        assertEquals("(): void", unresolvedCalls.get(0).calleeDescriptor().valueToString());

    }

    @Test
    public void testFindEntryPoints() {
        var entryPoints = PartialCallGraph.findEntryPoints(
            JavaConverters.asJavaIterable(artifactInOpalFormat.allMethodsWithBody()));
        assertEquals(3, entryPoints.size());
        assertEquals("public void <init>()", entryPoints.head().toString());
        assertEquals("public static void sourceMethod()", entryPoints.tail().head().toString());
        assertEquals("public static void targetMethod()",
            entryPoints.tail().tail().head().toString());

    }

    @Test
    public void testPartialCallGraph() {

        assertNotNull(singleSourceToTarget);
        assertEquals("OPAL", singleSourceToTarget.getGENERATOR());
        assertEquals("PartialCallGraph{"
                + "classHierarchy={"
                + "/name.space/SingleSourceToTarget="
                + "Type{"
                + "sourceFileName='SingleSourceToTarget.java', "
                + "methods={"
                + "0=/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid, "
                + "1=/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid, "
                + "2=/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid}, "
                + "superClasses=[/java.lang/Object], "
                + "superInterfaces=[]}}, "
                + "resolvedCalls=[1,2], "
                + "unresolvedCalls={(0,///java.lang/Object.Object()Void)={invokespecial=1}}}",
            singleSourceToTarget.toString());

    }

    @Test
    public void testGraphShouldNotContainDuplicateArcs() throws FileNotFoundException {


        //This artifact has some duplicate arcs in OPAL result.
        final var cg = new PartialCallGraph(MavenCoordinate.MavenResolver.downloadJar(
            "HTTPClient"
                + ":HTTPClient"
                + ":0.3-3").orElseThrow(RuntimeException::new));


        //Based on logs this arc of the resolved calls was duplicated.
        //Before removing duplicates the size of this duplicate arcs was 32.
        final List<FastenURI[]> arcs = new ArrayList<>();
        for (final var method : cg.mapOfAllMethods().entrySet()) {
            if (method.getValue().toString().equals("/HTTPClient/IdempotentSequence.main(%2Fjava"
                + ".lang%2FString%25255B%25255D)%2Fjava.lang%2FVoid")) {
                for (final var call : cg.getResolvedCalls()) {
                    if (call.get(0).equals(method.getKey())) {
                        arcs.add(new FastenURI[] {
                            method.getValue(),
                            cg.mapOfAllMethods().get(call.get(1))}
                        );
                    }
                }
            }
        }
        final List<FastenURI[]> duplicateArcs = new ArrayList<>();
        for (final var arc : arcs) {
            if (arc[1].toString().equals("/HTTPClient/Request.Request(HTTPConnection,%2Fjava"
                + ".lang%2FString,%2Fjava.lang%2FString,NVPair%25255B%25255D,%2Fjava"
                + ".lang%2FByte%25255B%25255D,HttpOutputStream,"
                + "%2Fjava.lang%2FBoolean)%2Fjava.lang%2FVoid")) {
                duplicateArcs.add(arc);
            }
        }
        assertEquals(1, duplicateArcs.size());


        //Based on logs this arc of the unresolved calls was duplicated.
        //Before removing duplicates the size of this duplicate arcs was 3.
        arcs.clear();
        for (final var method : cg.mapOfAllMethods().entrySet()) {
            if (method.getValue().toString().contains(
                "/HTTPClient/UncompressInputStream.read(%2Fjava.lang%2FByte%25255B%25255D,%2Fjava"
                    + ".lang%2FInteger,%2Fjava.lang%2FInteger)%2Fjava.lang%2FInteger")) {
                for (final var call : cg.getUnresolvedCalls().entrySet()) {
                    if (call.getKey().getLeft().equals(method.getKey())) {
                        arcs.add(new FastenURI[] {
                            method.getValue(),
                            call.getKey().getRight()}
                        );
                    }
                }
            }
        }
        duplicateArcs.clear();
        for (final var arc : arcs) {
            if (arc[1].toString().contains(
                "///java.lang/System.arraycopy(Object,Integer,Object,Integer,Integer)Void")) {
                duplicateArcs.add(arc);
            }
        }
        assertEquals(1, duplicateArcs.size());

    }


    @Test
    public void testToURIInterfaces() {

        assertEquals(new ArrayList<>(), PartialCallGraph.toURIInterfaces(
            cha.get(methodsList.get(0).declaringClassFile().thisType()).getSuperInterfaces()));
    }

    @Test
    public void testToURIClasses() {

        assertEquals(
            new LinkedList<FastenURI>(Arrays.asList(new FastenJavaURI("/java.lang/Object"))),
            PartialCallGraph.toURIClasses(
                cha.get(methodsList.get(0).declaringClassFile().thisType()).getSuperClasses()));
    }

    @Test
    public void testToURIMethods() {

        assertEquals(methodsMap,
            PartialCallGraph.toURIMethods(
                cha.get(methodsList.get(0).declaringClassFile().thisType()).getMethods())
        );
    }

    @Test
    public void testToURIHierarchy() {

        final var mock = new HashMap(Map.of(FastenURI.create(
            "/name.space/SingleSourceToTarget"),
            new ExtendedRevisionCallGraph.Type("SingleSourceToTarget.java",
                methodsMap,
                new LinkedList<>(Arrays.asList(new FastenJavaURI("/java.lang/Object"))),
                new ArrayList<>())));

        assertEquals(mock.toString().replace(" ", ""),
            PartialCallGraph.asURIHierarchy(cha).toString().replace(" ", ""));

    }

    @Test
    public void getTargetURI() {
    }

    @Test
    public void createCHA() {
    }

    @Test
    public void extractSuperClasses() {
    }

    @Test
    public void extractSuperInterfaces() {
    }

    @Test
    public void getResolvedCalls() {
    }

    @Test
    public void getUnresolvedCalls() {
    }

    @Test
    public void getClassHierarchy() {
    }

    @Test
    public void getGENERATOR() {
    }

    @Test
    public void getGraph() {
    }

    @Test
    public void mapOfAllMethods() {
    }

    @Test
    public void testToString() {
    }
}