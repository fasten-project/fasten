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

/**
 * Makes javacg-opal module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGOpal")
public class Main implements Runnable {

    static class Dependent {
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
    Exclusive exclusive;

    static class Exclusive {
        @CommandLine.ArgGroup(exclusive = false)
        Dependent mavencoords;

        @CommandLine.Option(names = {"-c", "--coord"},
            paramLabel = "COORD",
            description = "Maven coordinates string",
            required = true)
        String mavenCoordStr;
    }

    @CommandLine.Option(names = {"-t", "--timestamp"},
        paramLabel = "TS",
        description = "Release TS",
        defaultValue = "0")
    String timestamp;

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public void run() {
        NumberFormat timeFormatter = new DecimalFormat("#0.000");
        MavenCoordinate mavenCoordinate = null;

        if (this.exclusive.mavenCoordStr != null) {
            mavenCoordinate = MavenCoordinate.fromString(this.exclusive.mavenCoordStr);
        } else {
            mavenCoordinate = new MavenCoordinate(this.exclusive.mavencoords.group,
                this.exclusive.mavencoords.artifact,
                this.exclusive.mavencoords.version);
        }

        ExtendedRevisionCallGraph revisionCallGraph = null;
        try {
            logger.info("Generating call graph for the Maven coordinate: {}", this.exclusive.mavenCoordStr);
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


