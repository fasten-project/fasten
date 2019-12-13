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

import org.junit.Test;
import org.junit.BeforeClass;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CallGraphMergerTest {

    static ProposalRevisionCallGraph artifact;
    static ProposalRevisionCallGraph dependency;

    @BeforeClass
    public static void generateCallGraph() {

        /**
         * Importer is a java8 compiled bytecode of:
         *<pre>
         * package name.space;
         *
         * import depen.dency.Imported;
         *
         * public class Importer {
         *     public Importer() {
         *     }
         *
         *     public static void sourceMethod() {
         *         Imported.targetMethod();
         *     }
         * }
         * </pre>
         */
        var importerGraph = new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("Importer.class").getFile()));

        artifact = new ProposalRevisionCallGraph("mvn",
                "ImporterGroup.ImporterArtifact",
                "1.7.29",
                1574072773,
                Arrays.asList(),
                importerGraph.toURIGraph(),
                PartialCallGraph.toURIHierarchy(importerGraph.getClassHierarchy()));

        /**
         * Imported is a java8 compiled bytecode of:
         *<pre>
         * package depen.dency;
         *
         * public class Imported {
         *     public Imported() {
         *     }
         *
         *     public static void targetMethod() {
         *     }
         * }
         * </pre>
         */
        var importedGraph = new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("Imported.class").getFile()));

        dependency = new ProposalRevisionCallGraph("mvn",
                "ImportedGroup.ImportedArtifact",
                "1.7.29",
                1574072773,
                Arrays.asList(),
                importedGraph.toURIGraph(),
                PartialCallGraph.toURIHierarchy(importedGraph.getClassHierarchy()));
    }

    @Test
    public void testResolve() {

    }

    @Test
    public void testMergeCallGraphs() {

        assertEquals(new FastenJavaURI("//SomeDependency/depen.dency/Imported.targetMethod()%2Fjava.lang%2FVoid"),
                artifact.graph.stream().filter(i -> i[1].toString().contains("targetMethod")).findFirst().get()[1]);

        assertEquals(new FastenJavaURI("//ImportedGroup.ImportedArtifact/depen.dency/Imported.targetMethod()%2Fjava.lang%2FVoid"),
                CallGraphMerger.mergeCallGraph(artifact, Arrays.asList(dependency))
                        .graph.stream().filter(i -> i[1].toString().contains("targetMethod")).findFirst().get()[1]);
    }
}