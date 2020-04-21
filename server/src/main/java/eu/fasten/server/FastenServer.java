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

import eu.fasten.core.plugins.DBConnector;
import eu.fasten.server.db.PostgresConnector;
import eu.fasten.server.kafka.FastenKafkaConnection;
import org.apache.commons.lang3.ObjectUtils;
import ch.qos.logback.classic.Level;
import eu.fasten.core.plugins.FastenPlugin;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import eu.fasten.server.kafka.FastenKafkaConsumer;
import eu.fasten.server.kafka.FastenKafkaProducer;
import org.json.JSONObject;
import org.pf4j.JarPluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;


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

    private static Logger logger = LoggerFactory.getLogger(FastenServer.class);

    private List<FastenKafkaConsumer> consumers;
    private List<FastenKafkaProducer> producers;

    public static void setLoggingLevel(Level level) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }

    public void run() {

        if(deployMode) {
            setLoggingLevel(Level.INFO);
            logger.info("FASTEN server started in deployment mode");
        } else {
            setLoggingLevel(Level.DEBUG);
            logger.info("FASTEN server started in development mode");
        }

        // Register shutdown actions
        // TODO: Fix the null pointer exception for the following ShutdownHook
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//                        logger.debug("Shutting down...");
//                        if (consumers != null) {
//                            consumers.forEach(c -> c.shutdown());
//                        }
//                    }));

        logger.debug("Loading plugins from: {}", pluginPath);

        JarPluginManager jarPluginManager = new JarPluginManager(pluginPath);
        jarPluginManager.loadPlugins();
        jarPluginManager.startPlugins();

        List<FastenPlugin> plugins = jarPluginManager.getExtensions(FastenPlugin.class);
        List<KafkaConsumer> kafkaConsumers = jarPluginManager.getExtensions(KafkaConsumer.class);
        List<KafkaProducer> kafkaProducers = jarPluginManager.getExtensions(KafkaProducer.class);
        List<DBConnector> dbPlugins = jarPluginManager.getExtensions(DBConnector.class);

        logger.info("Plugin init done: {} KafkaConsumers, {} KafkaProducers, {} DB plug-ins: {} total plugins",
                kafkaConsumers.size(), kafkaProducers.size(), dbPlugins.size(), plugins.size());

        // Change the default topics of the plug-ins if a JSON file of the topics is given
        if(pluginTopic != null) {
            JSONObject jsonObject;
            try {
                jsonObject = new JSONObject(new String(Files.readAllBytes(Paths.get(pluginTopic))));
            } catch (IOException e) {
                logger.error("Failed to read the JSON file of the topics: {}", e.getMessage());
                // Here, it reads the JSON string directly from the CLI, not a file.
                jsonObject = new JSONObject(pluginTopic);
            }

            for(KafkaConsumer k: kafkaConsumers) {
                if(jsonObject.has(k.getClass().getSimpleName())) {
                    k.setTopic(jsonObject.getJSONObject(k.getClass().getSimpleName()).get("consumer").toString());
                }
            }

            for(KafkaProducer k: kafkaProducers) {
                if(jsonObject.has(k.getClass().getSimpleName())) {
                    k.setProducerTopic(jsonObject.getJSONObject(k.getClass().getSimpleName()).get("producer").toString());
                }
            }
        }

        // Here, a DB connection is made for the plug-ins that need it.
        if (ObjectUtils.allNotNull(dbUrl, dbUser, dbPass)){

            dbPlugins.forEach((p) -> {
                try {
                    p.setDBConnection(PostgresConnector.getDSLContext(dbUrl, dbUser, dbPass));
                    logger.debug("Set DB connection successfully for plug-in {}", p.getClass().getSimpleName());
                } catch (SQLException e) {
                    logger.error("Couldn't set DB connection for plug-in {}\n{}", p.getClass().getSimpleName(), e.getStackTrace());
                }
            });

        } else {
            logger.error("Couldn't make a DB connection. Make sure that you have provided a valid DB URL, username and password.");
        }

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
