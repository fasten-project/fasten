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

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.callgraph.ExtendedRevisionCallGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes javacg-opal module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGOpal")
public class Main implements Runnable {

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

    @CommandLine.ArgGroup(exclusive = true)
    FullCoordinate fullCoordinate;

    static class FullCoordinate {
        @CommandLine.ArgGroup(exclusive = false)
        CoordinateComponents coordinateComponents;

        @CommandLine.Option(names = {"-c", "--coord"},
            paramLabel = "COORD",
            description = "Maven coordinates string",
            required = true)
        String mavenCoordStr;
    }

    @CommandLine.ArgGroup(exclusive = true)
    MergeGenerateDiff mgd;
    static class MergeGenerateDiff{
        @CommandLine.Option(names = {"-m", "--merge"},
            paramLabel = "MERGE",
            description = "Merge artifact with the passed dependencies")
        boolean merge;
    }

    @CommandLine.Option(names = {"-t", "--timestamp"},
        paramLabel = "TS",
        description = "Release TS",
        defaultValue = "0")
    String timestamp;


    @CommandLine.Option(names = {"-d", "--dependencies"},
        paramLabel = "DEPENDENCIES",
        description = "One or more dependency coordinate to merge with the artifact")
    String[] dependencies;

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public void run() {
        NumberFormat timeFormatter = new DecimalFormat("#0.000");
        MavenCoordinate mavenCoordinate = null;
        List<MavenCoordinate> dependencies = new ArrayList<>();

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

        ExtendedRevisionCallGraph revisionCallGraph = null;
        try {
            logger.info("Generating call graph for the Maven coordinate: {}", this.fullCoordinate.mavenCoordStr);
            long startTime = System.currentTimeMillis();
            revisionCallGraph = ExtendedRevisionCallGraph.create("mvn", mavenCoordinate, Long.parseLong(this.timestamp));
            logger.info("Generated the call graph in {} seconds.", timeFormatter.format((System.currentTimeMillis() - startTime) / 1000d));
            //TODO something with the calculated RevesionCallGraph.
            System.out.println(revisionCallGraph.toJSON());

        } catch (FileNotFoundException e) {
            logger.error("Could not download the JAR file of Maven coordinate: {}", mavenCoordinate.getCoordinate());
            e.printStackTrace();
        }

    }

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line parameters.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}


