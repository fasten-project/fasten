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
import eu.fasten.core.data.ExtendedRevisionCallGraph;
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

        private final String topic = "opal_callgraphs";
        private final int transactionRestartLimit = 3;

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
            return new ArrayList<>(Collections.singletonList(topic));
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> record) {
            var consumedJson = new JSONObject(record.value());
            this.processedRecord = false;
            this.restartTransaction = false;
            this.pluginError = "";
            int transactionRestartCount = 0;
            do {
                try {
                    var callgraph = new ExtendedRevisionCallGraph(consumedJson);
                    var metadataDao = new MetadataDao(this.dslContext);
                    this.dslContext.transaction(transaction -> {
                        metadataDao.setContext(DSL.using(transaction));
                        try {
                            saveToDatabase(callgraph, metadataDao);
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
                } catch (JSONException e) {
                    logger.error("Error parsing JSON callgraph", e);
                    processedRecord = false;
                    setPluginError(e);
                    return;
                } catch (Exception expected) {
                }
                transactionRestartCount++;
            } while (restartTransaction && !processedRecord
                    && transactionRestartCount < transactionRestartLimit);
        }

        /**
         * Saves a callgraph to the database to appropriate tables.
         *
         * @param callGraph   Call graph to save to the database.
         * @param metadataDao Data Access Object to insert records in the database
         */
        public void saveToDatabase(ExtendedRevisionCallGraph callGraph, MetadataDao metadataDao) {
            var timestamp = (callGraph.timestamp != -1) ? new Timestamp(callGraph.timestamp) : null;
            long packageId = metadataDao.insertPackage(callGraph.product, callGraph.forge, null,
                    null, timestamp);

            long packageVersionId = metadataDao.insertPackageVersion(packageId,
                    callGraph.getCgGenerator(), callGraph.version, null, null);

            var depIds = new ArrayList<Long>();
            var depVersions = new ArrayList<String[]>();
            for (var depList : callGraph.depset) {
                for (var dependency : depList) {
                    var constraints = dependency.constraints;
                    var versions = new String[constraints.size()];
                    for (int i = 0; i < constraints.size(); i++) {
                        versions[i] = constraints.get(i).lowerBound + ".."
                                + constraints.get(i).upperBound;
                    }
                    depVersions.add(versions);

                    var depId = metadataDao.getPackageIdByNameAndForge(dependency.product,
                            dependency.forge);
                    if (depId == -1L) {
                        depId = metadataDao.insertPackage(dependency.product, dependency.forge,
                                null, null, null);
                    }
                    depIds.add(depId);
                }
            }
            if (depIds.size() > 0) {
                metadataDao.insertDependencies(packageVersionId, depIds, depVersions);
            }

            var cha = callGraph.getClassHierarchy();
            var globalIdsMap = new HashMap<Integer, Long>();
            for (var fastenUri : cha.keySet()) {
                var type = cha.get(fastenUri);
                var fileMetadata = new JSONObject();
                fileMetadata.put("superInterfaces",
                        ExtendedRevisionCallGraph.Type.toListOfString(type.getSuperInterfaces()));
                fileMetadata.put("sourceFile", type.getSourceFileName());
                fileMetadata.put("superClasses",
                        ExtendedRevisionCallGraph.Type.toListOfString(type.getSuperClasses()));
                long fileId = metadataDao.insertFile(packageVersionId, fastenUri.getNamespace(),
                        null, null, fileMetadata);
                for (var methodEntry : type.getMethods().entrySet()) {
                    var uri = methodEntry.getValue().toString();
                    long callableId = metadataDao.insertCallable(fileId, uri, true, null, null);
                    globalIdsMap.put(methodEntry.getKey(), callableId);
                }
            }

            var graph = callGraph.getGraph();

            var internalCalls = graph.getInternalCalls();
            for (var call : internalCalls) {
                var sourceLocalId = call.get(0);
                var targetLocalId = call.get(1);
                var sourceGlobalId = globalIdsMap.get(sourceLocalId);
                var targetGlobalId = globalIdsMap.get(targetLocalId);
                metadataDao.insertEdge(sourceGlobalId, targetGlobalId, null);
            }

            var externalCalls = graph.getExternalCalls();
            for (var callEntry : externalCalls.entrySet()) {
                var call = callEntry.getKey();
                var sourceLocalId = call.getKey();
                var sourceGlobalId = globalIdsMap.get(sourceLocalId);
                var uri = call.getValue().toString();
                var targetId = metadataDao.insertCallable(null, uri, false, null, null);
                var edgeMetadata = new JSONObject(callEntry.getValue());
                metadataDao.insertEdge(sourceGlobalId, targetId, edgeMetadata);
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
