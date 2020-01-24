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

package eu.fasten.server;

import eu.fasten.core.plugins.FastenPlugin;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import eu.fasten.server.kafka.FastenKafkaConnection;
import eu.fasten.server.kafka.FastenKafkaConsumer;
import eu.fasten.server.kafka.FastenKafkaProducer;
import org.pf4j.JarPluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;


@CommandLine.Command(name = "FastenServer", mixinStandardHelpOptions = true)
public class FastenServer implements Runnable {

    @Option(names = {"-p", "--plugin_dir"},
            paramLabel = "DIR",
            description = "Directory to load plugins from",
            defaultValue = ".")
    private Path pluginPath;

    @Option(names = {"-k", "--kafka_server"},
            paramLabel = "server.name:port",
            description = "Kafka server to connect to. Use multiple times for clusters.",
            defaultValue = "localhost:9092")
    private List<String> kafkaServers;

    @Option(names = {"-s", "--skip_offsets"},
            paramLabel = "skip",
            description = "Adds one to offset of all the partitions of the consumers.",
            defaultValue = "0")
    private int skipOffsets;

    private static Logger logger = LoggerFactory.getLogger(FastenServer.class);

    private List<FastenKafkaConsumer> consumers;
    private List<FastenKafkaProducer> producers;

    public void run() {

        // Register shutdown actions
        // TODO: Fix the null pointer exception for the following ShutdownHook
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            public void run() {
//                logger.debug("Shutting down...");
//                if (consumers != null) {
//                    consumers.forEach(c -> c.shutdown());
//                }
//            }
//        });

        logger.debug("Loading plugins from: {}", pluginPath);

        JarPluginManager jarPluginManager = new JarPluginManager(pluginPath);
        jarPluginManager.loadPlugins();
        jarPluginManager.startPlugins();

        List<FastenPlugin> plugins = jarPluginManager.getExtensions(FastenPlugin.class);
        List<KafkaConsumer> kafkaConsumers = jarPluginManager.getExtensions(KafkaConsumer.class);
        List<KafkaProducer> kafkaProducers = jarPluginManager.getExtensions(KafkaProducer.class);

        logger.info("Plugin init done: {} KafkaConsumers, {} KafkaProducers, {} total plugins",
                kafkaConsumers.size(), kafkaProducers.size(), plugins.size());

        this.producers = kafkaProducers.stream().map(k -> {
            var properties = FastenKafkaConnection.producerProperties(kafkaServers,
                    k.getClass().getCanonicalName());

            return new FastenKafkaProducer(properties, k);

        }).collect(Collectors.toList());

        this.producers.forEach(c -> c.start());

        this.consumers = kafkaConsumers.stream().map(k -> {
            var properties = FastenKafkaConnection.kafkaProperties(
                    kafkaServers,
                    k.getClass().getCanonicalName());

            return new FastenKafkaConsumer(properties, k, skipOffsets);
        }).collect(Collectors.toList());

        this.consumers.forEach(c -> c.start());

    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FastenServer()).execute(args);
        System.exit(exitCode);
    }
}
