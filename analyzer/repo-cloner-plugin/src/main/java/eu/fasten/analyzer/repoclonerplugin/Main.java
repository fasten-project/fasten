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

package eu.fasten.analyzer.repoclonerplugin;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Scanner;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "RepoCloner")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "JSON_FILE",
            description = "Path to JSON file which contains repoUrl and artifactId, "
                    + "groupId and optional version")
    String jsonFile;

    @CommandLine.Option(names = {"-l", "--url-list"},
            paramLabel = "FILE_WITH_URLS",
            description = "Path to file which contains a list of repository URLs to clone")
    String urlsFile;

    @CommandLine.Option(names = {"-u", "--url"},
            paramLabel = "REPO_URL",
            description = "URL of the repository to clone")
    String repoUrl;


    @CommandLine.Option(names = {"-a", "--artifactId"},
            paramLabel = "ARTIFACT",
            description = "artifactId of the Maven coordinate",
            defaultValue = "n/a")
    String artifact;

    @CommandLine.Option(names = {"-g", "--groupId"},
            paramLabel = "GROUP",
            description = "groupId of the Maven coordinate",
            defaultValue = "n/a")
    String group;

    @CommandLine.Option(names = {"-v", "--version"},
            paramLabel = "VERSION",
            description = "version of the Maven coordinate",
            defaultValue = "n/a")
    String version;


    @CommandLine.Option(names = {"-d", "--base-dir"},
            paramLabel = "Directory",
            description = "Path to base directory where repository hierarchy will be created")
    String baseDir;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var repoCloner = new RepoClonerPlugin.RepoCloner();
        repoCloner.setBaseDir(baseDir);
        if (repoUrl != null) {
            var json = new JSONObject();
            json.put("artifactId", artifact);
            json.put("groupId", group);
            json.put("version", version);
            json.put("repoUrl", repoUrl);
            repoCloner.consume(json.toString());
            var resultOptional = repoCloner.produce();
            resultOptional.ifPresent(System.out::println);
            return;
        }
        if (jsonFile != null) {
            final FileReader reader;
            try {
                reader = new FileReader(jsonFile);
            } catch (FileNotFoundException e) {
                logger.error("Could not find input JSON " + jsonFile, e);
                return;
            }
            final JSONObject json = new JSONObject(new JSONTokener(reader));
            repoCloner.consume(json.toString());
            var resultOptional = repoCloner.produce();
            resultOptional.ifPresent(System.out::println);
            return;
        }
        if (urlsFile != null) {
            Scanner reader;
            try {
                reader = new Scanner(new FileInputStream(urlsFile));
            } catch (FileNotFoundException e) {
                logger.error("Could not find input file " + urlsFile, e);
                return;
            }
            var successful = 0F;
            var total = 0F;
            var failedUrls = new ArrayList<String>();
            var emptyOutputUrls = new ArrayList<String>();
            while (reader.hasNextLine()) {
                total++;
                var url = reader.nextLine();
                var json = new JSONObject();
                json.put("artifactId", String.valueOf((int) total % 10));
                json.put("groupId", String.valueOf((int) total % 10));
                json.put("version", String.valueOf((int) total % 10));
                json.put("repoUrl", url);
                repoCloner.consume(json.toString());
                var resultOptional = repoCloner.produce();
                if (resultOptional.isPresent()) {
                    var result = new JSONObject(resultOptional.get());
                    if (result.getString("repoPath").isEmpty()) {
                        logger.error("Could not clone repo from URL #" + (int) total + ": " + url);
                        failedUrls.add(url);
                    } else {
                        successful++;
                        logger.info("Cloned repository from URL #" + (int) total + ": " + url);
                    }
                } else {
                    logger.error("No output from URL #" + (int) total + ": " + url);
                    emptyOutputUrls.add(url);
                }
                logger.info("Current success rate is " + successful / total);
            }
            logger.info("==================================================");
            logger.info("Finished cloning repositories");
            logger.info("Final success rate is " + successful / total);
            logger.info("==================================================");
            if (failedUrls.size() > 0) {
                logger.info("Failed URLs:");
                failedUrls.forEach(logger::info);
            }
            if (emptyOutputUrls.size() > 0) {
                logger.info("URLs that produced empty output:");
                emptyOutputUrls.forEach(logger::info);
            }
            return;
        }
        System.err.println("Invalid arguments!");
    }
}
