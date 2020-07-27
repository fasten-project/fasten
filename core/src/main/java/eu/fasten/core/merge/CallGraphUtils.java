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

package eu.fasten.core.merge;

import eu.fasten.core.data.RevisionCallGraph;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CallGraphUtils {
    private static Logger logger = LoggerFactory.getLogger(CallGraphUtils.class);


    /**
     * It writes two ProposalRevisionCallGraphs together with their differences in the provided.
     * path
     * @param resultPath  the path for the result diff
     * @param graphNumber the number of the graph this number will be written as a prefix to the
     *                    result files
     * @param firstGraph  first graph to be compared
     * @param secondGraph second graph to be compared
     * @throws IOException throws IOException.
     */
    public static void diffInFile(final String resultPath, final int graphNumber,
                                  final RevisionCallGraph firstGraph,
                                  final RevisionCallGraph secondGraph) throws IOException {

        final String graphPath =
            resultPath + graphNumber + "_" + firstGraph.product + "." + firstGraph.version;

        firstGraph.sortInternalCalls();
        secondGraph.sortInternalCalls();

        writeToFile(graphPath, firstGraph.toJSON(),"_1.txt");
        writeToFile(graphPath, secondGraph.toJSON(),"_2.txt");

        Runtime.getRuntime().exec(new String[] {"sh", "-c",
            "diff " + graphPath + "_1.txt" + " " + graphPath + "_2.txt" + " > " + graphPath
                + "_Diff.txt"});
    }

    /**
     * Writes Strings to files, can be used to output the graphs.
     * @param path the path to write
     * @param graph the String representation of graph or any other String to be written to a file
     * @param suffix the suffix to put at the end of the path, most of the time file name
     * @throws IOException throws if IO problems occur during writing in a file
     */
    public static void writeToFile(final String path, final JSONObject graph, final String suffix)
        throws IOException {
        if (!graph.isEmpty()) {
            logger.info("Writing graph to {}", path+suffix);
        }
        final BufferedWriter writer;
        writer = new BufferedWriter(new FileWriter(path + suffix));
        writer.write(graph.toString(4));
        writer.close();
    }

}
