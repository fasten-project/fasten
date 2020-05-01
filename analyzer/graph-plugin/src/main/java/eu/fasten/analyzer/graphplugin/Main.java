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

package eu.fasten.analyzer.graphplugin;

import eu.fasten.server.kafka.FastenKafkaConnection;
import eu.fasten.server.kafka.FastenKafkaConsumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "GraphPlugin")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-t", "--topic"},
            paramLabel = "topic",
            description = "Kafka topic from which to consume call graphs",
            defaultValue = "fasten.cg.edges")
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
            description = "Path to JSON file which contains edges")
    String jsonFile;

    @CommandLine.Option(names = {"-kb", "--kbDirectory"},
            paramLabel = "kbDir",
            description = "The directory of the RocksDB instance containing the knowledge base")
    String kbDir;

    @CommandLine.Option(names = {"-kbmeta", "--kbMetadataFile"},
            paramLabel = "kbMeta",
            description = "The file containing the knowledge base metadata")
    String kbMeta;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var graphPlugin = new GraphDatabasePlugin.GraphDBExtension();
        graphPlugin.setTopic(topic);
        try {
            graphPlugin.setKnowledgeBase(kbDir, kbMeta);
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            e.printStackTrace(System.err);
            return;
        }
        if (jsonFile == null || jsonFile.isEmpty()) {
            var consumerProperties = FastenKafkaConnection.kafkaProperties(kafkaServers,
                    this.getClass().getCanonicalName());
            new FastenKafkaConsumer(consumerProperties, graphPlugin, skipOffsets).start();
        } else {
            final String fileContents;
            try {
                fileContents = Files.readString(Paths.get(jsonFile));
            } catch (IOException e) {
                logger.error("Could not find the JSON file at " + jsonFile, e);
                return;
            }
            String artifact = fileContents.split("\n")[0];
            String edges = fileContents.split("\n")[1];
            final var record = new ConsumerRecord<>(topic, 0, 0L, artifact, edges);
            graphPlugin.consume(topic, record);
        }
    }
}
