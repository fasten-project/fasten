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
import eu.fasten.core.plugins.KafkaPlugin;
import eu.fasten.server.db.PostgresConnector;
import eu.fasten.server.kafka.FastenKafkaConnection;
import eu.fasten.server.kafka.FastenKafkaPlugin;
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
        var dbPlugins = jarPluginManager.getExtensions(DBConnector.class);
        var kafkaPlugins = jarPluginManager.getExtensions(KafkaPlugin.class);

        logger.info("Plugin init done: {} KafkaPlugins, {} DB plug-ins: {} total plugins",
                kafkaPlugins.size(), dbPlugins.size(), plugins.size());

        changeDefaultTopics(kafkaPlugins);
        makeDBConnection(dbPlugins);

        List<FastenKafkaPlugin> kafkaServerPlugins = kafkaPlugins.stream().map(k -> {
            var properties = FastenKafkaConnection.kafkaProperties(
                    kafkaServers,
                    k.getClass().getCanonicalName());

            return new FastenKafkaPlugin(properties, k, skipOffsets);
        }).collect(Collectors.toList());

        kafkaServerPlugins.forEach(FastenKafkaPlugin::start);

        waitForInterruption(kafkaServerPlugins);
    }

    /**
     * Joins threads of kafka plugins, waits for the interrupt signal and sends
     * shutdown signal to all threads.
     *
     * @param plugins list of kafka plugins
     */
    private void waitForInterruption(List<FastenKafkaPlugin> plugins) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            plugins.forEach(FastenKafkaPlugin::stop);
            logger.info("Fasten server has been successfully stopped");
        }));

        plugins.forEach(c -> {
            try {
                c.getThread().join();
            } catch (InterruptedException e) {
                logger.debug("Couldn't join consumers");
            }
        });
    }

    /**
     * Changes Kafka topics of consumers ad producers if specified in command line.
     *
     * @param kafkaConsumers list of consumers
     */
    private void changeDefaultTopics(List<KafkaPlugin> kafkaConsumers) {
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
        }
    }

    /**
     * Setup DB connection for DB plugins.
     *
     * @param dbPlugins list of DB plugins
     */
    private void makeDBConnection(List<DBConnector> dbPlugins) {
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
