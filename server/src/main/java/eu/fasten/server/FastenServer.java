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

import ch.qos.logback.classic.Level;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.FastenPlugin;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import eu.fasten.server.db.PostgresConnector;
import eu.fasten.server.kafka.FastenKafkaConnection;
import eu.fasten.server.kafka.FastenKafkaConsumer;
import eu.fasten.server.kafka.FastenKafkaProducer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.json.JSONObject;
import org.pf4j.JarPluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;


@CommandLine.Command(name = "FastenServer", mixinStandardHelpOptions = true)
public class FastenServer implements Runnable {

    @Option(names = {"-p", "--plugin_dir"},
            paramLabel = "DIR",
            description = "Directory to load plugins from",
            defaultValue = ".")
    private Path pluginPath;

    @Option(names = {"-t", "--topic"},
            paramLabel = "topic",
            description = "JSON file consists of topics for plug-ins.")
    private String pluginTopic;

    @Option(names = {"-k", "--kafka_server"},
            paramLabel = "server.name:port",
            description = "Kafka server to connect to. Use multiple times for clusters.",
            defaultValue = "localhost:9092")
    private List<String> kafkaServers;

    @Option(names = {"-d", "--database"},
            paramLabel = "dbURL",
            description = "Database URL for connection")
    private String dbUrl;

    @Option(names = {"-u", "--user"},
            paramLabel = "dbUser",
            description = "Database user name")
    private String dbUser;

    @Option(names = {"-pw", "--pass"},
            paramLabel = "dbPass",
            description = "Database user password")
    private String dbPass;

    @Option(names = {"-s", "--skip_offsets"},
            paramLabel = "skip",
            description = "Adds one to offset of all the partitions of the consumers.",
            defaultValue = "0")
    private int skipOffsets;

    @Option(names = {"-m", "--mode"},
            description = "Deployment or Development mode")
    boolean deployMode;

    private static final Logger logger = LoggerFactory.getLogger(FastenServer.class);

    @Override
    public void run() {
        setLoggingLevel();

        logger.debug("Loading plugins from: {}", pluginPath);

        JarPluginManager jarPluginManager = new JarPluginManager(pluginPath);
        jarPluginManager.loadPlugins();
        jarPluginManager.startPlugins();

        var plugins = jarPluginManager.getExtensions(FastenPlugin.class);
        var kafkaConsumers = jarPluginManager.getExtensions(KafkaConsumer.class);
        var kafkaProducers = jarPluginManager.getExtensions(KafkaProducer.class);
        var dbPlugins = jarPluginManager.getExtensions(DBConnector.class);

        logger.info("Plugin init done: {} KafkaConsumers, {} KafkaProducers, "
                        + "{} DB plug-ins: {} total plugins",
                kafkaConsumers.size(), kafkaProducers.size(), dbPlugins.size(), plugins.size());

        changeDefaultTopics(kafkaConsumers, kafkaProducers);

        // Here, a DB connection is made for the plug-ins that need it.
        if (ObjectUtils.allNotNull(dbUrl, dbUser, dbPass)) {
            dbPlugins.forEach((p) -> {
                try {
                    p.setDBConnection(PostgresConnector.getDSLContext(dbUrl, dbUser, dbPass));
                    logger.debug("Set DB connection successfully for plug-in {}",
                            p.getClass().getSimpleName());
                } catch (SQLException e) {
                    logger.error("Couldn't set DB connection for plug-in {}\n{}",
                            p.getClass().getSimpleName(), e.getStackTrace());
                }
            });
        } else {
            logger.error("Couldn't make a DB connection. Make sure that you have "
                    + "provided a valid DB URL, username and password.");
        }

        List<FastenKafkaProducer> producers = kafkaProducers.stream().map(k -> {
            var properties = FastenKafkaConnection.producerProperties(kafkaServers,
                    k.getClass().getCanonicalName());

            return new FastenKafkaProducer(properties, k);

        }).collect(Collectors.toList());

        List<FastenKafkaConsumer> consumers = kafkaConsumers.stream().map(k -> {
            var properties = FastenKafkaConnection.kafkaProperties(
                    kafkaServers,
                    k.getClass().getCanonicalName());

            return new FastenKafkaConsumer(properties, k, skipOffsets);
        }).collect(Collectors.toList());

        producers.forEach(Thread::start);
        consumers.forEach(Thread::start);

        waitForInterruption(consumers, producers);
    }

    /**
     * Joins threads of consumers and producers, waits for the interrupt signal and sends
     * shutdown signal to all threads.
     *
     * @param consumers list of consumers
     * @param producers list of producers
     */
    private void waitForInterruption(List<FastenKafkaConsumer> consumers,
                                     List<FastenKafkaProducer> producers) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            consumers.forEach(FastenKafkaConsumer::shutdown);
            logger.info("Fasten server has been successfully stopped");
        }));

        // Join all consumers and producers and wait until they are interrupted
        consumers.forEach(c -> {
            try {
                c.join();
            } catch (InterruptedException e) {
                logger.debug("Couldn't join consumers");
            }
        });
        producers.forEach(p -> {
            try {
                p.join();
            } catch (InterruptedException e) {
                logger.debug("Couldn't join producers");
            }
        });
    }

    /**
     * Changes Kafka topics of consumers ad producers if specified in command line.
     *
     * @param kafkaConsumers list of consumers
     * @param kafkaProducers list of producers
     */
    private void changeDefaultTopics(List<KafkaConsumer> kafkaConsumers,
                                     List<KafkaProducer> kafkaProducers) {
        if (pluginTopic != null) {
            JSONObject jsonObject;
            try {
                jsonObject = new JSONObject(new String(Files.readAllBytes(Paths.get(pluginTopic))));
            } catch (IOException e) {
                logger.error("Failed to read the JSON file of the topics: {}", e.getMessage());
                // Here, it reads the JSON string directly from the CLI, not a file.
                jsonObject = new JSONObject(pluginTopic);
            }

            for (var k : kafkaConsumers) {
                if (jsonObject.has(k.getClass().getSimpleName())) {
                    k.setTopic(jsonObject.getJSONObject(k.getClass().getSimpleName())
                            .get("consumer").toString());
                }
            }

            for (KafkaProducer k : kafkaProducers) {
                if (jsonObject.has(k.getClass().getSimpleName())) {
                    k.setProducerTopic(jsonObject.getJSONObject(k.getClass().getSimpleName())
                            .get("producer").toString());
                }
            }
        }
    }

    /**
     * Sets logging level depending on the command line argument.
     */
    private void setLoggingLevel() {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        if (deployMode) {
            root.setLevel(Level.INFO);
            logger.info("FASTEN server started in deployment mode");
        } else {
            root.setLevel(Level.DEBUG);
            logger.info("FASTEN server started in development mode");
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FastenServer()).execute(args);
        System.exit(exitCode);
    }
}
