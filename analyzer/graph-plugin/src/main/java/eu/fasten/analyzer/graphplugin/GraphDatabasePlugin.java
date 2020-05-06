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

import eu.fasten.analyzer.graphplugin.db.RocksDao;
import eu.fasten.core.data.graphdb.GidGraph;
import eu.fasten.core.plugins.KafkaConsumer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONException;
import org.json.JSONObject;
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
    public static class GraphDBExtension implements KafkaConsumer<String> {

        private String consumerTopic = "fasten.cg.gid_graphs";
        private boolean processedRecord = false;
        private String pluginError = "";
        private final Logger logger = LoggerFactory.getLogger(GraphDBExtension.class.getName());
        private String rocksDbDir = "graphDB";

        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList(consumerTopic));
        }

        public void setRocksDbDir(String dir) {
            this.rocksDbDir = dir;
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> record) {
            var json = new JSONObject(record.value());
            GidGraph gidGraph;
            try {
                gidGraph = GidGraph.getGraph(json);
            } catch (JSONException e) {
                logger.error("Could not parse GID graph", e);
                processedRecord = false;
                setPluginError(e);
                return;
            }
            var artifact = gidGraph.getProduct() + "@" + gidGraph.getVersion();
            try {
                var rocksDao = new RocksDao(rocksDbDir);
                saveToDatabase(gidGraph, rocksDao);
            } catch (RocksDBException | IOException e) {
                logger.error("Could not save GID graph of '" + artifact + "' into RocksDB", e);
                processedRecord = false;
                setPluginError(e);
                return;
            }
            if (getPluginError().isEmpty()) {
                processedRecord = true;
                logger.info("Saved the '" + artifact + "' GID graph into RocksDB graph database");
            }
        }

        /**
         * Inserts a graph into RocksDB.
         *
         * @param gidGraph Graph with Global IDs
         * @param rocksDao Database Access Object for RocksDB
         * @throws IOException      if there was a problem writing to files
         * @throws RocksDBException if there was a problem inserting in the database
         */
        public void saveToDatabase(GidGraph gidGraph, RocksDao rocksDao)
                throws IOException, RocksDBException {
            rocksDao.saveToRocksDb(gidGraph.getIndex(), gidGraph.getNodes(), gidGraph.getNumInternalNodes(),
                    gidGraph.getEdges());
        }

        @Override
        public boolean recordProcessSuccessful() {
            return this.processedRecord;
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
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void setPluginError(Throwable throwable) {
            this.pluginError =
                    new JSONObject().put("plugin", this.getClass().getSimpleName()).put("msg",
                            throwable.getMessage()).put("trace", throwable.getStackTrace())
                            .put("type", throwable.getClass().getSimpleName()).toString();
        }

        @Override
        public String getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }
    }
}
