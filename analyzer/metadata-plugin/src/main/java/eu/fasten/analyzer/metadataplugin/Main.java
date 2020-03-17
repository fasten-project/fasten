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

import eu.fasten.analyzer.metadataplugin.db.PostgresConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "MetadataPlugin")
public class Main implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-t", "--topic"},
            paramLabel = "topic",
            description = "Kafka topic from which to consume call graphs",
            defaultValue = "opal_callgraphs")
    String topic;

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "JSON",
            description = "Path to JSON file which contains the callgraph")
    String jsonFile;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "dbURL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:postgres")
    String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "dbUser",
            description = "Database user name",
            defaultValue = "postgres")
    String dbUser;

    @CommandLine.Option(names = {"-p", "--pass"},
            paramLabel = "dbPass",
            description = "Database user password",
            defaultValue = "pass123")
    String dbPass;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        if (jsonFile == null || jsonFile.isEmpty()) {
            try {
                final var context = PostgresConnector.getDSLContext(dbUrl, dbUser, dbPass);
                var metadataPlugin = new MetadataDatabasePlugin.MetadataPlugin(topic, context);
                metadataPlugin.start();
            } catch (SQLException e) {
                logger.error("Could not connect to the database", e);
            } catch (IllegalArgumentException e) {
                logger.error("Incorrect database URL", e);
            }
        } else {
            final var filePath = Paths.get(jsonFile);
            var jsonCallgraph = new JSONObject();
            try {
                jsonCallgraph = new JSONObject(Files.readString(filePath));
            } catch (IOException e) {
                logger.error("Could not find the JSON file at " + jsonFile, e);
                return;
            }
            try {
                final var context = PostgresConnector.getDSLContext(dbUrl, dbUser, dbPass);
                var metadataPlugin = new MetadataDatabasePlugin.MetadataPlugin(topic, context);
                final var record = new ConsumerRecord<>(topic, 0, 0L, "test",
                        jsonCallgraph.toString());
                metadataPlugin.consume(topic, record);
            } catch (SQLException e) {
                logger.error("Could not connect to the database", e);
            } catch (IllegalArgumentException e) {
                logger.error("Incorrect database URL", e);
            }
        }
    }
}
