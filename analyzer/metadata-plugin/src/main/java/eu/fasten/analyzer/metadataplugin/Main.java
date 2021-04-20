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

package eu.fasten.analyzer.metadataplugin;

import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.data.Constants;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "MetadataPlugin")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "JSON_FILE",
            description = "Path to JSON file which contains the callgraph")
    String jsonFile;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:fasten_java")
    String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    String dbUser;

    @CommandLine.Option(names = {"-l", "--language"},
            paramLabel = "LANGUAGE",
            description = "Language of the callgraph",
            defaultValue = "java")
    String language;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    public MetadataDBExtension getMetadataDBExtension() {
        switch (language) {
            case "java":
                return new MetadataDatabaseJavaPlugin.MetadataDBJavaExtension();
            case "c":
                return new MetadataDatabaseCPlugin.MetadataDBCExtension();
            case "python":
                return new MetadataDatabasePythonPlugin.MetadataDBPythonExtension();
        }
        return null;
    }

    public String getForge() {
        switch (language) {
            case "java":
                return Constants.mvnForge;
            case "c":
                return Constants.debianForge;
            case "python":
                return Constants.pypiForge;
        }
        return null;
    }

    @Override
    public void run() {
        var metadataPlugin = getMetadataDBExtension();
        try {
            metadataPlugin.setDBConnection(new HashMap<>(Map.of(getForge(),
                                           PostgresConnector.getDSLContext(dbUrl, dbUser, true))));
        } catch (IllegalArgumentException | SQLException e) {
            logger.error("Could not connect to the database", e);
            return;
        }
        final FileReader reader;
        try {
            reader = new FileReader(jsonFile);
        } catch (FileNotFoundException e) {
            logger.error("Could not find the JSON file at " + jsonFile, e);
            return;
        }
        final JSONObject jsonCallgraph = new JSONObject(new JSONTokener(reader));
        metadataPlugin.consume(jsonCallgraph.toString());
        metadataPlugin.produce().ifPresent(System.out::println);
        if (metadataPlugin.getPluginError() != null) {
            metadataPlugin.getPluginError().printStackTrace(System.err);
        }
    }
}
