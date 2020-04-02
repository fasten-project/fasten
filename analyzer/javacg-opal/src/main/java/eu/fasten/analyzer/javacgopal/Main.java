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

import eu.fasten.analyzer.baseanalyzer.MavenCoordinate;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.merge.CallGraphDifferentiator;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Makes javacg-opal module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGOpal")
public class Main implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(Main.class);
    @CommandLine.ArgGroup(exclusive = true)
    FullCoordinate fullCoordinate;
    @CommandLine.ArgGroup(exclusive = true)
    MergeGenerateDiff mgd;
    @CommandLine.Option(names = {"-t", "--timestamp"},
        paramLabel = "TS",
        description = "Release TS",
        defaultValue = "0")
    String timestamp;
    @CommandLine.Option(names = {"-d", "--dependencies"},
        paramLabel = "DEPENDENCIES",
        description = "One or more dependency coordinate to merge with the artifact")
    String[] dependencies;

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line
     * parameters.
     */
    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    public void run() {
        final NumberFormat timeFormatter = new DecimalFormat("#0.000");
        final MavenCoordinate mavenCoordinate;
        final List<MavenCoordinate> dependencies = new ArrayList<>();

        if (this.fullCoordinate.mavenCoordStr != null) {
            mavenCoordinate = MavenCoordinate.fromString(this.fullCoordinate.mavenCoordStr);
        } else {
            mavenCoordinate = new MavenCoordinate(this.fullCoordinate.coordinateComponents.group,
                this.fullCoordinate.coordinateComponents.artifact,
                this.fullCoordinate.coordinateComponents.version);
        }

        if (this.dependencies != null) {
            for (String currentCoordinate : this.dependencies) {
                dependencies.add(MavenCoordinate.fromString(currentCoordinate));
            }
        }

        final ExtendedRevisionCallGraph revisionCallGraph;
        try {
            logger.info("Generating call graph for the Maven coordinate: {}",
                this.fullCoordinate.mavenCoordStr);
            long startTime = System.currentTimeMillis();
            revisionCallGraph = PartialCallGraph
                .createExtendedRevisionCallGraph(mavenCoordinate, Long.parseLong(this.timestamp));
            logger.info("Generated the call graph in {} seconds.",
                timeFormatter.format((System.currentTimeMillis() - startTime) / 1000d));
            //TODO something with the calculated RevesionCallGraph.
            CallGraphDifferentiator
                .writeToFile("", revisionCallGraph.toJSON().toString(4), "graph");


        } catch (IOException e) {
            logger.error("Could not download the JAR file of Maven coordinate: {}",
                mavenCoordinate.getCoordinate());
            e.printStackTrace();
        }

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

    static class MergeGenerateDiff {
        @CommandLine.Option(names = {"-m", "--merge"},
            paramLabel = "MERGE",
            description = "Merge artifact with the passed dependencies")
        boolean merge;
    }
}


