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
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import eu.fasten.server.plugins.FastenServerPlugin;
import eu.fasten.server.plugins.kafka.FastenKafkaPlugin;

import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.jooq.DSLContext;
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
                    + "OPAL=fasten.OPAL.out",
            split = ",")
    Map<String, String> pluginTopic;

    @Option(names = {"-ks", "--skip_offsets"},
            paramLabel = "skip",
            description = "Adds one to offset of all the partitions of the consumers.",
            defaultValue = "0")
    int skipOffsets;

    @Option(names = {"-d", "--database"},
            paramLabel = "dbURL",
            description = "Kay-value pairs of Database URLs for connection Example - " +
                    "mvn=jdbc:postgresql://postgres@localhost/dbname",
            split = ",")
    Map<String, String> dbUrls;

    @Option(names = {"-dgp", "--dep_graph_path"},
            paramLabel = "dir",
            description = "Path to serialized dependency graph")
    String depGraphPath;

    @Option(names = {"-gd", "--graphdb_dir"},
            paramLabel = "dir",
            description = "Path to directory with RocksDB database")
    String graphDbDir;

    @Option(names = {"-b", "--base_dir"},
            paramLabel = "PATH",
            description = "Path to base directory to which data will be written")
    String baseDir;

    @Option(names = {"-cg", "--consumer_group"},
            paramLabel = "consumerGroup",
            description = "Name of the consumer group. Defaults to (canonical) name of the plugin.",
            defaultValue = "undefined"
    )
    String consumerGroup;

    @Option(names = {"-ot", "--output_topic"},
            paramLabel = "outputTopic",
            description = "Name of the output topic. Defaults to (simple) name of the plugin."
    )
    String outputTopic;

    @Option(names = {"-ct", "--consume_timeout"},
            paramLabel = "consumeTimeout",
            description = "Adds a timeout on the time a plugin can spend on its consumed records. Disabled by default.",
            defaultValue = "-1"
    )
    long consumeTimeout;

    @Option(names = {"-cte", "--consume_timeout_exit"},
            paramLabel = "consumeTimeoutExit",
            description = "Shutdowns the JVM if a consume timeout is reached."
    )
    boolean consumeTimeoutExit;


    @Option(names = {"-ls", "--local_storage"},
            paramLabel = "localStorage",
            description = "Enables local storage which stores record currently processed. This ensure that records that were processed before won't be processed again (e.g. when the pod crashes). "
    )
    boolean localStorage;

    @Option(names = {"-lsd", "--local_storage_dir"},
            paramLabel = "localStorageDir",
            description = "Directory of local storage, must be available from every pod location. Default's to /mnt/fasten/local_storage/plugin_name")
    String localStorageDir;

    private static final Logger logger = LoggerFactory.getLogger(FastenServer.class);

    @Override
    public void run() {
        setLoggingLevel();
        if (!deployMode) {
            showSysInfo();
        }

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
                .filter(x -> !jarPluginManager.getExtensions(x.getPluginId()).stream().anyMatch(e -> plugins.contains(e.getClass().getSimpleName())))
                .forEach(x -> {
                    jarPluginManager.stopPlugin(x.getPluginId());
                    jarPluginManager.unloadPlugin(x.getPluginId());
                });

        var fastenPlugins = jarPluginManager.getExtensions(FastenPlugin.class);
        var dbPlugins = jarPluginManager.getExtensions(DBConnector.class);
        var kafkaPlugins = jarPluginManager.getExtensions(KafkaPlugin.class);
        var graphDbPlugins = jarPluginManager.getExtensions(GraphDBConnector.class);
        var dataWriterPlugins = jarPluginManager.getExtensions(DataWriter.class);
        var graphResolverUserPlugins = jarPluginManager.getExtensions(DependencyGraphUser.class);
        var graphDbReaderPlugins = jarPluginManager.getExtensions(GraphDBReader.class);

        logger.info("Plugin init done: {} KafkaPlugins, {} DB plug-ins, {} GraphDB plug-ins:"
                        + " {} total plugins",
                kafkaPlugins.size(), dbPlugins.size(), graphDbPlugins.size(), fastenPlugins.size());
        fastenPlugins.stream().filter(x -> plugins.contains(x.getClass().getSimpleName()))
                .forEach(x -> logger.info("{}, {}, {}", x.getClass().getSimpleName(),
                        x.version(), x.description()));

        makeDBConnection(dbPlugins);
        makeGraphDBConnection(graphDbPlugins);
        setBaseDirectory(dataWriterPlugins);
        loadDependencyGraphResolvers(graphResolverUserPlugins);
        makeReadOnlyGraphDBConnection(graphDbReaderPlugins);

        var kafkaServerPlugins = setupKafkaPlugins(kafkaPlugins);

        kafkaServerPlugins.forEach(FastenServerPlugin::start);
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

        return kafkaPlugins.stream().filter(x -> plugins.contains(x.getClass().getSimpleName())).map(k -> {
            var consumerProperties = KafkaConnector.kafkaConsumerProperties(
                    kafkaServers,
                    (consumerGroup.equals("undefined") ? k.getClass().getCanonicalName() : consumerGroup), // if consumergroup != undefined, set to canonical name. If we upgrade to picocli 2.4.6 we can use optionals.
                    k.getSessionTimeout(),
                    k.getMaxConsumeTimeout(),
                    k.isStaticMembership());
            var producerProperties = KafkaConnector.kafkaProducerProperties(
                    kafkaServers,
                    k.getClass().getCanonicalName());

            return new FastenKafkaPlugin(consumerProperties, producerProperties, k, skipOffsets,
                    (outputDirs != null) ? outputDirs.get(k.getClass().getSimpleName()) : null,
                    (outputLinks != null) ? outputLinks.get(k.getClass().getSimpleName()) : null,
                    (outputTopic != null) ? outputTopic : k.getClass().getSimpleName(),
                    (consumeTimeout != -1) ? true : false,
                    consumeTimeout,
                    consumeTimeoutExit,
                    localStorage,
                    (localStorageDir != null) ? localStorageDir : "/mnt/fasten/local_storage/" + k.getClass().getSimpleName());
        }).collect(Collectors.toList());
    }

    /**
     * Setup DB connection for DB plugins.
     *
     * @param dbPlugins list of DB plugins
     */
    private void makeDBConnection(List<DBConnector> dbPlugins) {
        dbPlugins.forEach((p) -> {
            if (dbUrls != null) {
                p.setDBConnection(dbUrls.entrySet().stream().map(e -> new AbstractMap.SimpleEntry<>(e.getKey(),
                        e.getValue())).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, e -> {
                    try {
                        logger.debug("Set {} DB connection successfully for plug-in {}",
                                e.getKey(), p.getClass().getSimpleName());
                        return getDSLContext(e.getValue());
                    } catch (SQLException ex) {
                        logger.error("Couldn't set {} DB connection for plug-in {}\n{}",
                                e.getKey(), p.getClass().getSimpleName(), ex.getStackTrace());
                    }
                    return null;
                })));

            } else {
                logger.error("Couldn't make a DB connection. Make sure that you have "
                        + "provided a valid DB URL, username and password.");
            }
        });
    }

    private void loadDependencyGraphResolvers(List<DependencyGraphUser> plugins) {
        plugins.forEach(p -> {
            if (dbUrls != null && depGraphPath != null) {
                DSLContext dbContext = null;
                try {
                    dbContext = getDSLContext(dbUrls.get("mvn"));
                } catch (SQLException ex) {
                    logger.error("Couldn't set {} DB connection for plug-in {}\n{}",
                            dbUrls.get("mvn"), p.getClass().getSimpleName(), ex.getStackTrace());
                }
                p.loadGraphResolver(dbContext, depGraphPath);
            } else {
                logger.error("Couldn't load dependency graph. Make sure that you have "
                        + "provided a valid DB URL, username, password, "
                        + "and a path to the serialized dependency graph.");
            }
        });
    }

    /**
     * Get a DB connection for a given DB URL
     *
     * @param dbURL JDBC URI
     * @throws SQLException
     */
    private DSLContext getDSLContext(String dbURL) throws SQLException {
        String cleanURI = dbURL.substring(5);
        URI uri = URI.create(cleanURI);
        return PostgresConnector.getDSLContext("jdbc:postgresql://" + uri.getHost() + uri.getPath(),
                uri.getUserInfo(), true);
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
     * Setup RocksDB connection for GraphDB plugins.
     *
     * @param graphDbPlugins list of Graph DB plugins
     */
    private void makeReadOnlyGraphDBConnection(List<GraphDBReader> graphDbPlugins) {
        graphDbPlugins.forEach((p) -> {
            if (ObjectUtils.allNotNull(graphDbDir)) {
                try {
                    p.setRocksDao(RocksDBConnector.createReadOnlyRocksDBAccessObject(graphDbDir));
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

    /**
     * Shows system info. It can be useful for debugging purpose.
     */
    private void showSysInfo() {
        logger.info("************************System Info***************************");
        logger.info("Max Heap size: " + Runtime.getRuntime().maxMemory() + " Bytes");
        logger.info("**************************************************************");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FastenServer()).execute(args);
        System.exit(exitCode);
    }
}
