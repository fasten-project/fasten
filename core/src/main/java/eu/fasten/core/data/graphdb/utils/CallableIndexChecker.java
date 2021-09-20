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

package eu.fasten.core.data.graphdb.utils;

import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import eu.fasten.core.merge.CallGraphUtils;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.io.IOException;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@CommandLine.Command(name = "CallableIndexChecker")
public class CallableIndexChecker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CallableIndexChecker.class);

    @CommandLine.Option(names = {"-p", "--graph-db-path"},
        paramLabel = "GRAPHDB_PATH",
        required = true,
        description = "Path to the graph database")
    String graphDbPath;

    @CommandLine.Option(names = {"-d", "--database"},
        paramLabel = "DB_URL",
        description = "Database URL for connection",
        defaultValue = "jdbc:postgresql:fasten_java")
    String metadataDbUrl;

    @CommandLine.Option(names = {"-n", "--no-db"},
        paramLabel = "BOOL",
        description = "Flag for not using database",
        defaultValue = "false")
    Boolean isNotUsingDb;

    @CommandLine.Option(names = {"-u", "--user"},
        paramLabel = "DB_USER",
        description = "Database user name",
        defaultValue = "fastenro")
    String metadataDbUser;

    @CommandLine.Option(names = {"-a", "--artifactId"},
        paramLabel = "ARTIFACT",
        description = "The artifact id to check in the graph database")
    String artifactId;

    @CommandLine.Option(names = {"-o", "--outputPath"},
        paramLabel = "OUTPUT",
        description = "The path to the directory to write the ")
    String outDir;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new CallableIndexChecker()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        if (artifactId != null & outDir != null) {
            writeArtifact(artifactId, outDir);
        } else {
            List<Long> packageVersionIds = getInterestingPackageIds();
            RocksDao rocksDb = connectToReadOnlyRocksDB();
            logger.info("Retrieved package versions' IDs ({} in total)", packageVersionIds.size());

            var successfulDirectedGraph = 0;
            var successfulGraphMetadata = 0;

            for (var packageVersionId : packageVersionIds) {
                try {
                    logger.info("Retrieving data for package version ID {}", packageVersionId);

                    var graph = rocksDb.getGraphData(packageVersionId);
                    if (graph == null) {
                        logger.info("Cannot find graph data for package ID {}",
                            packageVersionId);
                        continue;
                    }
                    successfulDirectedGraph++;

                    var graphMetadata = rocksDb.getGraphMetadata(packageVersionId, graph);
                    if (graphMetadata == null) {
                        logger.info("Cannot find meta data for package version ID {}",
                            packageVersionId);
                        continue;
                    }
                    successfulGraphMetadata++;

                } catch (RocksDBException ignored) {
                    logger.error("Retrieving data for package version ID " + packageVersionId,
                        ignored);
                }
            }

            logger.info("Finished");
            if (!isNotUsingDb) {
                logger.info("Graph database contains {} out of {} directed graphs",
                    successfulDirectedGraph, packageVersionIds.size());
                logger.info("Graph database contains {} out of {} graph metadata objects",
                    successfulGraphMetadata, packageVersionIds.size());
            } else {
                logger.info("Graph database contains {} directed graphs", successfulDirectedGraph);
                logger.info("Graph database contains {} graph metadata objects",
                    successfulGraphMetadata);
            }
        }
    }

    private List<Long> getInterestingPackageIds() {
        List<Long> packageVersionIds;
        if (isNotUsingDb) {
            packageVersionIds = generateNPackageIds(10000000L);
        } else {
            var metadataDb = connectToPostgres();
            packageVersionIds = metadataDb
                .select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS)
                .fetch()
                .map(Record1::value1);
        }
        return packageVersionIds;
    }

    private List<Long> generateNPackageIds(long endInclusive) {
        return LongStream.rangeClosed(0L, endInclusive).boxed().collect(Collectors.toList());
    }

    private DSLContext connectToPostgres() {
        try {
            return PostgresConnector.getDSLContext(metadataDbUrl, metadataDbUser, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeArtifact(String artifactId, String outDir) {
        RocksDao rocksDb = connectToReadOnlyRocksDB();
        try {
            logger.info("Retrieving data for package version ID {}", artifactId);
            long packageVersionId = Long.parseLong(artifactId);
            var graph = rocksDb.getGraphData(packageVersionId);
            if (graph != null) {
                String delim = "";
                StringBuilder strGraph = new StringBuilder();
                for (LongLongPair longLongPair : graph.edgeSet()) {
                    strGraph.append(delim);
                    delim = ",";
                    strGraph.append(longLongPair);
                }
                CallGraphUtils.writeToFile(outDir + "/" + artifactId + ".directedGraph.txt",
                    strGraph.toString(), "");
                var graphMetadata = rocksDb.getGraphMetadata(packageVersionId, graph);
                delim = "";
                StringBuilder strMetadata = new StringBuilder();
                if (graphMetadata != null) {
                    for (final var entry :
                        graphMetadata.gid2NodeMetadata.long2ObjectEntrySet()) {
                        strMetadata.append(delim);
                        delim = ",";
                        strMetadata.append(entry.getLongKey()).append("->")
                            .append(entry.getValue().signature).append(",")
                            .append(entry.getValue().type).append(",")
                            .append(entry.getValue().receiverRecords).append("\n");
                    }
                    CallGraphUtils.writeToFile(outDir + "/" + artifactId + ".graphMetadata.txt",
                        strMetadata.toString(), "");
                } else {
                    logger.info("No metadata for the artifact {}", artifactId);
                }
            } else {
                logger.info("No graph for this artifact {}", artifactId);
            }
        } catch (RocksDBException | IOException ignored) {
        }
    }

    private RocksDao connectToReadOnlyRocksDB() {
        try {
            return RocksDBConnector.createReadOnlyRocksDBAccessObject(this.graphDbPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}