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

import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.data.JavaNode;
import it.unimi.dsi.fastutil.ints.IntIntPair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CallGraphUtils {
    private static final Logger logger = LoggerFactory.getLogger(CallGraphUtils.class);


    /**
     * It writes two ProposalRevisionCallGraphs together with their differences in the provided.
     * path
     *
     * @param resultPath  the path for the result diff
     * @param graphNumber the number of the graph this number will be written as a prefix to the
     *                    result files
     * @param firstGraph  first graph to be compared
     * @param secondGraph second graph to be compared
     * @throws IOException throws IOException.
     */
    public static void diffInFile(final String resultPath, final int graphNumber,
                                  final ExtendedRevisionJavaCallGraph firstGraph,
                                  final ExtendedRevisionJavaCallGraph secondGraph) throws IOException {

        final String graphPath =
                resultPath + graphNumber + "_" + firstGraph.product + "." + firstGraph.version;

        writeToFile(graphPath, JSONUtils.toJSONString(firstGraph), "_1.txt");
        writeToFile(graphPath, JSONUtils.toJSONString(secondGraph), "_2.txt");

        Runtime.getRuntime().exec(new String[]{"sh", "-c",
                "diff " + graphPath + "_1.txt" + " " + graphPath + "_2.txt" + " > " + graphPath
                        + "_Diff.txt"});
    }

    /**
     * Writes Strings to files, can be used to output the graphs.
     *
     * @param path   the path to write
     * @param graph  the String representation of graph or any other String to be written to a file
     * @param suffix the suffix to put at the end of the path, most of the time file name
     * @throws IOException throws if IO problems occur during writing in a file
     */
    public static void writeToFile(final String path, final String graph, final String suffix)
            throws IOException {
        if (!graph.isEmpty()) {
            logger.info("Writing graph to {}", path + suffix);
        }
        final BufferedWriter writer;
        writer = new BufferedWriter(new FileWriter(path + suffix));
        writer.write(graph);
        writer.close();
    }

    /**
     * Converts {@link ExtendedRevisionCallGraph} graph into the list of node pairs.
     *
     * @param ercg call graph
     * @return list of node pairs
     */
    public static Map<String, List<Pair<String, String>>> convertToNodePairs(
        final ExtendedRevisionJavaCallGraph ercg) {

        return convertToNodePairs(ercg, true, true);
    }

    public static Map<String, List<Pair<String, String>>> convertToNodePairs(
        final ExtendedRevisionJavaCallGraph ercg, final boolean includeExternals,
        final boolean includeInternals) {

        final Map<String, List<Pair<String, String>>> result =
            new HashMap<>();
        final var methods = ercg.mapOfAllMethods();
        final var types = ercg.nodeIDtoTypeNameMap();
        if (includeInternals) {
            result.put("internalTypes",
                getEdges(ercg.getGraph().getCallSites(), methods, types));
        }
        result.put("resolvedTypes",
            getEdges(ercg.getGraph().getResolvedCalls(), methods, types));

        if (includeExternals) {
            result.put("externalTypes",
                getEdges(ercg.getGraph().getExternalCalls(), methods, types));
        }

        return result;
    }

    /**
     * Get edges of an {@link ExtendedRevisionCallGraph}.
     *
     * @param calls   source, target of a call and metadata
     * @param methods node information
     * @param types   types information
     * @return edges list
     */
    private static List<Pair<String, String>> getEdges(
            final Map<IntIntPair, Map<Object, Object>> calls,
            final Map<Integer, JavaNode> methods,
            final Map<Integer, String> types) {

        final List<Pair<String, String>> result = new ArrayList<>();

        for (final var exCall : calls.entrySet()) {
            result.add(MutablePair.of(decode(types.get(exCall.getKey().firstInt())) + "." +
                            decode(methods.get(exCall.getKey().firstInt()).getSignature()),
                    decode(types.get(exCall.getKey().secondInt())) + "." + decode(methods.get(exCall.getKey().secondInt()).getSignature())));
        }
        return result;
    }

    /**
     * Decodes method signature.
     *
     * @param methodSignature method signature
     * @return decoded method signature
     */
    public static String decode(final String methodSignature) {
        String result = methodSignature;
        while (result.contains("%")) {
            result = URLDecoder.decode(result, StandardCharsets.UTF_8);
        }
        return result;
    }

    /**
     * Convert pairs of edges to a String.
     *
     * @param pairs edges
     * @return String representation of edges
     */
    public static String toStringEdges(List<Pair<String, String>> pairs) {
        StringBuilder result = new StringBuilder();
        if (pairs != null) {
            for (final var edge : pairs.stream().sorted().collect(Collectors.toList())) {
                result.append(getStringEdge(edge));
            }
        }
        return result.toString();
    }

    /**
     * Converts a pair of edges to a String representation.
     *
     * @param edge edge
     * @return String representation of an edge
     */
    public static String getStringEdge(final Pair<String, String> edge) {
        return decode(edge.getLeft()) + " '->" + "'\n" + decode(edge.getRight()) + "\n\n";
    }

    /**
     * Convert data to a CSV file.
     *
     * @param data data to convert
     * @return data in CSV format
     */
    public static String convertToCSV(final String[] data) {
        return Stream.of(data)
                .map(CallGraphUtils::escapeSpecialCharacters)
                .collect(Collectors.joining(","));
    }

    /**
     * Write data into CSV file.
     *
     * @param data       data to write
     * @param resultPath path to write to
     * @throws IOException occurs if write is unsuccessful
     */
    public static void writeToCSV(final List<String[]> data,
                                  final String resultPath) throws IOException {
        File csvOutputFile = new File(resultPath);
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            data.stream()
                    .map(CallGraphUtils::convertToCSV)
                    .forEach(pw::println);
        }
    }

    /**
     * Escape special characters in a string.
     *
     * @param data data to escape characters in
     * @return escaped String
     */
    public static String escapeSpecialCharacters(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    public static String getString(final Map<String,
        List<Pair<String, String>>> nodePairs) {
        StringBuilder result = new StringBuilder();
        for (final var scope : nodePairs.entrySet()) {
            result.append(toStringEdges(
                nodePairs.get(scope.getKey())));
        }

        return result.toString();
    }

}
