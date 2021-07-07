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

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.merge.CallGraphUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes javacg-opal module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGOpal", mixinStandardHelpOptions = true)
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.ArgGroup(multiplicity = "1")
    Commands commands;

    @CommandLine.Option(names = {"-o", "--output"},
            paramLabel = "OUT",
            description = "Output directory path",
            defaultValue = "")
    String output;

    @CommandLine.Option(names = {"-r"},
            paramLabel = "REPOS",
            description = "Maven repositories",
            split = ",")
    List<String> repos;

    static class Commands {
        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
        Computations computations;
    }

    static class Computations {
        @CommandLine.Option(names = {"-a", "--artifact"},
                paramLabel = "ARTIFACT",
                description = "Artifact, Maven coordinate or file path")
        String artifact;

        @CommandLine.Option(names = {"-i", "--input-type"},
                paramLabel = "MODE",
                description = "Input of algorithms are {FILE or COORD}",
                defaultValue = "FILE")
        String mode;

        @CommandLine.Option(names = {"-n", "--main"},
                paramLabel = "MAIN",
                description = "Main class of artifact")
        String main;

        @CommandLine.Option(names = {"-ga", "--genAlgorithm"},
                paramLabel = "GenALG",
                description = "gen{RTA,CHA,AllocationSiteBasedPointsTo,TypeBasedPointsTo}",
                defaultValue = "CHA")
        String genAlgorithm;

        @CommandLine.ArgGroup(multiplicity = "1")
        Tools tools;

        @CommandLine.Option(names = {"-t", "--timestamp"},
                paramLabel = "TS",
                description = "Release TS",
                defaultValue = "0")
        String timestamp;
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
        @CommandLine.Option(names = {"-m", "--merge"},
                paramLabel = "MERGE",
                description = "Merge artifact CG to dependencies",
                required = true)
        boolean doMerge;

        @CommandLine.Option(names = {"-d", "--dependencies"},
                paramLabel = "DEPS",
                description = "Dependencies, coordinates or files",
                split = ",")
        List<String> dependencies;
    }

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line
     * parameters.
     */
    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }

    /**
     * Run the generator, merge algorithm or evaluator depending on parameters provided.
     */
    public void run() {
        if (this.commands.computations != null && this.commands.computations.tools != null) {
            if (this.commands.computations.tools.opal != null
                    && this.commands.computations.tools.opal.doGenerate) {
                runGenerate();
            }
            if (this.commands.computations.tools.merge != null
                    && this.commands.computations.tools.merge.doMerge) {
                runMerge();
            }
        }
    }

    /**
     * Run call graph generator.
     */
    private void runGenerate() {
        if (commands.computations.mode.equals("COORD")) {
            final var artifact = getArtifactCoordinate();
            logger.info("Generating call graph for the Maven coordinate: {}",
                    artifact.getCoordinate());
            try {
                generate(artifact, commands.computations.main, commands.computations.genAlgorithm,
                        !this.output.isEmpty());
            } catch (IOException | OPALException | MissingArtifactException e) {
                logger.error("Call graph couldn't be generated for Maven coordinate: {}", artifact.getCoordinate(), e);
            }

        } else if (commands.computations.mode.equals("FILE")) {
            try {
                generate(getArtifactFile(), commands.computations.main,
                        commands.computations.genAlgorithm, !this.output.isEmpty());
            } catch (IOException | OPALException | MissingArtifactException e) {
                logger.error("Call graph couldn't be generated for file: {}", getArtifactFile().getName(), e);
            }
        }
    }

    /**
     * Run merge algorithm.
     */
    private void runMerge() {
        if (commands.computations.mode.equals("COORD")) {
            try {
                merge(getArtifactCoordinate(), getDependenciesCoordinates());
            } catch (IOException | OPALException | MissingArtifactException e) {
                logger.error("Call graph couldn't be merge for coord: {}", getArtifactCoordinate().getCoordinate(), e);
            }

        } else if (commands.computations.mode.equals("FILE")) {
            try {
                merge(getArtifactFile(), getDependenciesFiles());
            } catch (IOException | OPALException | MissingArtifactException e) {
                logger.error("Call graph couldn't be generated for file: {}", getArtifactFile().getName(), e);
            }
        }
    }

    /**
     * Merge an artifact with a list of it's dependencies using a specified algorithm.
     *
     * @param artifact     artifact to merge
     * @param dependencies list of dependencies
     * @param <T>          artifact can be either a file or coordinate
     * @return a revision call graph with resolved class hierarchy and calls
     * @throws IOException thrown in case file related exceptions occur, e.g FileNotFoundException
     */
    public <T> DirectedGraph merge(final T artifact,
                                                   final List<T> dependencies)
            throws IOException, OPALException, MissingArtifactException {
        final long startTime = System.currentTimeMillis();
        final DirectedGraph result;
        final var deps = new ArrayList<ExtendedRevisionJavaCallGraph>();
        for (final var dep : dependencies) {
            deps.add(generate(dep, "", commands.computations.genAlgorithm, true));
        }
        final var art = generate(artifact, this.commands.computations.main,
                commands.computations.genAlgorithm, true);
        deps.add(art);
        final var merger = new CGMerger(deps);
        result = merger.mergeWithCHA(art);

        if (result != null) {
            logger.info("Resolved {} nodes, {} calls in {} seconds",
                    result.nodes().size(),
                    result.edgeSet().size(),
                    new DecimalFormat("#0.000")
                            .format((System.currentTimeMillis() - startTime) / 1000d));
            if (!this.output.isEmpty()) {
                try {
                    CallGraphUtils.writeToFile(Paths.get(Paths.get(this.output).getParent().toString(),
                            FilenameUtils.getBaseName(this.output) + "_" + getArtifactCoordinate().getCoordinate() + "_merged" + "." +
                                    FilenameUtils.getExtension(this.output)).toString(), JSONUtils.toJSONString(result, getArtifactCoordinate()), "");
                } catch (NullPointerException e) {
                    logger.error("Provided output path might be incomplete!");
                }
            }
        }

        return result;
    }

    /**
     * Generate a revision call graph for a given coordinate using a specified algorithm. In case
     * the artifact is an application a main class can be specified. If left empty a library entry
     * point finder algorithm will be used.
     *
     * @param artifact    artifact to generate a call graph for
     * @param mainClass   main class in case the artifact is an application
     * @param algorithm   algorithm for generating a call graph
     * @param writeToFile will be written to a file if true
     * @param <T>         artifact can be either a file or a coordinate
     * @return generated revision call graph
     * @throws IOException file related exceptions, e.g. FileNotFoundException
     */
    public <T> ExtendedRevisionJavaCallGraph generate(final T artifact,
                                                      final String mainClass,
                                                      final String algorithm, final boolean writeToFile)
            throws MissingArtifactException, OPALException, IOException {
        final ExtendedRevisionJavaCallGraph revisionCallGraph;

        final long startTime = System.currentTimeMillis();

        if (artifact instanceof File) {
            logger.info("Generating graph for {}", ((File) artifact).getAbsolutePath());
            final var cg = new PartialCallGraph(
                    new CallGraphConstructor((File) artifact, mainClass, algorithm));
            revisionCallGraph =
                    ExtendedRevisionJavaCallGraph.extendedBuilder().graph(cg.getGraph())
                            .product(cleanUpFileName((File) artifact))
                            .version("").timestamp(0).cgGenerator("").forge("")
                            .classHierarchy(cg.getClassHierarchy()).nodeCount(cg.getNodeCount())
                            .build();

        } else {
            revisionCallGraph = PartialCallGraph
                    .createExtendedRevisionJavaCallGraph((MavenCoordinate) artifact, mainClass,
                            algorithm, Long.parseLong(this.commands.computations.timestamp),
                            (repos == null || repos.size() < 1) ? MavenUtilities.MAVEN_CENTRAL_REPO : repos.get(0),
                            false);
        }

        logger.info("Generated the call graph in {} seconds.", new DecimalFormat("#0.000")
                .format((System.currentTimeMillis() - startTime) / 1000d));

        if (writeToFile) {
            CallGraphUtils.writeToFile(this.output, JSONUtils.toJSONString(revisionCallGraph), "");

        }
        return revisionCallGraph;
    }

    private String cleanUpFileName(File artifact) {
        return artifact.getName().replace(".class", "").replace("$", "").replace(".jar", "");
    }

    /**
     * Get a list of files of dependencies.
     *
     * @return a list of dependencies files
     */
    private List<File> getDependenciesFiles() {
        final var result = new ArrayList<File>();
        if (this.commands.computations.tools.merge.dependencies != null) {
            for (String currentCoordinate : this.commands.computations.tools.merge.dependencies) {
                result.add(new File(currentCoordinate));
            }
        }
        return result;
    }

    /**
     * Get a list of coordinates of dependencies.
     *
     * @return a list of Maven coordinates
     */
    private List<MavenCoordinate> getDependenciesCoordinates() {
        final var result = new ArrayList<MavenCoordinate>();
        if (this.commands.computations.tools.merge.dependencies != null) {
            for (String currentCoordinate : this.commands.computations.tools.merge.dependencies) {
                var coordinate = MavenCoordinate.fromString(currentCoordinate, "jar");
                if (this.repos != null && !this.repos.isEmpty()) {
                    coordinate.setMavenRepos(this.repos);
                }
                result.add(coordinate);
            }
        }
        return result;
    }

    /**
     * Get an artifact file from a provided path.
     *
     * @return artifact file
     */
    private File getArtifactFile() {
        File result = null;
        if (this.commands.computations.artifact != null) {
            result = new File(this.commands.computations.artifact);
        }
        return result;
    }

    /**
     * Get an artifact coordinate for an artifact.
     *
     * @return artifact coordinate
     */
    private MavenCoordinate getArtifactCoordinate() {
        MavenCoordinate result = null;
        if (this.commands.computations.artifact != null) {
            result = MavenCoordinate.fromString(this.commands.computations.artifact, "jar");
            if (this.repos != null && !this.repos.isEmpty()) {
                result.setMavenRepos(this.repos);
            }
        }
        return result;
    }
}
