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

package eu.fasten.core.merge;

import ch.qos.logback.classic.Level;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "MavenMerger", mixinStandardHelpOptions = true)
public class Merger implements Runnable {

    @CommandLine.Option(names = {"-a", "--artifact"},
            paramLabel = "ARTIFACT",
            description = "Maven coordinate of an artifact or file path for local merge")
    String artifact;

    @CommandLine.Option(names = {"-d", "--dependencies"},
            paramLabel = "DEPS",
            description = "Maven coordinates of dependencies or file paths for local merge",
            split = ",")
    List<String> dependencies;

    @CommandLine.Option(names = {"-md", "--database"},
            paramLabel = "dbURL",
            description = "Metadata database URL for connection")
    String dbUrl;

    @CommandLine.Option(names = {"-du", "--user"},
            paramLabel = "dbUser",
            description = "Metadata database user name")
    String dbUser;

    @CommandLine.Option(names = {"-gd", "--graphdb_dir"},
            paramLabel = "dir",
            description = "Path to directory with RocksDB database")
    String graphDbDir;

    @CommandLine.Option(names = {"-m", "--mode"},
            paramLabel = "mode",
            description = "Merge mode. Available: DATABASE, LOCAL",
            defaultValue = "LOCAL")
    String mode;

    @CommandLine.Option(names = {"-o", "--output"},
            paramLabel = "output",
            description = "Output file path")
    String output;

    private static final Logger logger = LoggerFactory.getLogger(Merger.class);

    public static void main(String[] args) {
        System.exit(new CommandLine(new Merger()).execute(args));
    }

    @Override
    public void run() {
        if (artifact != null && dependencies != null && !dependencies.isEmpty()) {
            var root = (ch.qos.logback.classic.Logger) LoggerFactory
                    .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.setLevel(Level.INFO);

            System.out.println("==========================");
            System.out.println("+         MERGER         +");
            System.out.println("==========================");

            System.out.println("--------------------------------------------------");
            System.out.println("Artifact: " + artifact);
            System.out.println("--------------------------------------------------");
            final long startTime = System.currentTimeMillis();

            switch (mode) {
                case "DATABASE":
                    DSLContext dbContext;
                    RocksDao rocksDao;
                    try {
                        dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser, false);
                        rocksDao = RocksDBConnector.createReadOnlyRocksDBAccessObject(graphDbDir);
                    } catch (SQLException | IllegalArgumentException e) {
                        logger.error("Could not connect to the metadata database: " + e.getMessage());
                        return;
                    } catch (RuntimeException e) {
                        logger.error("Could not connect to the graph database: " + e.getMessage());
                        return;
                    }

                    final var depSet = dependencies;
                    depSet.add(artifact);
                    var databaseMerger = new CGMerger(depSet, dbContext, rocksDao);
                    var mergedDirectedGraph = databaseMerger.mergeWithCHA(artifact);
                    logger.info("Resolved {} nodes, {} calls in {} seconds",
                            mergedDirectedGraph.numNodes(),
                            mergedDirectedGraph.numArcs(),
                            new DecimalFormat("#0.000")
                                    .format((System.currentTimeMillis() - startTime) / 1000d));

                    rocksDao.close();
                    break;

                case "LOCAL":
                    ExtendedRevisionJavaCallGraph artFile;
                    var depFiles = new ArrayList<ExtendedRevisionJavaCallGraph>();

                    try {
                        var tokener = new JSONTokener(new FileReader(artifact));
                        artFile = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));
                    } catch (FileNotFoundException e) {
                        logger.error("Incorrect file path for the artifact", e);
                        return;
                    }

                    for (var dep : dependencies) {
                        try {
                            var tokener = new JSONTokener(new FileReader(dep));
                            depFiles.add(new ExtendedRevisionJavaCallGraph(new JSONObject(tokener)));
                        } catch (FileNotFoundException e) {
                            logger.error("Incorrect file path for a dependency");
                        }
                    }
                    depFiles.add(artFile);
                    var localMerger = new CGMerger(depFiles);
                    var mergedERCG = new ExtendedRevisionJavaCallGraph(new JSONObject()); //localMerger.mergeWithCHA(artFile); TODO: Fix this
                    logger.info("Resolved {} nodes, {} calls in {} seconds",
                            mergedERCG.getClassHierarchy().get(JavaScope.resolvedTypes).size(),
                            mergedERCG.getGraph().getResolvedCalls().size(),
                            new DecimalFormat("#0.000")
                                    .format((System.currentTimeMillis() - startTime) / 1000d));

                    if (output != null) {
                        try {
                            CallGraphUtils.writeToFile(output, JSONUtils.toJSONString(mergedERCG), "");
                        } catch (IOException e) {
                            logger.error("Unable to write to file");
                        }
                    }
                    break;

                default:
                    logger.warn("Unsupported mode. Available: DATABASE, LOCAL");
            }
            System.out.println("==================================================");
        }
    }
}
