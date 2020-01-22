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

import eu.fasten.analyzer.javacgopal.data.callgraph.ProposalRevisionCallGraph;
import eu.fasten.core.data.FastenURI;

import java.io.*;
import java.util.Comparator;

public class CallGraphDifferentiator {

    /**
     * It writes two ProposalRevisionCallGraphs together with their differences in the provided path
     * @param resultPath the path for the result diff
     * @param graphNumber the number of the graph this number will be written as a prefix to the result files
     * @param firstGraph first graph to be compared
     * @param secondGraph second graph to be compared
     * @throws IOException
     */
    public static void diff(String resultPath, int graphNumber, ProposalRevisionCallGraph firstGraph, ProposalRevisionCallGraph secondGraph) throws IOException {

        String graphPath = resultPath + graphNumber + "_" + firstGraph.product + "." + firstGraph.version;

        firstGraph.graph.sort(getComparing());
        secondGraph.graph.sort(getComparing());

        writeToFile(firstGraph.toJSON().toString(4), graphPath, "_1.txt");
        writeToFile(secondGraph.toJSON().toString(4), graphPath, "_2.txt");

        Runtime.getRuntime().exec(new String[]{"sh", "-c", "diff " + graphPath + "_1.txt" + " " + graphPath + "_2.txt" + " > " + graphPath + "_Diff.txt"});
    }

    private static void writeToFile(String path, String graph, String suffix) throws IOException {
        BufferedWriter writer;
        writer = new BufferedWriter(new FileWriter(path + suffix));
        writer.write(graph);
        writer.close();
    }

    private static Comparator<FastenURI[]> getComparing() {
        return Comparator.comparing(o -> (o[0].toString() + o[1].toString()));
    }


}
