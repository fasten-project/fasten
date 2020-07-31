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

package eu.fasten.analyzer.qualityanalyzer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "Quality Analyzer")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "JSON File",
            description = "Path to JSON file which contains quality metrics and artifactId, "
                    + "groupId and version")
    String jsonFile;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var qualityAnalyser = new QualityAnalyzerPlugin.QualityAnalyzer();
        final FileReader reader;
        try {
            reader = new FileReader(jsonFile);
        } catch (FileNotFoundException e) {
            logger.error("Could not find input JSON " + jsonFile, e);
            return;
        }
        final JSONObject json = new JSONObject(new JSONTokener(reader));
        qualityAnalyser.consume(json.toString());
        var resultOptional = qualityAnalyser.produce();
        resultOptional.ifPresent(System.out::println);
    }
}
