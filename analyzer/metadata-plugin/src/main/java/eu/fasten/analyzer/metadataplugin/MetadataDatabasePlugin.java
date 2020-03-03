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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jooq.DSLContext;
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

        private DSLContext dslContext;
        private boolean processedRecord = false;
        private String pluginError = "";
        private final Logger logger = LoggerFactory.getLogger(MetadataPlugin.class.getName());

        public MetadataPlugin() throws IOException, SQLException {
            this(PostgresConnector.getDSLContext());
        }

        public MetadataPlugin(DSLContext dslContext) {
            super();
            this.dslContext = dslContext;
        }

        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList("opal_callgraphs"));
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> record) {
            var consumedJson = new JSONObject(record.value());
            this.processedRecord = false;
            this.pluginError = "";
            var metadataDao = new MetadataDao(this.dslContext);
            saveToDatabase(consumedJson, metadataDao);
        }

        /**
         * Saves consumed JSON to the database to appropriate tables.
         *
         * @param json        JSON Object consumed by Kafka
         * @param metadataDao Data Access Object to insert records in the database.
         */
        public void saveToDatabase(JSONObject json, MetadataDao metadataDao) {
            boolean saved = false;
            try {
                var packageName = json.getString("product");
                var project = json.has("project") ? json.getString("project") : null;
                var repository = json.has("repository") ? json.getString("repository") : null;
                var timestamp = json.has("timestamp") ? new Timestamp(json.getLong("timestamp"))
                        : null;
                long packageId = metadataDao
                        .insertPackage(packageName, project, repository, timestamp);

                var generator = json.getString("generator");
                var version = json.getString("version");

                long packageVersionId = metadataDao.insertPackageVersion(packageId, generator,
                        version, timestamp, null);

                var depset = json.getJSONArray("depset").optJSONArray(0);
                if (depset != null) {
                    var depIds = new ArrayList<Long>(depset.length());
                    var depVersions = new ArrayList<String>(depset.length());
                    for (int i = 0; i < depset.length(); i++) {
                        // TODO: Check if dependency is already in the database to avoid duplicates
                        var dependency = depset.getJSONObject(i);
                        var depName = dependency.getString("product");
                        var depVersion = dependency.getJSONArray("constraints").getString(0);
                        depVersions.add(depVersion);
                        long depPackageId = metadataDao.insertPackage(depName, null, null, null);
                        depIds.add(metadataDao.insertPackageVersion(depPackageId, generator,
                                depVersion, null, null));
                    }
                    metadataDao.insertDependencies(packageId, depIds, depVersions);
                }

                var cha = json.getJSONObject("cha");
                var fileNames = new ArrayList<String>(cha.keySet().size());
                cha.keys().forEachRemaining(fileNames::add);
                var globalIdsMap = new HashMap<Long, Long>();
                for (var file : fileNames) {
                    var fileJson = cha.getJSONObject(file);
                    var metadata = new JSONObject();
                    metadata.append("superInterfaces", fileJson.getJSONArray("superInterfaces"));
                    metadata.append("sourceFile", fileJson.getString("sourceFile"));
                    metadata.append("superClasses", fileJson.getJSONArray("superClasses"));
                    long fileId = metadataDao.insertFile(packageVersionId, file, null, null,
                            metadata);
                    var methods = fileJson.getJSONObject("methods");
                    var methodIds = new ArrayList<String>(methods.keySet().size());
                    methods.keys().forEachRemaining(methodIds::add);
                    for (var method : methodIds) {
                        var uri = methods.getString(method);
                        long callableId = metadataDao.insertCallable(fileId, uri, null, null);
                        globalIdsMap.put(Long.parseLong(method), callableId);
                    }
                }

                var graph = json.getJSONObject("graph");
                var resolvedCalls = graph.getJSONArray("resolvedCalls");
                for (int i = 0; i < resolvedCalls.length(); i++) {
                    var resolvedCall = resolvedCalls.getJSONArray(i);
                    var sourceLocalId = resolvedCall.getLong(0);
                    var targetLocalId = resolvedCall.getLong(1);
                    var sourceGlobalId = globalIdsMap.get(sourceLocalId);
                    var targetGlobalId = globalIdsMap.get(targetLocalId);
                    metadataDao.insertEdge(sourceGlobalId, targetGlobalId, null);
                }

                // TODO: Save unresolved calls
                // var unresolvedCalls = graph.getJSONObject("unresolvedCalls");


                saved = true;
            } catch (Exception e) {
                logger.error("Error saving to the database", e);
                setPluginError(e);
            }
            if (saved && getPluginError().isEmpty()) {
                processedRecord = true;
                logger.info("Saved the callgraph metadata to the database");
            } else {
                processedRecord = false;
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
