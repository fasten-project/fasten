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

package eu.fasten.analyzer.metadataplugin;

import eu.fasten.analyzer.metadataplugin.db.MetadataDao;
import eu.fasten.analyzer.metadataplugin.db.PostgresConnector;
import eu.fasten.core.plugins.KafkaConsumer;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataDatabasePlugin extends Plugin {

    public MetadataDatabasePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MetadataPlugin implements KafkaConsumer<String> {

        private boolean processedRecord = false;
        private String pluginError = "";
        private final Logger logger = LoggerFactory.getLogger(MetadataPlugin.class.getName());

        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList("opal_callgraphs"));
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> record) {
            var consumedJson = new JSONObject(record.value());
            this.processedRecord = false;
            this.pluginError = "";
            try {
                var metadataDao = new MetadataDao(PostgresConnector.getDSLContext());
                saveToDatabase(consumedJson, metadataDao);
            } catch (SQLException e) {
                this.processedRecord = false;
                setPluginError(e);
                logger.error("Could not connect to the database", e);
            } catch (IOException e) {
                this.processedRecord = false;
                setPluginError(e);
                logger.error("Could not find 'postgres.properties' file with database connection "
                        + "parameters", e);
            }
        }

        /**
         * Saves consumed JSON to the database to appropriate tables.
         *
         * @param json JSON Object consumed by Kafka
         * @param metadataDao Data Access Object to insert records in the database.
         */
        public void saveToDatabase(JSONObject json, MetadataDao metadataDao) {
            boolean saved = false;
            // TODO: Insert consumed data in the metadata database using metadataDao

            if (saved && getPluginError().isEmpty()) {
                processedRecord = true;
                logger.info("Saved the callgraph metadata to the database");
            }
        }

        @Override
        public boolean recordProcessSuccessful() {
            return this.processedRecord;
        }

        @Override
        public String name() {
            return "Metadata plugin";
        }

        @Override
        public String description() {
            return "Metadata plugin. "
                    + "Consumes kafka topic and populates metadata database with consumed data.";
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
            System.out.println(this.pluginError);
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
