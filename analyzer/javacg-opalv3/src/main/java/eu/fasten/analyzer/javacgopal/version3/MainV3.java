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

package eu.fasten.analyzer.javacgopal.version3;

import eu.fasten.analyzer.javacgopal.version3.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.version3.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.version3.merge.CallGraphMerger;
import eu.fasten.analyzer.javacgopal.version3.merge.CallGraphUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Makes javacg-opal module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGOpal")
public class MainV3 implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(MainV3.class);

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    Commands commands;

    @CommandLine.Option(names = {"-o", "--output"},
            paramLabel = "OUT",
            description = "Output directory path",
            defaultValue = "")
    String output;

    static class Commands {

        @CommandLine.ArgGroup(exclusive = false)
        Computations computations;

        @CommandLine.ArgGroup(exclusive = false)
        Conversions conversions;
    }

    static class Computations {

        @CommandLine.Option(names = {"-a", "--artifact"},
                paramLabel = "ARTIFACT",
                description = "Artifact, maven coordinate or file path")
        String artifact;

        @CommandLine.Option(names = {"-m", "--mode"},
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
                description = "gen{RTA,CHA,CTA,FTA,MTA,XTA,AllocationSiteBasedPointsTo,TypeBasedPointsTo}",
                defaultValue = "CHA")
        String genAlgorithm;

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

        @CommandLine.Option(names = {"-t", "--timestamp"},
                paramLabel = "TS",
                description = "Release TS",
                defaultValue = "0")
        String timestamp;

    }

    static class Merge {

        @CommandLine.Option(names = {"-ma", "--mergeAlgorithm"},
                paramLabel = "MerALG",
                description = "Algorhtm merge{RA, CHA}",
                defaultValue = "CHA")
        String mergeAlgorithm;

        @CommandLine.Option(names = {"-s", "--stitch"},
                paramLabel = "STITCH",
                description = "Stitch artifact CG to dependencies")
        boolean doMerge;

        @CommandLine.Option(names = {"-d", "--dependencies"},
                paramLabel = "DEPS",
                description = "Dependencies, coordinates or files",
                split = ",")
        List<String> dependencies;

    }

    static class Conversions {

        @CommandLine.Option(names = {"-c", "--convert"},
                paramLabel = "CON",
                description = "Convert the call graph to the specified format")
        boolean doConvert;

        @CommandLine.Option(names = {"-i", "--input"},
                paramLabel = "IN",
                description = "Path to the input call graph for conversion",
                required = true,
                split = ",")
        List<String> input;

        @CommandLine.Option(names = {"-f", "--format"},
                paramLabel = "FORMAT",
                description = "The desired format for conversion {JCG}",
                defaultValue = "JCG")
        String format;

    }

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line
     * parameters.
     */
    public static void main(String[] args) {
        new CommandLine(new MainV3()).execute(args);
    }

    public void run() {
        if (this.commands.computations != null && this.commands.computations.tools != null) {
            if (this.commands.computations.tools.opal != null
                    && this.commands.computations.tools.opal.doGenerate) {

                if (commands.computations.mode.equals("COORD")) {
                    final var artifact = getArtifactCoordinate();
                    logger.info("Generating call graph for the Maven coordinate: {}", artifact);
                    try {
                        generate(artifact, commands.computations.main, commands.computations.genAlgorithm,
                                !this.output.isEmpty());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else if (commands.computations.mode.equals("FILE")) {
                    try {
                        generate(getArtifactFile(), commands.computations.main,
                                commands.computations.genAlgorithm, !this.output.isEmpty());
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
        if (this.commands.conversions != null
                && this.commands.conversions.doConvert) {

            if (this.commands.conversions.format.equals("JCG")) {
                final var result = new JSONObject();
                var reachableMethods = new JSONArray();
                try {
                    for (final var input : this.commands.conversions.input) {
                        final var cg = new String(Files.readAllBytes((Paths.get(input))));

                        final var mergeJCG = JCGFormatV3
                                .convertERCGTOJCG(new ExtendedRevisionCallGraphV3(new JSONObject(cg))
                                );
                        if (!mergeJCG.isEmpty() && !mergeJCG.isNull("reachableMethods")) {
                            reachableMethods = concatArray(reachableMethods, (mergeJCG.getJSONArray("reachableMethods")));
                        }
                    }
                    result.put("reachableMethods", reachableMethods);
                    if (!this.output.isEmpty()) {
                        CallGraphUtils.writeToFile(this.output, result, "");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private JSONArray concatArray(JSONArray arr1, JSONArray arr2)
            throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < arr1.length(); i++) {
            result.put(arr1.get(i));
        }
        for (int i = 0; i < arr2.length(); i++) {
            result.put(arr2.get(i));
        }
        return result;
    }

    public <T> ExtendedRevisionCallGraphV3 merge(final T artifact,
                                                 final List<T> dependencies) throws IOException {

        final ExtendedRevisionCallGraphV3 result;
        final var deps = new ArrayList<ExtendedRevisionCallGraphV3>();
        for (final var dep : dependencies) {
            deps.add(generate(dep, "", commands.computations.genAlgorithm, true));
        }
        final var art = generate(artifact, this.commands.computations.main,
                commands.computations.genAlgorithm, true);

        result = CallGraphMerger.mergeCallGraph(art, deps,
                commands.computations.tools.merge.mergeAlgorithm);

        if (!this.output.isEmpty()) {
            if (result != null) {
                CallGraphUtils.writeToFile(this.output, result.toJSON(), "_" + result.product + "_merged");
            }
        }

        return result;
    }

    public <T> ExtendedRevisionCallGraphV3 generate(final T artifact,
                                                    final String mainClass,
                                                    final String algorithm, final boolean writeToFile)
            throws IOException {
        final ExtendedRevisionCallGraphV3 revisionCallGraph;

        final long startTime = System.currentTimeMillis();

        if (artifact instanceof File) {
            logger.info("Generating graph for {}", ((File) artifact).getAbsolutePath());
            final var cg = new PartialCallGraph((File) artifact, mainClass, algorithm);
            revisionCallGraph =
                    ExtendedRevisionCallGraphV3.extendedBuilderV3().graph(cg.getGraph())
                            .product(((File) artifact).getName().replace(".class", "").replace("$", ""))
                            .version("").timestamp(0).cgGenerator("").depset(new ArrayList<>()).forge("")
                            .classHierarchy(cg.getClassHierarchy()).nodeCount(cg.getNodeCount()).build();
        } else {
            revisionCallGraph =
                    PartialCallGraph.createExtendedRevisionCallGraph((MavenCoordinate) artifact, mainClass,
                            algorithm,
                            Long.parseLong(this.commands.computations.tools.opal.timestamp));
        }

        logger.info("Generated the call graph in {} seconds.",
                new DecimalFormat("#0.000").format((System.currentTimeMillis() - startTime) / 1000d));

        if (writeToFile) {
            CallGraphUtils
                    .writeToFile(this.output, revisionCallGraph.toJSON(), "_" + revisionCallGraph.product);
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
