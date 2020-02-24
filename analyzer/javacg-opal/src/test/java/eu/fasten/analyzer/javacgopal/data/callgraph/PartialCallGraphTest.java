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

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import org.junit.Test;
import org.junit.BeforeClass;
import org.opalj.br.analyses.Project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertArrayEquals;

public class PartialCallGraphTest {

    static PartialCallGraph singleSourceToTarget,lambdaCallGraph;
    static File jarFile;
    static Project artifactInOpalFormat;

    @BeforeClass
    public static void generateCallGraph() {

        lambdaCallGraph = new PartialCallGraph(
                new File(Thread.currentThread().getContextClassLoader().getResource("TwoCalls.class").getFile())
        );

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
        jarFile = new File(Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class").getFile());
        singleSourceToTarget = new PartialCallGraph(jarFile);
        artifactInOpalFormat = Project.apply(jarFile);


    }

    @Test
    public void testGeneratePartialCallGraph() {

//        assertEquals("public static void sourceMethod()", singleSourceToTarget.getResolvedCalls().get(0).getSource().toString());
//        assertEquals("public static void targetMethod()", singleSourceToTarget.getResolvedCalls().get(0).getTarget().toString());
//        assertEquals("public void <init>()", singleSourceToTarget.getUnresolvedCalls().get(0).caller().toString());
//        assertEquals("name/space/SingleSourceToTarget", singleSourceToTarget.getUnresolvedCalls().get(0).caller().declaringClassFile().thisType().fqn());
//        assertEquals("java/lang/Object", singleSourceToTarget.getUnresolvedCalls().get(0).calleeClass().asObjectType().fqn());
//        assertEquals("<init>", singleSourceToTarget.getUnresolvedCalls().get(0).calleeName());
    }

    @Test
    public void testFindEntryPoints() {

        var entryPoints = PartialCallGraph.findEntryPoints(artifactInOpalFormat.allMethodsWithBody());
        assertEquals(3, entryPoints.size());
        assertEquals("public void <init>()", entryPoints.head().toString());
        assertEquals("public static void sourceMethod()", entryPoints.tail().head().toString());
        assertEquals("public static void targetMethod()", entryPoints.tail().tail().head().toString());

    }

    @Test
    public void testCreateRevisionCallGraph() throws FileNotFoundException {

//        var revisionCallGraph = PartialCallGraph.createRevisionCallGraph("mvn",
//                new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29"),
//                1574072773,
//                new PartialCallGraph(
//                        MavenCoordinate.MavenResolver.downloadJar("org.slf4j:slf4j-api:1.7.29").orElseThrow(RuntimeException::new)
//                )
//        );
//
//        assertNotNull(revisionCallGraph);
//        assertEquals("mvn", revisionCallGraph.forge);
//        assertEquals("1.7.29", revisionCallGraph.version);
//        assertEquals(1574072773, revisionCallGraph.timestamp);
//        assertEquals(new FastenJavaURI("fasten://mvn!org.slf4j.slf4j-api$1.7.29"), revisionCallGraph.uri);
//        assertEquals(new FastenJavaURI("fasten://org.slf4j.slf4j-api$1.7.29"), revisionCallGraph.forgelessUri);
//        assertEquals("org.slf4j.slf4j-api", revisionCallGraph.product);
//        assertNotEquals(0, revisionCallGraph.graph.size());

    }

    @Test
    public void testToURICallGraph() throws FileNotFoundException {

//        assertArrayEquals(
//                new FastenJavaURI[]{
//                        new FastenJavaURI("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid"),
//                        new FastenJavaURI("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid")}
//                ,
//                singleSourceToTarget.toURIGraph().get(0)
//        );

        //This artifact has some duplicate arcs in OPAL result.
//        var duplicatedEdgesGraph = PartialCallGraph.createRevisionCallGraph("mvn",
//                new MavenCoordinate("HTTPClient", "HTTPClient", "0.3-3"),
//                1574072773,
//                new PartialCallGraph(
//                        MavenCoordinate.MavenResolver.downloadJar("HTTPClient:HTTPClient:0.3-3").orElseThrow(RuntimeException::new)
//                )
//        );

        //Based on logs this arc of the resolved calls was duplicated. Before removing duplicates the size of this duplicate arcs was 32.
//        assertEquals(1,
//                searchInGraph(
//                        searchInGraph(duplicatedEdgesGraph.graph, 0, "IdempotentSequence.main"),
//                        1,
//                        "Request.Request"
//                ).size()
//        );

        //Based on logs this arc of the unresolved calls was duplicated. Before removing duplicates the size of this duplicate arcs was 3.
//        assertEquals(1,
//                searchInGraph(
//                        searchInGraph(duplicatedEdgesGraph.graph, 0, "UncompressInputStream.read"),
//                        1,
//                        "System.arraycopy"
//                ).size()
//        );

    }

    private List<FastenURI[]> searchInGraph(List<FastenURI[]> graph, int sourceOrTarget, String stringToSearch) {
        List<FastenURI[]> arcs = new ArrayList<>();
        for (FastenURI[] fastenURIS : graph) {
            if (fastenURIS[sourceOrTarget].toString().contains(stringToSearch)) {
                arcs.add(fastenURIS);
            }
        }
        return arcs;
    }

    @Test
    public void testToURIInterfaces() {

//        assertEquals(
//                new ArrayList<>(),
//                PartialCallGraph.toURIInterfaces(singleSourceToTarget.getClassHierarchy().get(singleSourceToTarget.getResolvedCalls().get(0).getSource().declaringClassFile().thisType()).getSuperInterfaces())
//        );
    }

    @Test
    public void testToURIClasses() {

//        assertEquals(
//                new LinkedList<FastenURI>(Arrays.asList(new FastenJavaURI("/java.lang/Object"))),
//                PartialCallGraph.toURIClasses(singleSourceToTarget.getClassHierarchy().get(singleSourceToTarget.getResolvedCalls().get(0).getSource().declaringClassFile().thisType()).getSuperClasses())
//        );
    }

    @Test
    public void testToURIMethods() {
//        Map<Integer,FastenJavaURI> mock = new HashMap<>();
//        mock.put(0, new FastenJavaURI("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid"));
//        mock.put(1, new FastenJavaURI("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid"));
//        mock.put(2, new FastenJavaURI("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid"));
//
//        assertEquals(mock,
//                PartialCallGraph.toURIMethods(singleSourceToTarget.getClassHierarchy().get(singleSourceToTarget.getResolvedCalls().get(0).getSource().declaringClassFile().thisType()).getMethods())
//        );
    }

    @Test
    public void testToURIHierarchy() {

        Map<Integer,FastenJavaURI> mock = new HashMap<>();
        mock.put(0, new FastenJavaURI("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid"));
        mock.put(1, new FastenJavaURI("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid"));
        mock.put(2, new FastenJavaURI("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid"));

//        assertEquals(mock,
//                PartialCallGraph.toURIMethods(singleSourceToTarget.getClassHierarchy().get(singleSourceToTarget.getResolvedCalls().get(0).getSource().declaringClassFile().thisType()).getMethods())
//        );

//        assertEquals(
//                new ArrayList<>(),
//                PartialCallGraph.getURIHierarchy(singleSourceToTarget.createCHA()).get(new FastenJavaURI("/name.space/SingleSourceToTarget")).getSuperInterfaces()
//        );
//
//        assertEquals(
//                Arrays.asList(new FastenJavaURI("/java.lang/Object")),
//                PartialCallGraph.getURIHierarchy(singleSourceToTarget.createCHA()).get(new FastenJavaURI("/name.space/SingleSourceToTarget")).getSuperClasses()
//        );

    }

    @Test
    public void testGetInvocationType() {
    }
}