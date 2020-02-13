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
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import java.io.FileNotFoundException;
import picocli.CommandLine;

@CommandLine.Command(name = "JavaCGWala")
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

    @CommandLine.ArgGroup()
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

    /**
     * Runs Wala Analyzer.
     */
    public void run() {
        MavenCoordinate mavenCoordinate;
        if (this.exclusive.mavenCoordStr != null) {
            mavenCoordinate = MavenCoordinate.of(this.exclusive.mavenCoordStr);
        } else {
            mavenCoordinate = new MavenCoordinate(this.exclusive.mavencoords.group,
                    this.exclusive.mavencoords.artifact,
                    this.exclusive.mavencoords.version);
        }

        PartialCallGraph revisionCallGraph = null;
        try {
            revisionCallGraph = CallGraphConstructor.build(mavenCoordinate.getCanonicalForm());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        assert revisionCallGraph != null;
        System.out.println(revisionCallGraph.toRevisionCallGraph(0).toJSON());
    }

    /**
     * Generates RevisionCallGraphs using Wala for the specified artifact
     * in the command line parameters.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
