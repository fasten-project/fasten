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

package eu.fasten.analyzer.javacgwala;

import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Makes javacg-wala module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGWala")
public class Main implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.ArgGroup()
    SetRunner setRunner;

    @CommandLine.Option(names = {"-t", "--timestamp"},
            paramLabel = "TS",
            description = "Release TS",
            defaultValue = "0")
    String timestamp;

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line
     * parameters.
     */
    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Runs Wala plugin.
     */
    public void run() {
        MavenCoordinate mavenCoordinate;

        if (setRunner.set != null) {
            consumeSet(setRunner.set);
        } else {
            if (this.setRunner.fullCoordinate.mavenCoordStr != null) {
                mavenCoordinate = MavenCoordinate
                        .fromString(this.setRunner.fullCoordinate.mavenCoordStr);
            } else {
                mavenCoordinate = new MavenCoordinate(
                        this.setRunner.fullCoordinate.coordinateComponents.group,
                        this.setRunner.fullCoordinate.coordinateComponents.artifact,
                        this.setRunner.fullCoordinate.coordinateComponents.version);
            }

            try {
                final var revisionCallGraph = PartialCallGraph.createExtendedRevisionCallGraph(
                        mavenCoordinate,
                        Long.parseLong(this.timestamp));

                System.out.println(revisionCallGraph.toJSON());

            } catch (Throwable e) {
                logger.error("Failed to generate a call graph for Maven coordinate: {}, Error: {}",
                        mavenCoordinate.getCoordinate(), e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Consume a set of maven coordinates and generate call graphs for them.
     *
     * @param path Path to the file containing maven coordinates.
     */
    private void consumeSet(String path) {
        List<String> successfulRecords = new ArrayList<>();
        Map<String, String> failedRecords = new HashMap<>();
        Map<String, Integer> errorOccurrences = new HashMap<>();

        for (var coordinate : getCoordinates(path)) {
            final var mavenCoordinate = getMavenCoordinate(coordinate);
            assert mavenCoordinate != null;

            try {
                var cg = PartialCallGraph.createExtendedRevisionCallGraph(
                        mavenCoordinate,
                        Long.parseLong(coordinate.get("date").toString()));

                int totalCalls = cg.getGraph().getUnresolvedCalls().size()
                        + cg.getGraph().getResolvedCalls().size();

                successfulRecords.add("Number of calls: " + totalCalls
                        + " COORDINATE: " + mavenCoordinate.getCoordinate());

                logger.info("Call graph successfully generated for {}!",
                        mavenCoordinate.getCoordinate());

            } catch (Throwable e) {
                JSONObject error = new JSONObject().put("plugin", this.getClass().getSimpleName())
                        .put("msg", e.getMessage())
                        .put("trace", e.getStackTrace())
                        .put("type", e.getClass().getSimpleName());

                String errorType = error.get("type").toString();

                failedRecords.put(mavenCoordinate.getCoordinate(), errorType);


                if (errorOccurrences.containsKey(errorType)) {
                    errorOccurrences.put(errorType, errorOccurrences.get(errorType) + 1);
                } else {
                    errorOccurrences.put(errorType, 1);
                }

                logger.info("Failed to generate a call graph for {}!",
                        mavenCoordinate.getCoordinate());
            }
        }

        printStats(successfulRecords, failedRecords, errorOccurrences);
    }

    /**
     * Print statistics of call graph generation.
     *
     * @param successfulRecords Records that were successfully processed
     * @param failedRecords     Failed records
     * @param errorOccurrences  Map of error and number of their occurrences
     */
    private void printStats(List<String> successfulRecords, Map<String, String> failedRecords,
                            Map<String, Integer> errorOccurrences) {
        for (var record : successfulRecords) {
            System.out.println(record);
        }

        for (var record : failedRecords.entrySet()) {
            System.out.println(record.getKey() + " ERROR: " + record.getValue());
        }

        int total = successfulRecords.size() + failedRecords.size();

        System.out.println();
        System.out.println("===================SUMMARY=================");
        System.out.println("Total number of analyzed coordinates: \t" + total);
        System.out.println("Total number of successful: \t\t\t" + successfulRecords.size());
        System.out.println("Total number of failed: \t\t\t\t" + failedRecords.size());
        System.out.println("Most common exceptions: ");

        var sortedErrorMap = errorOccurrences.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, HashMap::new));

        for (var entry : sortedErrorMap.entrySet()) {
            System.out.println("\t [" + entry.getKey() + " - " + entry.getValue() + "]");
        }

        System.out.println("Success rate: \t\t\t\t\t\t\t"
                + 100 * successfulRecords.size() / total + "%");
    }

    /**
     * Parse JSON representation of MavenCoordinate.
     *
     * @param kafkaConsumedJson Json maven coordinate
     * @return MavenCoordinate
     */
    private MavenCoordinate getMavenCoordinate(final JSONObject kafkaConsumedJson) {

        try {
            return new MavenCoordinate(
                    kafkaConsumedJson.get("groupId").toString(),
                    kafkaConsumedJson.get("artifactId").toString(),
                    kafkaConsumedJson.get("version").toString());
        } catch (JSONException e) {
            logger.error("Could not parse input coordinates: {}\n{}", kafkaConsumedJson, e);
        }
        return null;
    }

    /**
     * Process the file containing maven coordinates and convert it to list of JSON maven
     * coordinates.
     *
     * @param path Path to the file
     * @return List of Json objects
     */
    private List<JSONObject> getCoordinates(String path) {
        try {
            File file = new File(path);

            BufferedReader br = new BufferedReader(new FileReader(file));
            return br.lines()
                    .map(x -> x.substring(0, x.indexOf("url") - 2) + "}")
                    .map(JSONObject::new)
                    .collect(Collectors.toList());


        } catch (IOException e) {
            System.out.println("Couldn't parse a file with coordinates");
        }

        return new ArrayList<>();
    }

    static class CoordinateComponents {
        @CommandLine.Option(names = {"-g", "--group"},
                paramLabel = "GROUP",
                description = "Maven group id",
                required = true)
        String group;

        @CommandLine.Option(names = {"-a", "--artifact"},
                paramLabel = "ARTIFACT",
                description = "Maven artifact id",
                required = true)
        String artifact;

        @CommandLine.Option(names = {"-v", "--version"},
                paramLabel = "VERSION",
                description = "Maven version id",
                required = true)
        String version;
    }

    static class FullCoordinate {
        @CommandLine.ArgGroup(exclusive = false)
        CoordinateComponents coordinateComponents;

        @CommandLine.Option(names = {"-c", "--coord"},
                paramLabel = "COORD",
                description = "Maven coordinates string",
                required = true)
        String mavenCoordStr;
    }

    static class SetRunner {
        @CommandLine.ArgGroup()
        FullCoordinate fullCoordinate;

        @CommandLine.Option(names = {"-s", "--set"},
                paramLabel = "Set",
                description = "Set of maven coordinates",
                required = true)
        String set;
    }
}


