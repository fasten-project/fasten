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

package eu.fasten.analyzer.pomanalyzer;

import eu.fasten.core.data.Constants;
import eu.fasten.core.dbconnectors.PostgresConnector;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONTokener;
import picocli.CommandLine;

@CommandLine.Command(name = "POMAnalyzer")
public class Main implements Runnable {

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "JSON_FILE",
            description = "Path to JSON file which contains the Maven coordinate")
    String jsonFile;

    @CommandLine.Option(names = {"-cf", "--coordinates-file"},
            paramLabel = "COORD_FILE",
            description = "Path to file which contains the Maven coordinates "
                    + "in form of groupId:artifactId:version")
    String coordinatesFile;

    @CommandLine.Option(names = {"-s", "--skip"},
            description = "Skip first line of the coordinates file")
    boolean skipFirstLine;

    @CommandLine.Option(names = {"-a", "--artifactId"},
            paramLabel = "ARTIFACT",
            description = "artifactId of the Maven coordinate")
    String artifact;

    @CommandLine.Option(names = {"-g", "--groupId"},
            paramLabel = "GROUP",
            description = "groupId of the Maven coordinate")
    String group;

    @CommandLine.Option(names = {"-v", "--version"},
            paramLabel = "VERSION",
            description = "version of the Maven coordinate")
    String version;

    @CommandLine.Option(names = {"-t", "--timestamp"},
            paramLabel = "TS",
            description = "Timestamp when the artifact was released")
    String timestamp;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:postgres")
    String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    String dbUser;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var pomAnalyzer = new POMAnalyzerPlugin.POMAnalyzer();
        try {
            pomAnalyzer.setDBConnection(new HashMap<>(Map.of(Constants.mvnForge,
                    PostgresConnector.getDSLContext(dbUrl, dbUser, true))));
        } catch (SQLException e) {
            System.err.println("Error connecting to the database:");
            e.printStackTrace(System.err);
            return;
        }
        if (artifact != null && group != null && version != null) {
            var mvnCoordinate = new JSONObject();
            mvnCoordinate.put("artifactId", artifact);
            mvnCoordinate.put("groupId", group);
            mvnCoordinate.put("version", version);
            mvnCoordinate.put("date", timestamp);
            var record = new JSONObject();
            record.put("payload", mvnCoordinate);
            pomAnalyzer.consume(record.toString());
            pomAnalyzer.produce().ifPresent(System.out::println);
            if (pomAnalyzer.getPluginError() != null) {
                System.err.println(pomAnalyzer.getPluginError().getMessage());
            }
        } else if (jsonFile != null) {
            FileReader reader;
            try {
                reader = new FileReader(jsonFile);
            } catch (FileNotFoundException e) {
                System.err.println("Could not find the JSON file at " + jsonFile);
                return;
            }
            var record = new JSONObject(new JSONTokener(reader));
            pomAnalyzer.consume(record.toString());
            pomAnalyzer.produce().ifPresent(System.out::println);
            if (pomAnalyzer.getPluginError() != null) {
                System.err.println(pomAnalyzer.getPluginError().getMessage());
            }
        } else if (coordinatesFile != null) {
            Scanner input;
            try {
                input = new Scanner(new File(coordinatesFile));
            } catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
                return;
            }
            if (skipFirstLine && input.hasNextLine()) {
                input.nextLine();
            }
            var count = 0F;
            var result = 0F;
            while (input.hasNextLine()) {
                count += 1F;
                var line = input.nextLine();
                var mvnCoordinate = new JSONObject();
                var coordinate = line.split(Constants.mvnCoordinateSeparator);
                mvnCoordinate.put("artifactId", coordinate[1]);
                mvnCoordinate.put("groupId", coordinate[0]);
                mvnCoordinate.put("version", coordinate[2]);
                var record = new JSONObject();
                record.put("payload", mvnCoordinate);
                pomAnalyzer.consume(record.toString());
                pomAnalyzer.produce().ifPresent(System.out::println);
                if (pomAnalyzer.getPluginError() != null) {
                    System.err.println(pomAnalyzer.getPluginError().getMessage());
                } else {
                    result += 1F;
                }
            }
            System.out.println("--------------------------------------------------");
            System.out.println("Success rate: " + result / count);
            System.out.println("--------------------------------------------------");
        } else {
            System.err.println("You need to specify Maven coordinate either by providing its "
                    + "artifactId ('-a'), groupId ('-g') and version ('-v') or by providing path "
                    + "to JSON file that contains that Maven coordinate as payload.");
        }
    }
}
