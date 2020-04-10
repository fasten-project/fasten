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

package eu.fasten.analyzer.databaseplugin;

import eu.fasten.server.db.PostgresConnector;
import eu.fasten.server.kafka.FastenKafkaConnection;
import eu.fasten.server.kafka.FastenKafkaConsumer;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.rocksdb.RocksDBException;
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

    @CommandLine.Option(names = {"-s", "--skip_offsets"},
            paramLabel = "skip",
            description = "Adds one to offset of all the partitions of the consumers.",
            defaultValue = "0")
    int skipOffsets;

    @CommandLine.Option(names = {"-k", "--kafka_server"},
            paramLabel = "server.name:port",
            description = "Kafka server to connect to. Use multiple times for clusters.",
            defaultValue = "localhost:9092")
    List<String> kafkaServers;

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

    @CommandLine.Option(names = {"-kb", "--kb_dir"},
            paramLabel = "kbDir",
            description = "The directory of the RocksDB instance containing the knowledge base")
    String kbDir;

    @CommandLine.Option(names = {"-kbmeta", "--kbMeta_file"},
            paramLabel = "kbMeta",
            description = "The file containing the knowledge base metadata")
    String kbMeta;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            var databasePlugin = new DatabasePlugin.DatabaseExtension();
            databasePlugin.setTopic(topic);
            databasePlugin.setDBConnection(PostgresConnector.getDSLContext(dbUrl, dbUser, dbPass));
            databasePlugin.setKnowledgeBase(kbDir, kbMeta);

            if (jsonFile == null || jsonFile.isEmpty()) {
                var properties = FastenKafkaConnection.kafkaProperties(kafkaServers,
                        this.getClass().getCanonicalName());
                new FastenKafkaConsumer(properties, databasePlugin, skipOffsets).start();
            } else {
                final FileReader reader;
                try {
                    reader = new FileReader(jsonFile);
                } catch (FileNotFoundException e) {
                    logger.error("Could not find the JSON file at " + jsonFile, e);
                    return;
                }
                final JSONObject jsonCallgraph = new JSONObject(new JSONTokener(reader));
                try {
                    final var record = new ConsumerRecord<>(topic, 0, 0L, "test",
                            jsonCallgraph.toString());
                    databasePlugin.consume(topic, record);
                } catch (IllegalArgumentException e) {
                    logger.error("Incorrect database URL", e);
                }
            }
        } catch (SQLException | RocksDBException | IOException | ClassNotFoundException e) {
            logger.error("Could not connect to the database or instantiate KnowledgeBase", e);
        }
    }
}
