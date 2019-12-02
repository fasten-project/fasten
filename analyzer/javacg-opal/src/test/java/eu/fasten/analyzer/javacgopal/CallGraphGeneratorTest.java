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

import org.junit.BeforeClass;
import org.junit.Test;
import org.opalj.br.analyses.Project;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class CallGraphGeneratorTest {

    static PartialCallGraph callgraph;
    static File jarFile;
    static Project artifactInOpalFormat;

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
        jarFile = new File(Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class").getFile());
        callgraph = CallGraphGenerator.generatePartialCallGraph(jarFile);
        artifactInOpalFormat = Project.apply(jarFile);

    }

    @Test
    public void testGeneratePartialCallGraph() {

        assertEquals("public static void sourceMethod()",callgraph.getResolvedCalls().get(0).getSource().toString());
        assertEquals("public static void targetMethod()",callgraph.getResolvedCalls().get(0).getTarget().get(0).toString());
        assertEquals("public void <init>()",callgraph.getUnresolvedCalls().get(0).caller().toString());
        assertEquals("name/space/SingleSourceToTarget",callgraph.getUnresolvedCalls().get(0).caller().declaringClassFile().thisType().fqn());
        assertEquals("java/lang/Object",callgraph.getUnresolvedCalls().get(0).calleeClass().asObjectType().fqn());
        assertEquals("<init>",callgraph.getUnresolvedCalls().get(0).calleeName());
    }

    @Test
    public void testFindEntryPoints() {

        var entryPoints = CallGraphGenerator.findEntryPoints(artifactInOpalFormat.allMethodsWithBody());
        assertEquals(3, entryPoints.size());
        assertEquals("public void <init>()", entryPoints.head().toString());
        assertEquals("public static void sourceMethod()", entryPoints.tail().head().toString());
        assertEquals("public static void targetMethod()", entryPoints.tail().tail().head().toString());

    }
}