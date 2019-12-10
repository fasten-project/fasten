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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CallGraphMergerTest {

    @Test
    void testResolve() {

    }

    @Test
    void testMergeCallGraphs() {
        var revisionCallGraph = PartialCallGraph.createRevisionCallGraph("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api","1.7.29"),
                1574072773,
                new PartialCallGraph(
                        new File(Thread.currentThread().getContextClassLoader().getResource("Importer.class").getFile())
                )
        );
//        var revisionCallGraph1 = PartialCallGraph.createProposalRevisionCallGraph("mvn",
//                new MavenCoordinate("org.slf4j", "slf4j-api","1.7.29"),
//                1574072773,
//                new PartialCallGraph(
//                        new File(Thread.currentThread().getContextClassLoader().getResource("Importer.class").getFile())
//                )
//        );
//
//        CallGraphMerger.mergeCallGraphs(Arrays.asList(revisionCallGraph,revisionCallGraph1));
    }
}