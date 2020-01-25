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

package eu.fasten.analyzer.javacgopal.merge;

import eu.fasten.analyzer.javacgopal.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgopal.data.callgraph.PartialCallGraph;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;
import org.junit.BeforeClass;

public class CallGraphDifferentiatorTest {

    static ExtendedRevisionCallGraph firstGraph;
    static ExtendedRevisionCallGraph secondGraph;

    @BeforeClass
    public static void generateCallGraph() {

        var DiffExampleFirst = new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("DiffExampleFirst.class").getFile()));

        firstGraph = new ExtendedRevisionCallGraph("mvn",
                "DiffExample",
                "1.7.29",
                1574072773,
                Arrays.asList(),
                DiffExampleFirst.toURIGraph(),
                PartialCallGraph.toURIHierarchy(DiffExampleFirst.getClassHierarchy()));

        var DiffExampleSecond = new PartialCallGraph(new File(Thread.currentThread().getContextClassLoader().getResource("DiffExampleSecond.class").getFile()));

        secondGraph = new ExtendedRevisionCallGraph("mvn",
                "DiffExample",
                "1.7.29",
                1574072773,
                Arrays.asList(),
                DiffExampleSecond.toURIGraph(),
                PartialCallGraph.toURIHierarchy(DiffExampleSecond.getClassHierarchy()));

    }

    @Test
    public void testDiff() throws IOException {
        //CallGraphDifferentiator.diff("/Users/mehdi/Desktop/FastenRepo/current1/fasten/analyzer/javacg-opal/target/",1, firstGraph, secondGraph);
    }
}