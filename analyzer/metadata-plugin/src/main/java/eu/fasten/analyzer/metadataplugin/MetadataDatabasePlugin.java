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
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONException;
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
        private boolean restartTransaction = false;

        public MetadataPlugin() throws IOException, SQLException, IllegalArgumentException {
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
            this.restartTransaction = false;
            this.pluginError = "";
            do {
                try {
                    var metadataDao = new MetadataDao(this.dslContext);
                    this.dslContext.transaction(transaction -> {
                        metadataDao.setContext(DSL.using(transaction));
                        try {
                            saveToDatabase(consumedJson, metadataDao);
                        } catch (RuntimeException e) {
                            logger.error("Error saving to the database", e);
                            processedRecord = false;
                            setPluginError(e);
                            if (e instanceof DataAccessException) {
                                logger.info("Restarting transaction");
                                restartTransaction = true;
                            } else {
                                restartTransaction = false;
                            }
                            throw e;
                        }
                        if (getPluginError().isEmpty()) {
                            processedRecord = true;
                            restartTransaction = false;
                            logger.info("Saved the callgraph metadata to the database");
                        }
                    });
                } catch (Exception expected) {
                }
            } while (restartTransaction && !processedRecord);
        }

        /**
         * Saves consumed JSON to the database to appropriate tables.
         *
         * @param json        JSON Object consumed by Kafka
         * @param metadataDao Data Access Object to insert records in the database
         */
        public void saveToDatabase(JSONObject json, MetadataDao metadataDao) {
            var packageName = json.getString("product");
            var forge = json.getString("forge");
            var project = json.optString("project", null);
            var repository = json.optString("repository", null);
            var timestamp = json.has("timestamp") ? new Timestamp(json.getLong("timestamp"))
                    : null;
            long packageId = metadataDao
                    .insertPackage(packageName, forge, project, repository, timestamp);

            var generator = json.getString("generator");
            var version = json.getString("version");

            long packageVersionId = metadataDao.insertPackageVersion(packageId, generator,
                    version, null, null);

            var depset = json.getJSONArray("depset").optJSONArray(0);
            if (depset != null) {
                var depIds = new ArrayList<Long>(depset.length());
                var depVersions = new ArrayList<String[]>(depset.length());
                for (int i = 0; i < depset.length(); i++) {
                    var dependency = depset.getJSONObject(i);
                    var depName = dependency.getString("product");
                    var depForge = dependency.getString("forge");
                    var constraints = dependency.getJSONArray("constraints");
                    var versionRange = new String[constraints.length()];
                    for (int j = 0; j < constraints.length(); j++) {
                        versionRange[j] = constraints.getString(j);
                    }
                    depVersions.add(versionRange);

                    var depId = metadataDao.getPackageIdByNameAndForge(depName, depForge);
                    if (depId == -1L) {
                        depId = metadataDao.insertPackage(depName, depForge, null, null, null);
                    }
                    depIds.add(depId);
                }
                metadataDao.insertDependencies(packageVersionId, depIds, depVersions);
            }
            var cha = json.getJSONObject("cha");
            var fileNames = new ArrayList<String>(cha.keySet().size());
            cha.keys().forEachRemaining(fileNames::add);
            var globalIdsMap = new HashMap<Long, Long>();
            for (var file : fileNames) {
                var fileJson = cha.getJSONObject(file);
                var metadata = new JSONObject();
                metadata.put("superInterfaces", fileJson.getJSONArray("superInterfaces"));
                metadata.put("sourceFile", fileJson.getString("sourceFile"));
                metadata.put("superClasses", fileJson.getJSONArray("superClasses"));
                String namespace = file.split("/")[1];
                long fileId = metadataDao.insertFile(packageVersionId, namespace, null, null,
                        metadata);
                var methods = fileJson.getJSONObject("methods");
                var methodIds = new ArrayList<String>(methods.keySet().size());
                methods.keys().forEachRemaining(methodIds::add);
                for (var method : methodIds) {
                    var uri = methods.getString(method);
                    long callableId = metadataDao.insertCallable(fileId, uri, true, null,
                            null);
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

            var unresolvedCalls = graph.getJSONArray("unresolvedCalls");
            for (int i = 0; i < unresolvedCalls.length(); i++) {
                var unresolvedCall = unresolvedCalls.getJSONArray(i);
                var sourceLocalId = Long.parseLong(unresolvedCall.getString(0));
                var sourceGlobalId = globalIdsMap.get(sourceLocalId);
                var uri = unresolvedCall.getString(1);
                var metadata = unresolvedCall.getJSONObject(2);
                var targetId = metadataDao.insertCallable(null, uri, false, null, null);
                metadataDao.insertEdge(sourceGlobalId, targetId, metadata);
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
