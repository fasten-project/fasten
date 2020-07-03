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
import eu.fasten.core.plugins.*;
import eu.fasten.server.connectors.KafkaConnector;
import eu.fasten.server.connectors.PostgresConnector;
import eu.fasten.server.connectors.RocksDBConnector;
import eu.fasten.server.plugins.FastenServerPlugin;
import eu.fasten.server.plugins.kafka.FastenKafkaPlugin;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.pf4j.JarPluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;


@CommandLine.Command(name = "FastenServer", mixinStandardHelpOptions = true)
public class FastenServer implements Runnable {

    @Option(names = {"-p", "--plugin_dir"},
            paramLabel = "DIR",
            description = "Directory to load plugins from.",
            defaultValue = "./plugins")
    Path pluginPath;

    @Option(names = {"-la", "--list_all"},
            description = "List all values and ext.")
    boolean showPlugins;

    @Option(names = {"-pl", "--plugin_list"},
            paramLabel = "plugins",
            description = "List of plugins to run. Can be used multiple times.",
            split = ",")
    List<String> plugins;

    @Option(names = {"-po", "--plugin_output"},
            paramLabel = "dir",
            description = "Path to directory where plugin output messages will be stored")
    Map<String, String> outputDirs;

    @Option(names = {"-pol", "--plugin_output_link"},
            paramLabel = "dir",
            description = "HTTP link to the root directory where output messages will be stored")
    Map<String, String> outputLinks;

    @Option(names = {"-m", "--mode"},
            description = "Deployment or Development mode")
    boolean deployMode;

    @Option(names = {"-k", "--kafka_server"},
            paramLabel = "server.name:port",
            description = "Kafka server to connect to. Use multiple times for clusters.",
            defaultValue = "localhost:9092")
    List<String> kafkaServers;

    @Option(names = {"-kt", "--topic"},
            paramLabel = "topic",
            description = "Kay-value pairs of Plugin and topic to consume from. Example - "
                    + "OPAL=fasten.maven.pkg",
            split = ",")
    Map<String, String> pluginTopic;

    @Option(names = {"-ks", "--skip_offsets"},
            paramLabel = "skip",
            description = "Adds one to offset of all the partitions of the consumers.",
            defaultValue = "0")
    int skipOffsets;

    @Option(names = {"-d", "--database"},
            paramLabel = "dbURL",
            description = "Database URL for connection")
    String dbUrl;

    @Option(names = {"-du", "--user"},
            paramLabel = "dbUser",
            description = "Database user name")
    String dbUser;

    @Option(names = {"-gd", "--graphdb_dir"},
            paramLabel = "dir",
            description = "Path to directory with RocksDB database")
    String graphDbDir;

    @Option(names = {"-b", "--base_dir"},
            paramLabel = "PATH",
            description = "Path to base directory to which data will be written")
    String baseDir;

    private static final Logger logger = LoggerFactory.getLogger(FastenServer.class);

    @Override
    public void run() {
        setLoggingLevel();

        logger.debug("Loading plugins from: {}", pluginPath);

        JarPluginManager jarPluginManager = new JarPluginManager(pluginPath);
        jarPluginManager.loadPlugins();
        jarPluginManager.startPlugins();
        if (showPlugins) {
            System.out.println("Available plugins:");
            jarPluginManager.getExtensions(FastenPlugin.class)
                    .forEach(x -> System.out.println(String.format("\t%s %s %s",
                            x.getClass().getSimpleName(), x.version(), x.description())));
            System.exit(0);
        }

        // Stop plugins that are not passed as parameters.
        jarPluginManager.getPlugins().stream()
                .filter(x -> !plugins.contains(jarPluginManager
                        .getExtensions(x.getPluginId()).get(0).getClass().getSimpleName()))
                .forEach(x -> {
                    jarPluginManager.stopPlugin(x.getPluginId());
                    jarPluginManager.unloadPlugin(x.getPluginId());
                });

        var plugins = jarPluginManager.getExtensions(FastenPlugin.class);
        var dbPlugins = jarPluginManager.getExtensions(DBConnector.class);
        var kafkaPlugins = jarPluginManager.getExtensions(KafkaPlugin.class);
        var graphDbPlugins = jarPluginManager.getExtensions(GraphDBConnector.class);
        var dataWriterPlugins = jarPluginManager.getExtensions(DataWriter.class);

        logger.info("Plugin init done: {} KafkaPlugins, {} DB plug-ins, {} GraphDB plug-ins:"
                        + " {} total plugins",
                kafkaPlugins.size(), dbPlugins.size(), graphDbPlugins.size(), plugins.size());
        plugins.forEach(x -> logger.info("{}, {}, {}", x.getClass().getSimpleName(),
                x.version(), x.description()));

        makeDBConnection(dbPlugins);
        makeGraphDBConnection(graphDbPlugins);
        setBaseDirectory(dataWriterPlugins);

        var kafkaServerPlugins = setupKafkaPlugins(kafkaPlugins);

        kafkaServerPlugins.forEach(FastenServerPlugin::start);

        waitForInterruption(kafkaServerPlugins);
    }

    /**
     * Joins threads of kafka plugins, waits for the interrupt signal and sends
     * shutdown signal to all threads.
     *
     * @param plugins list of kafka plugins
     */
    private void waitForInterruption(List<FastenServerPlugin> plugins) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            plugins.forEach(FastenServerPlugin::stop);
            plugins.forEach(c -> {
                try {
                    c.thread().join();
                } catch (InterruptedException e) {
                    logger.debug("Couldn't join consumers");
                }
            });
            logger.info("Fasten server has been successfully stopped");
        }));

        plugins.forEach(c -> {
            try {
                c.thread().join();
            } catch (InterruptedException e) {
                logger.debug("Couldn't join consumers");
            }
        });
    }

    /**
     * Changes Kafka topics of consumers ad producers if specified in command line.
     *
     * @param kafkaPlugins list of consumers
     */
    private List<FastenServerPlugin> setupKafkaPlugins(List<KafkaPlugin> kafkaPlugins) {
        if (pluginTopic != null) {
            kafkaPlugins.stream()
                    .filter(x -> pluginTopic.containsKey(x.getClass().getSimpleName()))
                    .forEach(x -> x.setTopic(pluginTopic.get(x.getClass().getSimpleName())));
        }

        return kafkaPlugins.stream().map(k -> {
            var consumerProperties = KafkaConnector.kafkaConsumerProperties(
                    kafkaServers,
                    k.getClass().getCanonicalName());
            var producerProperties = KafkaConnector.kafkaProducerProperties(
                    kafkaServers,
                    k.getClass().getCanonicalName());

            return new FastenKafkaPlugin(consumerProperties, producerProperties, k, skipOffsets,
                    (outputDirs != null) ? outputDirs.get(k.getClass().getSimpleName()) : null,
                    (outputLinks != null) ? outputLinks.get(k.getClass().getSimpleName()) : null);
        }).collect(Collectors.toList());
    }

    /**
     * Setup DB connection for DB plugins.
     *
     * @param dbPlugins list of DB plugins
     */
    private void makeDBConnection(List<DBConnector> dbPlugins) {
        dbPlugins.forEach((p) -> {
            if (ObjectUtils.allNotNull(dbUrl, dbUser)) {
                try {
                    p.setDBConnection(PostgresConnector.getDSLContext(dbUrl, dbUser));
                    logger.debug("Set DB connection successfully for plug-in {}",
                            p.getClass().getSimpleName());
                } catch (SQLException e) {
                    logger.error("Couldn't set DB connection for plug-in {}\n{}",
                            p.getClass().getSimpleName(), e.getStackTrace());
                }
            } else {
                logger.error("Couldn't make a DB connection. Make sure that you have "
                        + "provided a valid DB URL, username and password.");
            }
        });
    }

    /**
     * Setup RocksDB connection for GraphDB plugins.
     *
     * @param graphDbPlugins list of Graph DB plugins
     */
    private void makeGraphDBConnection(List<GraphDBConnector> graphDbPlugins) {
        graphDbPlugins.forEach((p) -> {
            if (ObjectUtils.allNotNull(graphDbDir)) {
                try {
                    p.setRocksDao(RocksDBConnector.createRocksDBAccessObject(graphDbDir));
                    logger.debug("Set Graph DB connection successfully for plug-in {}",
                            p.getClass().getSimpleName());
                } catch (RuntimeException e) {
                    logger.error("Couldn't set GraphDB connection for plug-in {}",
                            p.getClass().getSimpleName(), e);
                }
            } else {
                logger.error("Couldn't set a GraphDB connection. Make sure that you have "
                        + "provided a valid directory to the database.");
            }
        });
    }

    /**
     * Sets base directory to Data Writer plugins
     *
     * @param dataWriterPlugins list of Data Writer plugins
     */
    private void setBaseDirectory(List<DataWriter> dataWriterPlugins) {
        dataWriterPlugins.forEach((p) -> {
            if (ObjectUtils.allNotNull(baseDir)) {
                p.setBaseDir(baseDir);
            } else {
                logger.error("Couldn't set a base directory. Make sure that you have "
                        + "provided a valid path to base directory.");
            }
        });
    }

    /**
     * Sets logging level depending on the command line argument.
     */
    private void setLoggingLevel() {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        if (showPlugins) {
            root.setLevel(Level.OFF);
            return;
        }
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
