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

import eu.fasten.analyzer.javacgopal.version3.merge.CallGraphUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class Evaluator {

    public static void main(String[] args) throws IOException {

        final var resourceDir =
                new File("/Users/mehdi/Desktop/ThisPC/TUD/FASTEN/Repositories/OtherRepos/jcg/testcaseJars");
        final var JDK = "/Library/Java/JavaVirtualMachines/jdk1.8.0_221.jdk/Contents/Home/jre/";

        if (args.length != 0) {
            final var languageFeature = new File(args[0]);
            String main = extractMain(languageFeature);
            generateOpal(languageFeature, main, "RTA", "cg/opalV3");
            generateMerge(languageFeature, main, "RTA", "CHA", "cg/mergeV3");
        } else {
            final var splitJars = resourceDir.listFiles(f -> f.getPath().endsWith("_split"));
            var counter = 0;
            final var tot = splitJars.length;
            int singleClass = 0;
            for (final var langFeature : splitJars) {
                new File(langFeature.getAbsolutePath() + "/cg").mkdir();
                counter += 1;
                System.out.println("\n" +
                        "*************************" +
                        "number: " + counter + "/" + tot + " : " + langFeature.getAbsoluteFile());

                String main = extractMain(langFeature);
                generateOpal(langFeature, main, "RTA", "cg/opalV3");

                if (!generateMerge(langFeature, main, "RTA", "CHA", "cg/mergeV3")) {
                    singleClass++;
                }
            }
            System.out.println(
                    "There was " + singleClass + " number of single class language features we couldn't merge!");
        }
    }

    private static String extractMain(File langFeature) throws IOException {
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

    public static void generateOpal(File langFeature, String mainClass, String algorithm, String output) {
        final var fileName = langFeature.getName().replace(".class", "");
        final var resultGraphPath = langFeature.getAbsolutePath() + "/" + output + "_" + fileName;

        final var cgCommand = new String[]{"-g", "-a", langFeature.getAbsolutePath(), "-n", mainClass, "-ga", algorithm,
                "-m", "FILE", "-o", langFeature.getAbsolutePath() + "/" + output};

        final var convertCommand =
                new String[]{"-c", "-i", resultGraphPath, "-f", "JCG", "-o",
                        langFeature.getAbsolutePath() + "/" + output + "Jcg"};

        System.out.println("CG: " + Arrays.toString(cgCommand).replace(",", " "));
        eu.fasten.analyzer.javacgopal.version3.MainV3.main(cgCommand);
        System.out.println("Convert: " + Arrays.toString(convertCommand).replace(",", " "));
        eu.fasten.analyzer.javacgopal.version3.MainV3.main(convertCommand);

    }

    public static boolean generateMerge(final File langFeature, String main, String genAlg, String mergeAlg,
                                        final String output) {

        final var files = langFeature.listFiles(file -> file.getPath().endsWith(".class"));
        var deps = "";
        File art = new File("");
        if (files.length > 1) {
            for (int i = 0; i < files.length; i++) {
                if (!main.isEmpty()) {
                    if (files[i].getName().equals(main.split("[.]")[1] + ".class")) {
                        art = files[i];
                    } else {
                        deps = deps + files[i].getAbsolutePath() + ",";
                    }
                } else {
                    if (files[i].getName().equals("Demo.class")) {
                        art = files[i];
                    } else {
                        deps = deps + files[i].getAbsolutePath() + ",";
                    }
                }

            }
            compute(langFeature, main, output, deps, art, genAlg, mergeAlg);
            return true;
        } else {
            System.out.println(
                    "No dependency for " + langFeature.getAbsolutePath() + ", So there is no reason for merge");
            return false;
        }
    }

    private static void compute(File langFeature, String main, String output, String deps, File art, String genAlg,
                                String mergeAlg) {
        final var mergeCommand =
                new String[]{"-s", "-a", art.getAbsolutePath(), "-d", deps.replaceAll(".$", ""), "-ma", mergeAlg, "-ga",
                        genAlg, "-n", main, "-o",
                        langFeature.getAbsolutePath() + "/" + output};

        System.out.println("mergeCommand :" + Arrays.toString(mergeCommand).replace(",", " "));
        MainV3.main(mergeCommand);


        String input = "";
        final var files =
                new File(langFeature.getAbsolutePath() + "/cg")
                        .listFiles(file -> (file.getName().startsWith("mergeV3") && !file.getName().endsWith("Demo")));
        if (files.length > 1) {
            for (int i = 0; i < files.length; i++) {
                if (i == files.length - 1) {
                    input = input + files[i].getAbsolutePath();
                }else {
                    input = input + files[i].getAbsolutePath() + ",";
                }
            }
        }
        final var convertCommand = new String[]{"-c", "-i", input, "-f", "JCG", "-o",
                langFeature.getAbsolutePath() + "/" + output + "Jcg"};

        System.out.println("mergeConvert: " + Arrays.toString(convertCommand).replace(",", " "));
        MainV3.main(convertCommand);
    }
}