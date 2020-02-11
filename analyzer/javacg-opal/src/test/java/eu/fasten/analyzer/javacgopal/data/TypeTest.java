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

import eu.fasten.analyzer.javacgopal.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgopal.data.callgraph.PartialCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

public class TypeTest {
    static PartialCallGraph callgraph;
    static ExtendedRevisionCallGraph extendedRevisionCallGraph;

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
        callgraph = new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class").getFile()));
        extendedRevisionCallGraph = new ExtendedRevisionCallGraph("mvn",
                "SingleSourceToTarget",
                "1.7.29",
                1574072773,
                Arrays.asList(),
                callgraph.toURIGraph(),
                PartialCallGraph.toURIHierarchy(callgraph.getClassHierarchy()));
    }

    @Test
    public void testExtractSourceFile() {

        assertEquals("SingleSourceToTarget.java",
                extendedRevisionCallGraph.getClassHierarchy()
                        .get(new FastenJavaURI("/name.space/SingleSourceToTarget"))
                        .getSourceFileName()
        );
    }

    @Test
    public void testSetSupers() {

        assertEquals(new FastenJavaURI("/java.lang/Object"),
                extendedRevisionCallGraph.getClassHierarchy()
                        .get(new FastenJavaURI("/name.space/SingleSourceToTarget"))
                        .getSuperClasses().get(0)
        );
    }
}