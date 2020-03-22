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

import eu.fasten.analyzer.baseanalyzer.MavenCoordinate;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgopal.data.callgraph.PartialCallGraph;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;

public class CallGraphDifferentiatorTest {

    static ExtendedRevisionCallGraph firstGraph;
    static ExtendedRevisionCallGraph secondGraph;

    @BeforeClass
    public static void generateCallGraph() throws FileNotFoundException {

        var diffExampleFirst = new PartialCallGraph(new File(
            Thread.currentThread().getContextClassLoader().getResource("DiffExampleFirst.class")
                .getFile()));

        final var coord = new MavenCoordinate("mvn", "DiffExample", "1.7.29");

        firstGraph = ExtendedRevisionCallGraph.extendedBuilder()
            .forge("mvn")
            .product(coord.getProduct())
            .version(coord.getVersionConstraint())
            .cgGenerator(diffExampleFirst.getGENERATOR())
            .timestamp(1574072773)
            .graph(diffExampleFirst.getGraph())
            .classHierarchy(diffExampleFirst.getClassHierarchy())
            .build();

        var diffExampleSecond = new PartialCallGraph(new File(
            Thread.currentThread().getContextClassLoader().getResource("DiffExampleSecond.class")
                .getFile()));

        secondGraph = ExtendedRevisionCallGraph.extendedBuilder()
            .forge("mvn")
            .product(coord.getProduct())
            .version(coord.getVersionConstraint())
            .cgGenerator(diffExampleSecond.getGENERATOR())
            .timestamp(1574072773)
            .graph(diffExampleSecond.getGraph())
            .classHierarchy(diffExampleSecond.getClassHierarchy())
            .build();
    }

    @Test
    public void testDiff() throws IOException {
        CallGraphDifferentiator.diffInFile("", 1, firstGraph, secondGraph);
    }
}