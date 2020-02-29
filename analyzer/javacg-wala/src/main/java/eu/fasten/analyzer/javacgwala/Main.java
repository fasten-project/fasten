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
import eu.fasten.analyzer.javacgwala.data.callgraph.ExtendedRevisionCallGraph;
import java.io.FileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Makes javacg-wala module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGWala")
public class Main implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(Main.class);
    @CommandLine.ArgGroup(exclusive = true)
    FullCoordinate fullCoordinate;

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
        final MavenCoordinate mavenCoordinate;

        if (this.fullCoordinate.mavenCoordStr != null) {
            mavenCoordinate = MavenCoordinate.fromString(this.fullCoordinate.mavenCoordStr);
        } else {
            mavenCoordinate = new MavenCoordinate(this.fullCoordinate.coordinateComponents.group,
                    this.fullCoordinate.coordinateComponents.artifact,
                    this.fullCoordinate.coordinateComponents.version);
        }


        try {
            final var revisionCallGraph = ExtendedRevisionCallGraph
                    .create(mavenCoordinate, Long.parseLong(this.timestamp));

            System.out.println(revisionCallGraph.toJSON());

        } catch (FileNotFoundException e) {
            logger.error("Could not download the JAR file of Maven coordinate: {}",
                    mavenCoordinate.getCoordinate());
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
}


