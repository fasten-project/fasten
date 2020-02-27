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
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WalaResultAnalyzer {

    private static Logger logger = LoggerFactory.getLogger(WalaResultAnalyzer.class);

    private final CallGraph rawCallGraph;

    private final PartialCallGraph partialCallGraph;

    /**
     * Analyze result produced by Wal plugin.
     *
     * @param rawCallGraph Raw call graph in Wala format
     */
    private WalaResultAnalyzer(CallGraph rawCallGraph) {
        this.rawCallGraph = rawCallGraph;
        this.partialCallGraph = new PartialCallGraph();
    }

    /**
     * Convert raw Wala call graph to {@link PartialCallGraph}.
     *
     * @param rawCallGraph Raw call graph in Wala format
     * @return Partial call graph
     */
    public static PartialCallGraph wrap(CallGraph rawCallGraph) {
        if (rawCallGraph == null) {
            logger.info("Call graph is NULL");
            return new PartialCallGraph();
        }

        final NumberFormat timeFormatter = new DecimalFormat("#0.000");
        logger.info("Wrapping call graph with {} nodes...", rawCallGraph.getNumberOfNodes());
        long startTime = System.currentTimeMillis();

        WalaResultAnalyzer walaResultAnalyzer = new WalaResultAnalyzer(rawCallGraph);

        CallGraphAnalyzer callGraphAnalyzer = new CallGraphAnalyzer(walaResultAnalyzer.rawCallGraph,
                walaResultAnalyzer.partialCallGraph);
        callGraphAnalyzer.resolveCalls();

        logger.info("Wrapped call graph in {} seconds [Resolved calls: {}, Unresolved calls: {}]",
                timeFormatter.format((System.currentTimeMillis() - startTime) / 1000d),
                walaResultAnalyzer.partialCallGraph.getResolvedCalls().size(),
                walaResultAnalyzer.partialCallGraph.getUnresolvedCalls().size());

        return walaResultAnalyzer.partialCallGraph;
    }
}
