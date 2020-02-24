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

package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.ipa.callgraph.CallGraph;
import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;

public class WalaResultAnalyzer {

    private final CallGraph rawCallGraph;

    private final PartialCallGraph partialCallGraph;

    /**
     * Analyze result produced by Wal plugin.
     *
     * @param rawCallGraph Raw call graph in Wala format
     * @param coordinate   List of {@link MavenCoordinate}
     */
    private WalaResultAnalyzer(CallGraph rawCallGraph, MavenCoordinate coordinate) {
        this.rawCallGraph = rawCallGraph;
        this.partialCallGraph = new PartialCallGraph(coordinate);
    }

    /**
     * Convert raw Wala call graph to {@link PartialCallGraph}.
     *
     * @param rawCallGraph Raw call graph in Wala format
     * @param coordinate   List of {@link MavenCoordinate}
     * @return Partial call graph
     */
    public static PartialCallGraph wrap(CallGraph rawCallGraph,
                                        MavenCoordinate coordinate) {
        if (rawCallGraph == null) {
            return new PartialCallGraph(coordinate);
        }

        WalaResultAnalyzer walaResultAnalyzer = new WalaResultAnalyzer(rawCallGraph, coordinate);

        CallGraphAnalyzer callGraphAnalyzer = new CallGraphAnalyzer(walaResultAnalyzer.rawCallGraph,
                walaResultAnalyzer.partialCallGraph);
        callGraphAnalyzer.resolveCalls();

        return walaResultAnalyzer.partialCallGraph;
    }
}
