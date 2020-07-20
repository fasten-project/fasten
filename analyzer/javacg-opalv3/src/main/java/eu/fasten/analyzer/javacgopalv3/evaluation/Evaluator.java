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

package eu.fasten.analyzer.javacgopalv3.evaluation;

import eu.fasten.analyzer.javacgopalv3.Main;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jooq.tools.csv.CSVReader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Evaluator {

    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);

    public static void generateAllFeatures(final File testCasesDirectory) throws IOException {
        final var splitJars = testCasesDirectory.listFiles(f -> f.getPath().endsWith("_split"));
        var counter = 0;
        assert splitJars != null;
        final var tot = splitJars.length;
        int singleClass = 0;
        for (final var langFeature : splitJars) {
            new File(langFeature.getAbsolutePath() + "/cg").mkdir();
            counter += 1;
            logger.info("\n Processing {} -> {}", langFeature.getName(), counter + "/" + tot);
            final String main = extractMain(langFeature);
            generateOpal(langFeature, main, "RTA", "cg/opalV3");

            if (!merge(langFeature, main, "RTA", "CHA", "cg/mergeV3")) {
                singleClass++;
            }
        }
        logger
            .info("There was #{} single class language features we couldn't merge! ", singleClass);
    }

    public static void generateSingleFeature(final File testCaseDirectory) throws IOException {
        final String main = extractMain(testCaseDirectory);
        generateOpal(testCaseDirectory, main, "RTA", "cg/opalV3");
        merge(testCaseDirectory, main, "RTA", "CHA", "cg/mergeV3");
    }

    public static void main(String[] args) throws IOException {

        if (args[0].equals("--RQ3single")) {
            generateSingleFeature(new File(args[1]));
        } else if (args[0].equals("--RQ3All")){
            generateAllFeatures(new File(args[1]));
        }else if (args[0].equals("--RQ1")){
            evaluatePerformance(dropTheHeader(readCSV(args[1])));
        }
    }

    private static List<String> dropTheHeader(final List<String> csv) {
        csv.remove(0);
        return csv;
    }

    private static List<String> readCSV(final String revisions) throws IOException {
        List<String> coords = new ArrayList<>();
        try (final var csvReader = new CSVReader(new FileReader(revisions))) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                coords.add(Arrays.asList(values).get(0));
            }
        }
        return coords;
    }

    private static void evaluatePerformance(final List<String> coords) throws IOException {
        for (final String coord : coords) {
            final var files = Maven.resolver().resolve(coord).withoutTransitivity().asFile();
            final var mainClass = new Main();
            new File("RCGs").mkdir();
            for (final var file : files) {
                final long startTime = System.currentTimeMillis();
                mainClass.setOutput("RCGs/" + coord);
                mainClass.generate(file,"", "RTA", true);
                new DecimalFormat("#0.000").format((System.currentTimeMillis() - startTime) / 1000d);
            }

        }
    }

    private static String extractMain(final File langFeature) throws IOException {
        final var conf = new String(Files.readAllBytes(
            (Paths.get(langFeature.getAbsolutePath().replace(".jar_split", "").concat(".conf")))));
        final var jsObject = new JSONObject(conf);

        String main = "";
        if (jsObject.has("main")) {
            main = jsObject.getString("main");
        }

        if (main != null) {
            main = main.replace("\"", "");
        }
        return main;
    }

    public static void generateOpal(final File langFeature, final String mainClass,
                                    final String algorithm,
                                    final String output) {
        final var fileName = langFeature.getName().replace(".class", "");
        final var resultGraphPath = langFeature.getAbsolutePath() + "/" + output;
        final var cgCommand =
            new String[] {"-g", "-a", langFeature.getAbsolutePath(), "-n", mainClass, "-ga",
                algorithm, "-m", "FILE", "-o", resultGraphPath};
        final var convertCommand = new String[] {"-c", "-i", resultGraphPath + "_" + fileName, "-f",
            "JCG",
            "-o",
            langFeature.getAbsolutePath() + "/" + output + "Jcg"};
        logger.info("CG Command: {}", Arrays.toString(cgCommand).replace(",", " "));
        Main.main(cgCommand);
        logger.info("Convert Command: {}", Arrays.toString(convertCommand).replace(",", " "));
        Main.main(convertCommand);

    }

    public static boolean merge(final File langFeature, final String main, final String genAlg,
                                final String mergeAlg,
                                final String output) {

        final var files = langFeature.listFiles(file -> file.getPath().endsWith(".class"));
        assert files != null;
        if (files.length > 1) {
            compute(langFeature, main, output, createArtDep(main, files), genAlg, mergeAlg);
            return true;
        } else {
            logger.info("No dependency, no merge for {}", langFeature.getAbsolutePath());
            return false;
        }
    }

    private static String[] createArtDep(final String main, final File[] files) {

        String art = "";
        final StringBuilder deps = new StringBuilder();
        for (final File file : files) {
            if (!main.isEmpty()) {
                if (file.getName().equals(main.split("[.]")[1] + ".class")) {
                    art = file.getAbsolutePath();
                } else {
                    deps.append(file.getAbsolutePath()).append(",");
                }
            } else {
                if (file.getName().contains("Demo.class")) {
                    art = file.getAbsolutePath();
                } else {
                    deps.append(file.getAbsolutePath()).append(",");
                }
            }

        }
        return new String[]{"-a", art , "-d", deps.toString().replaceAll(".$", "")};
    }

    private static void compute(final File langFeature, final String main, final String output,
                                final String[] artDeps, final String genAlg,
                                final String mergeAlg) {
        var mergeCommand = new String[] {"-s", "-ma", mergeAlg, "-ga", genAlg, "-n", main, "-o", langFeature.getAbsolutePath() + "/" + output};
        mergeCommand = ArrayUtils.addAll(mergeCommand, artDeps);

        logger.info("Merge Command: {}", Arrays.toString(mergeCommand).replace(",", " "));
        Main.main(mergeCommand);

        StringBuilder input = new StringBuilder();
        final var files = new File(langFeature.getAbsolutePath() + "/cg")
            .listFiles(
                file -> (file.getName().startsWith("mergeV3") && !file.getName().endsWith("Demo")));

        assert files != null;
        if (files.length > 1) {
            for (int i = 0; i < files.length; i++) {
                if (i == files.length - 1) {
                    input.append(files[i].getAbsolutePath());
                } else {
                    input.append(files[i].getAbsolutePath()).append(",");
                }
            }
        }
        final var convertCommand = new String[] {"-c", "-i", input.toString(), "-f", "JCG", "-o",
            langFeature.getAbsolutePath() + "/" + output + "Jcg"};

        logger.info("Merge Convert Command: {}", Arrays.toString(convertCommand).replace(",", " "));
        Main.main(convertCommand);
    }
}