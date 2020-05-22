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
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.merge.CallGraphMerger;
import eu.fasten.analyzer.javacgopal.merge.CallGraphUtils;
import eu.fasten.core.data.RevisionCallGraph;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
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

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    Commands commands;

    @CommandLine.Option(names = {"-o", "--output"},
        paramLabel = "OUT",
        description = "Output directory path")
    String output;

    static class Commands {

        @CommandLine.ArgGroup(exclusive = false)
        Computations computations;

    }

    static class Computations {

        @CommandLine.Option(names = {"-a", "--artifact"},
            paramLabel = "ARTIFACT",
            description = "Artifact, maven coordinate or file path")
        String artifact;

        @CommandLine.Option(names = {"-t", "--timestamp"},
            paramLabel = "TS",
            description = "Release TS",
            defaultValue = "0")
        String timestamp;
        @CommandLine.Option(names = {"-m", "--mode"},
            paramLabel = "MODE",
            description = "Input of algorithms are {FILE or COORD}",
            defaultValue = "FILE")
        String mode;

        @CommandLine.ArgGroup(exclusive = true)
        Tools tools;
    }

    static class Tools {

        @CommandLine.ArgGroup(exclusive = false)
        Opal opal;

        @CommandLine.ArgGroup(exclusive = false)
        Merge merge;
    }

    static class Opal {

        @CommandLine.Option(names = {"-g", "--generate"},
            paramLabel = "GEN",
            description = "Generate call graph for artifact")
        boolean doGenerate;


    }

    static class Merge {

        @CommandLine.Option(names = {"-s", "--stitch"},
            paramLabel = "STITCH",
            description = "Stitch artifact CG to dependencies")
        boolean doMerge;

        @CommandLine.Option(names = {"-d", "--dependencies"},
            paramLabel = "DEPS",
            description = "Dependencies, coordinates or files",
            split = ",")
        List<String> dependencies;

        @CommandLine.Option(names = {"-l", "--algorithm"},
            paramLabel = "ALG",
            description = "Merge Tools {RA or CHA}",
            defaultValue = "CHA")
        String algorithm;

    }

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line
     * parameters.
     */
    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }

    public void run() {
        if (this.commands.computations != null && this.commands.computations.tools != null) {
            if (this.commands.computations.tools.opal != null
                && this.commands.computations.tools.opal.doGenerate) {

                if (commands.computations.mode.equals("COORD")) {
                    final var artifact = getArtifactCoordinate();
                    logger.info("Generating call graph for the Maven coordinate: {}", artifact);
                    try {
                        if(this.output!=null) {
                            generate(artifact, true);
                        }else {
                            System.out.println(generate(artifact, false).toJSON().toString(4));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else if (commands.computations.mode.equals("FILE")) {
                    try {
                        if(this.output!=null) {
                            generate(getArtifactFile(), true);
                        }else {
                            System.out.println(generate(getArtifactFile(), false).toJSON().toString(4));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
            if (this.commands.computations.tools.merge != null
                && this.commands.computations.tools.merge.doMerge) {

                if (commands.computations.mode.equals("COORD")) {
                    try {
                        merge(getArtifactCoordinate(), getDependenciesCoordinates());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else if (commands.computations.mode.equals("FILE")) {
                    try {
                        merge(getArtifactFile(), getDependenciesFiles()).toJSON();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }

    }

    public <T> RevisionCallGraph merge(final T artifact,
                                               final List<T> dependencies) throws IOException {

        final RevisionCallGraph result;
        final var deps = new ArrayList<RevisionCallGraph>();
        for (final var dep : dependencies) {
            deps.add(generate(dep, false));
        }
        final var art = generate(artifact, false);
        result = CallGraphMerger.mergeCallGraph(art, deps,
            this.commands.computations.tools.merge.algorithm);

        if (this.output != null) {
            if (result != null) {
                CallGraphUtils.writeToFile(this.output, result.toJSON().toString(4), "");
            }
        }else {
            System.out.println(result.toJSON().toString(4));
        }

        return result;
    }

    public <T> RevisionCallGraph generate(final T artifact, final boolean writeToFile)
        throws IOException {
        final RevisionCallGraph revisionCallGraph;

        final long startTime = System.currentTimeMillis();

        if (artifact instanceof File) {
            logger.info("Generating graph for {}", ((File) artifact).getAbsolutePath());
            final var cg = new PartialCallGraph((File) artifact);
            revisionCallGraph =
                RevisionCallGraph.extendedBuilder().graph(cg.getGraph())
                    .product(((File) artifact).getName().replace(".class", "").replace("$",""))
                    .version("").timestamp(0).cgGenerator("").depset(new ArrayList<>()).forge("")
                    .classHierarchy(cg.getClassHierarchy()).build();
        } else {
            revisionCallGraph =
                PartialCallGraph.createExtendedRevisionCallGraph((MavenCoordinate) artifact,
                    Long.parseLong(this.commands.computations.timestamp));

        }

        logger.info("Generated the call graph in {} seconds.",
            new DecimalFormat("#0.000").format((System.currentTimeMillis() - startTime) / 1000d));

        if (writeToFile) {
            CallGraphUtils
                .writeToFile(this.output, revisionCallGraph.toJSON().toString(4), "");
        }
        return revisionCallGraph;
    }

    private List<File> getDependenciesFiles() {
        final var result = new ArrayList<File>();
        if (this.commands.computations.tools.merge.dependencies != null) {
            for (String currentCoordinate : this.commands.computations.tools.merge.dependencies) {
                result.add(new File(currentCoordinate));
            }
        }
        return result;
    }

    private List<MavenCoordinate> getDependenciesCoordinates() {
        final var result = new ArrayList<MavenCoordinate>();
        if (this.commands.computations.tools.merge.dependencies != null) {
            for (String currentCoordinate : this.commands.computations.tools.merge.dependencies) {
                result.add(MavenCoordinate.fromString(currentCoordinate));
            }
        }
        return result;
    }

    private File getArtifactFile() {
        File result = null;
        if (this.commands.computations.artifact != null) {
            result = new File(this.commands.computations.artifact);
        }
        return result;
    }

    private MavenCoordinate getArtifactCoordinate() {
        MavenCoordinate result = null;
        if (this.commands.computations.artifact != null) {
            result = MavenCoordinate.fromString(this.commands.computations.artifact);
        }
        return result;
    }

}
