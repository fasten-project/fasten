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

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.graphdb.ExtendedGidGraph;
import eu.fasten.core.data.graphdb.GidGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.plugins.GraphDBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphDatabasePlugin extends Plugin {

    public GraphDatabasePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class GraphDBExtension implements KafkaPlugin, GraphDBConnector {

        private String consumerTopic = "fasten.MetadataDBExtension.out";
        private Exception pluginError = null;
        private final Logger logger = LoggerFactory.getLogger(GraphDBExtension.class.getName());
        private static RocksDao rocksDao;
        private String outputPath;

        public void setRocksDao(RocksDao rocksDao) {
            GraphDBExtension.rocksDao = rocksDao;
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return this.outputPath;
        }

        @Override
        public void consume(String record) {
            this.pluginError = null;
            var json = new JSONObject(record);
            if (json.has("payload")) {
                if (json.get("payload").toString().isEmpty()) {
                    logger.error("Empty payload");
                    setPluginError(new RuntimeException("Empty payload"));
                    return;
                }
                json = json.getJSONObject("payload");
            }
            final var path = json.optString("dir");

            final GidGraph gidGraph;
            if (!path.isEmpty()) {
                try {
                    JSONTokener tokener = new JSONTokener(new FileReader(path));
                    gidGraph = GidGraph.getGraph(new JSONObject(tokener));
                } catch (JSONException e) {
                    logger.error("Could not parse GID graph", e);
                    setPluginError(e);
                    return;
                } catch (FileNotFoundException e) {
                    logger.error("Error parsing JSON callgraph for '"
                            + Paths.get(path).getFileName() + "'", e);
                    setPluginError(e);
                    return;
                }
            } else {
                try {
                    if (json.has("callsites_info")) {
                        gidGraph = ExtendedGidGraph.getGraph(json);
                    } else {
                        gidGraph = GidGraph.getGraph(json);
                    }
                } catch (JSONException e) {
                    logger.error("Could not parse GID graph", e);
                    setPluginError(e);
                    return;
                }
            }

            var artifact = gidGraph.getProduct() + "@" + gidGraph.getVersion();

            final String groupId;
            final String artifactId;
            if (gidGraph.getProduct().contains(Constants.mvnCoordinateSeparator)) {
                groupId = gidGraph.getProduct().split(Constants.mvnCoordinateSeparator)[0];
                artifactId = gidGraph.getProduct().split(Constants.mvnCoordinateSeparator)[1];
            } else {
                final var productParts = gidGraph.getProduct().split("\\.");
                groupId = String.join(".", Arrays.copyOf(productParts, productParts.length - 1));
                artifactId = productParts[productParts.length - 1];
            }
            var version = gidGraph.getVersion();
            var product = artifactId + "_" + groupId + "_" + version;

            var firstLetter = artifactId.substring(0, 1);

            outputPath = File.separator + firstLetter + File.separator
                    + artifactId + File.separator + product + ".json";
            try {
                rocksDao.saveToRocksDb(gidGraph);
            } catch (Exception e) {
                logger.error("Could not save GID graph of '" + artifact + "' into RocksDB", e);
                setPluginError(e);
                return;
            }
            if (getPluginError() == null) {
                logger.info("Saved the '" + artifact
                        + "' GID graph into RocksDB graph database with index "
                        + gidGraph.getIndex());
            }
        }

        @Override
        public String name() {
            return "Graph plugin";
        }

        @Override
        public String description() {
            return "Graph plugin. "
                    + "Consumes list of edges (pair of global IDs produced by PostgreSQL from Kafka"
                    + " topic and populates graph database (RocksDB) with consumed data";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
            rocksDao.close();
            rocksDao = null;
        }

        public void setPluginError(Exception throwable) {
            this.pluginError = throwable;
        }

        @Override
        public Exception getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {
            rocksDao.close();
            rocksDao = null;
        }
    }
}
