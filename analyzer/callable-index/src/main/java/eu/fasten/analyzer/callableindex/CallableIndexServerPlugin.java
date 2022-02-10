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

package eu.fasten.analyzer.callableindex;

import eu.fasten.core.data.callableindex.ExtendedGidGraph;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.plugins.CallableIndexConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class CallableIndexServerPlugin extends Plugin {

    public CallableIndexServerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class CallableIndexFastenPlugin implements KafkaPlugin, CallableIndexConnector {

        private List<String> consumeTopics = new LinkedList<>(List.of("fasten.MetadataDBJavaExtension.priority.out",
                "fasten.MetadataDBExtension.out"));
        private Exception pluginError = null;
        private final Logger logger = LoggerFactory.getLogger(CallableIndexFastenPlugin.class.getName());
        private static RocksDao rocksDao;
        private long packageVersionID;

        public void setRocksDao(RocksDao rocksDao) {
            CallableIndexFastenPlugin.rocksDao = rocksDao;
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(consumeTopics);
        }

        @Override
        public void setTopics(List<String> consumeTopics) {
            this.consumeTopics = consumeTopics;
        }

        @Override
        public Optional<String> produce() {
            final var output = new JSONObject();
            output.put("packageVersionID", String.valueOf(this.packageVersionID));
            return Optional.of(output.toString());
        }

        @Override
        public String getOutputPath() {
            return null;
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

            final ExtendedGidGraph extendedGidGraph;

            if (path.isEmpty()) {
                throw new RuntimeException("Provided path to GID graph file is empty");
            }

            try {
                JSONTokener tokener = new JSONTokener(new FileReader(path));
                extendedGidGraph = ExtendedGidGraph.getGraph(new JSONObject(tokener));
            } catch (JSONException e) {
                logger.error("Could not parse GID graph", e);
                throw e;
            } catch (FileNotFoundException e) {
                logger.error("The JSON GID graph for '"
                        + Paths.get(path).getFileName() + "'", e);
                throw new RuntimeException("Couldn't find the GID graph at" + Paths.get(path).getFileName() + "on the FS");
            }

            var artifact = extendedGidGraph.getProduct() + "@" + extendedGidGraph.getVersion();

            try {
                rocksDao.saveToRocksDb(extendedGidGraph);
                this.packageVersionID = extendedGidGraph.getIndex();
                logger.info("Saved package version {} in callable index.", this.packageVersionID);

            } catch (Exception e) {
                logger.error("Could not save GID graph of '" + artifact + "' into RocksDB", e);
                throw new RuntimeException("Could not save GID graph of '" + artifact + "' into RocksDB");
            }
            if (getPluginError() == null) {
                logger.info("Saved the '" + artifact
                        + "' GID graph into RocksDB graph database with index "
                        + extendedGidGraph.getIndex());
            }
        }

        @Override
        public String name() {
            return "Graph plugin";
        }

        @Override
        public String description() {
            return "Callable index plugin. "
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
