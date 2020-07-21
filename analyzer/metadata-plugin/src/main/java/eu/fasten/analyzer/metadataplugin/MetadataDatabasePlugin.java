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

import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.data.graphdb.GidGraph;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
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
    public static class MetadataDBExtension implements KafkaPlugin, DBConnector {

        private String consumerTopic = "fasten.OPAL.out";
        private static DSLContext dslContext;
        private boolean processedRecord = false;
        private Throwable pluginError = null;
        private final Logger logger = LoggerFactory.getLogger(MetadataDBExtension.class.getName());
        private boolean restartTransaction = false;
        private final int transactionRestartLimit = 3;
        private GidGraph gidGraph = null;
        private String outputPath;

        @Override
        public void setDBConnection(DSLContext dslContext) {
            MetadataDBExtension.dslContext = dslContext;
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
        public void consume(String record) {
            this.processedRecord = false;
            this.restartTransaction = false;
            this.pluginError = null;
            var consumedJson = new JSONObject(record);
            if (consumedJson.has("payload")) {
                consumedJson = consumedJson.getJSONObject("payload");
            }
            final var path = consumedJson.optString("dir");
            if (consumedJson.has("depset")) {
                consumeOldFormat(consumedJson, path);
            } else {
                consumeNewFormat(consumedJson, path);
            }
        }

        /**
         * Consumes callgraph record of the old format.
         *
         * @param consumedJson JSON of the consumed record
         * @param path         Path where the record is stored
         */
        private void consumeOldFormat(JSONObject consumedJson, String path) {
            final RevisionCallGraph callgraph;
            if (!path.isEmpty()) {
                try {
                    JSONTokener tokener = new JSONTokener(new FileReader(path));
                    callgraph = new RevisionCallGraph(new JSONObject(tokener));
                } catch (JSONException | FileNotFoundException e) {
                    logger.error("Error parsing JSON callgraph from path for '"
                            + Paths.get(path).getFileName() + "'", e);
                    processedRecord = false;
                    setPluginError(e);
                    return;
                }
            } else {
                try {
                    callgraph = new RevisionCallGraph(consumedJson);
                } catch (JSONException e) {
                    logger.error("Error parsing JSON callgraph for '"
                            + Paths.get(path).getFileName() + "'", e);
                    processedRecord = false;
                    setPluginError(e);
                    return;
                }
            }
            final var artifact = callgraph.product + "@" + callgraph.version;
            final String groupId;
            final String artifactId;
            if (callgraph.product.contains(":")) {
                groupId = callgraph.product.split(":")[0];
                artifactId = callgraph.product.split(":")[1];
            } else {
                final var productParts = callgraph.product.split("\\.");
                groupId = String.join(".", Arrays.copyOf(productParts, productParts.length - 1));
                artifactId = productParts[productParts.length - 1];
            }
            var version = callgraph.version;
            var forge = callgraph.forge;
            var product = artifactId + "_" + groupId + "_" + version;
            var firstLetter = artifactId.substring(0, 1);
            outputPath = File.separator + forge + File.separator
                    + firstLetter + File.separator
                    + artifactId + File.separator + product + ".json";
            int transactionRestartCount = 0;
            do {
                try {
                    var metadataDao = new MetadataDao(dslContext);
                    dslContext.transaction(transaction -> {
                        metadataDao.setContext(DSL.using(transaction));
                        long id;
                        try {
                            id = saveToDatabaseOldFormat(callgraph, metadataDao);
                        } catch (RuntimeException e) {
                            logger.error("Error saving to the database: '" + artifact + "'", e);
                            processedRecord = false;
                            setPluginError(e);
                            if (e instanceof DataAccessException) {
                                logger.info("Restarting transaction for '" + artifact + "'");
                                restartTransaction = true;
                            } else {
                                restartTransaction = false;
                            }
                            throw e;
                        }
                        if (getPluginError() == null) {
                            processedRecord = true;
                            restartTransaction = false;
                            logger.info("Saved the '" + artifact + "' callgraph metadata "
                                    + "to the database with package version ID = " + id);
                        }
                    });
                } catch (Exception expected) {
                }
                transactionRestartCount++;
            } while (restartTransaction && !processedRecord
                    && transactionRestartCount < transactionRestartLimit);
        }

        /**
         * Consumes callgraph record of the new format.
         *
         * @param consumedJson JSON of the consumed record
         * @param path         Path where the record is stored
         */
        private void consumeNewFormat(JSONObject consumedJson, String path) {
            final ExtendedRevisionCallGraph callgraph;
            if (!path.isEmpty()) {
                try {
                    JSONTokener tokener = new JSONTokener(new FileReader(path));
                    callgraph = new ExtendedRevisionCallGraph(new JSONObject(tokener));
                } catch (JSONException | IOException e) {
                    logger.error("Error parsing JSON callgraph from path for '"
                            + Paths.get(path).getFileName() + "'", e);
                    processedRecord = false;
                    setPluginError(e);
                    return;
                }
            } else {
                try {
                    callgraph = new ExtendedRevisionCallGraph(consumedJson);
                } catch (JSONException | IOException e) {
                    logger.error("Error parsing JSON callgraph for '"
                            + Paths.get(path).getFileName() + "'", e);
                    processedRecord = false;
                    setPluginError(e);
                    return;
                }
            }
            final var artifact = callgraph.product + "@" + callgraph.version;
            final String groupId;
            final String artifactId;
            if (callgraph.product.contains(":")) {
                groupId = callgraph.product.split(":")[0];
                artifactId = callgraph.product.split(":")[1];
            } else {
                final var productParts = callgraph.product.split("\\.");
                groupId = String.join(".", Arrays.copyOf(productParts, productParts.length - 1));
                artifactId = productParts[productParts.length - 1];
            }
            var version = callgraph.version;
            var forge = callgraph.forge;
            var product = artifactId + "_" + groupId + "_" + version;

            var firstLetter = artifactId.substring(0, 1);

            outputPath = File.separator + forge + File.separator
                    + firstLetter + File.separator
                    + artifactId + File.separator + product + ".json";

            int transactionRestartCount = 0;
            do {
                try {
                    var metadataDao = new MetadataDao(dslContext);
                    dslContext.transaction(transaction -> {
                        metadataDao.setContext(DSL.using(transaction));
                        long id;
                        try {
                            id = saveToDatabaseNewFormat(callgraph, metadataDao);
                        } catch (RuntimeException e) {
                            logger.error("Error saving to the database: '" + artifact + "'", e);
                            processedRecord = false;
                            setPluginError(e);
                            if (e instanceof DataAccessException) {
                                logger.info("Restarting transaction for '" + artifact + "'");
                                restartTransaction = true;
                            } else {
                                restartTransaction = false;
                            }
                            throw e;
                        }
                        if (getPluginError() == null) {
                            processedRecord = true;
                            restartTransaction = false;
                            logger.info("Saved the '" + artifact + "' callgraph metadata "
                                    + "to the database with package version ID = " + id);
                        }
                    });
                } catch (Exception expected) {
                }
                transactionRestartCount++;
            } while (restartTransaction && !processedRecord
                    && transactionRestartCount < transactionRestartLimit);
        }

        @Override
        public Optional<String> produce() {
            if (gidGraph == null) {
                return Optional.empty();
            } else {
                return Optional.of(gidGraph.toJSONString());
            }
        }

        @Override
        public String getOutputPath() {
            return outputPath;
        }

        /**
         * Saves a callgraph of olf format to the database to appropriate tables.
         *
         * @param callGraph   Call graph to save to the database.
         * @param metadataDao Data Access Object to insert records in the database
         * @return Package ID saved in the database
         */
        public long saveToDatabaseOldFormat(RevisionCallGraph callGraph, MetadataDao metadataDao) {
            final var timestamp = this.getProperTimestamp(callGraph.timestamp);
            final long packageId = metadataDao.insertPackage(callGraph.product, callGraph.forge,
                    null, null, null);
            final long packageVersionId = metadataDao.insertPackageVersion(packageId,
                    callGraph.getCgGenerator(), callGraph.version, timestamp, null);
            var depIds = new ArrayList<Long>();
            var depVersions = new ArrayList<String[]>();
            var depMetadata = new ArrayList<JSONObject>();
            for (var depList : callGraph.depset) {
                for (var dependency : depList) {
                    var constraints = dependency.constraints;
                    var versions = new String[constraints.size()];
                    for (int i = 0; i < constraints.size(); i++) {
                        versions[i] = constraints.get(i).toString();
                    }
                    depVersions.add(versions);
                    var depId = metadataDao.insertPackage(dependency.product, dependency.forge,
                            null, null, null);
                    depIds.add(depId);
                    depMetadata.add(new JSONObject());
                }
            }
            if (depIds.size() > 0) {
                metadataDao.insertDependencies(packageVersionId, depIds, depVersions, depMetadata);
            }
            final var cha = callGraph.getClassHierarchy();
            var internalCallables = new ArrayList<CallablesRecord>();
            for (var fastenUri : cha.keySet()) {
                var type = cha.get(fastenUri);
                var moduleMetadata = new JSONObject();
                moduleMetadata.put("superInterfaces",
                        RevisionCallGraph.Type.toListOfString(type.getSuperInterfaces()));
                moduleMetadata.put("superClasses",
                        RevisionCallGraph.Type.toListOfString(type.getSuperClasses()));
                long moduleId = metadataDao.insertModule(packageVersionId, fastenUri.toString(),
                        null, moduleMetadata);
                var fileName = type.getSourceFileName();
                var fileId = metadataDao.insertFile(packageVersionId, fileName, null, null, null);
                metadataDao.insertModuleContent(moduleId, fileId);
                for (var methodEntry : type.getMethods().entrySet()) {
                    var localId = (long) methodEntry.getKey();
                    var uri = methodEntry.getValue().toString();
                    internalCallables.add(new CallablesRecord(localId, moduleId, uri, true,
                            null, null));
                }
            }
            final var graph = callGraph.getGraph();
            var internalEdges = new ArrayList<EdgesRecord>(graph.getInternalCalls().size());
            final var internalCalls = graph.getInternalCalls();
            for (var call : internalCalls) {
                var sourceLocalId = (long) call.get(0);
                var targetLocalId = (long) call.get(1);
                internalEdges.add(
                        new EdgesRecord(sourceLocalId, targetLocalId, JSONB.valueOf("{}")));
            }
            var nodes = new LinkedList<Long>();
            final var externalCalls = graph.getExternalCalls();
            var externalEdges = new ArrayList<EdgesRecord>(graph.getExternalCalls().size());
            for (var callEntry : externalCalls.entrySet()) {
                var call = callEntry.getKey();
                var sourceLocalId = (long) call.getKey();
                var uri = call.getValue().toString();
                var targetId = metadataDao.insertCallable(-1L, uri, false, null, null);
                nodes.add(targetId);
                var edgeMetadata = new JSONObject(callEntry.getValue());
                externalEdges.add(new EdgesRecord(sourceLocalId, targetId,
                        JSONB.valueOf(edgeMetadata.toString())));
            }
            final int batchSize = 4096;
            var internalCallablesIds = new ArrayList<Long>(internalCallables.size());
            final var internalCallablesIterator = internalCallables.iterator();
            while (internalCallablesIterator.hasNext()) {
                var callablesBatch = new ArrayList<CallablesRecord>(batchSize);
                while (internalCallablesIterator.hasNext() && callablesBatch.size() < batchSize) {
                    callablesBatch.add(internalCallablesIterator.next());
                }
                var ids = metadataDao.batchInsertCallables(callablesBatch);
                internalCallablesIds.addAll(ids);
            }
            for (var internalId : internalCallablesIds) {
                nodes.addFirst(internalId);
            }
            var internalLidToGidMap = new HashMap<Long, Long>();
            for (int i = 0; i < internalCallables.size(); i++) {
                internalLidToGidMap.put(internalCallables.get(i).getId(),
                        internalCallablesIds.get(i));
            }
            for (var edge : internalEdges) {
                edge.setSourceId(internalLidToGidMap.get(edge.getSourceId()));
                edge.setTargetId(internalLidToGidMap.get(edge.getTargetId()));
            }
            for (var edge : externalEdges) {
                edge.setSourceId(internalLidToGidMap.get(edge.getSourceId()));
            }
            var edges = new ArrayList<EdgesRecord>(graph.size());
            edges.addAll(internalEdges);
            edges.addAll(externalEdges);
            final var edgesIterator = edges.iterator();
            while (edgesIterator.hasNext()) {
                var edgesBatch = new ArrayList<EdgesRecord>(batchSize);
                while (edgesIterator.hasNext() && edgesBatch.size() < batchSize) {
                    edgesBatch.add(edgesIterator.next());
                }
                metadataDao.batchInsertEdges(edgesBatch);
            }
            this.gidGraph = new GidGraph(packageVersionId, callGraph.product, callGraph.version,
                    nodes, internalCallablesIds.size(), edges);
            return packageVersionId;
        }

        /**
         * Saves a callgraph of new format to the database to appropriate tables.
         *
         * @param callGraph   Call graph to save to the database.
         * @param metadataDao Data Access Object to insert records in the database
         * @return Package ID saved in the database
         */
        public long saveToDatabaseNewFormat(ExtendedRevisionCallGraph callGraph,
                                            MetadataDao metadataDao) {
            final var timestamp = this.getProperTimestamp(callGraph.timestamp);
            final long packageId = metadataDao.insertPackage(callGraph.product, callGraph.forge,
                    null, null, null);
            final long packageVersionId = metadataDao.insertPackageVersion(packageId,
                    callGraph.getCgGenerator(), callGraph.version, timestamp, null);
            var cha = callGraph.getClassHierarchy();
            var internalTypes = cha.get(ExtendedRevisionCallGraph.Scope.internalTypes);
            var callables = new ArrayList<CallablesRecord>();
            for (var fastenUri : internalTypes.keySet()) {
                var type = internalTypes.get(fastenUri);
                var moduleMetadata = new JSONObject();
                moduleMetadata.put("superInterfaces",
                        ExtendedRevisionCallGraph.Type.toListOfString(type.getSuperInterfaces()));
                moduleMetadata.put("superClasses",
                        ExtendedRevisionCallGraph.Type.toListOfString(type.getSuperClasses()));
                long moduleId = metadataDao.insertModule(packageVersionId, fastenUri.toString(),
                        null, moduleMetadata);
                var fileName = type.getSourceFileName();
                var fileId = metadataDao.insertFile(packageVersionId, fileName, null, null, null);
                metadataDao.insertModuleContent(moduleId, fileId);
                for (var methodEntry : type.getMethods().entrySet()) {
                    var localId = (long) methodEntry.getKey();
                    var uri = methodEntry.getValue().toString();
                    var callableMetadata = new JSONObject(methodEntry.getValue().getMetadata());
                    callables.add(new CallablesRecord(localId, moduleId, uri, true,
                            null, JSONB.valueOf(callableMetadata.toString())));
                }
            }
            final var numInternal = callables.size();
            var externalTypes = cha.get(ExtendedRevisionCallGraph.Scope.externalTypes);
            for (var fastenUri : externalTypes.keySet()) {
                var type = externalTypes.get(fastenUri);
                var moduleMetadata = new JSONObject();
                moduleMetadata.put("superInterfaces",
                        ExtendedRevisionCallGraph.Type.toListOfString(type.getSuperInterfaces()));
                moduleMetadata.put("superClasses",
                        ExtendedRevisionCallGraph.Type.toListOfString(type.getSuperClasses()));
                // TODO: Add 'access' and 'final'
                long moduleId = metadataDao.insertModule(packageVersionId, fastenUri.toString(),
                        null, moduleMetadata);
                var fileName = type.getSourceFileName();
                var fileId = metadataDao.insertFile(packageVersionId, fileName, null, null, null);
                metadataDao.insertModuleContent(moduleId, fileId);
                for (var methodEntry : type.getMethods().entrySet()) {
                    var localId = (long) methodEntry.getKey();
                    var uri = methodEntry.getValue().toString();
                    var callableMetadata = new JSONObject(methodEntry.getValue().getMetadata());
                    callables.add(new CallablesRecord(localId, -1L, uri, false,
                            null, JSONB.valueOf(callableMetadata.toString())));
                }
            }
            final int batchSize = 4096;
            var callablesIds = new ArrayList<Long>(callables.size());
            final var callablesIterator = callables.iterator();
            while (callablesIterator.hasNext()) {
                var callablesBatch = new ArrayList<CallablesRecord>(batchSize);
                while (callablesIterator.hasNext() && callablesBatch.size() < batchSize) {
                    callablesBatch.add(callablesIterator.next());
                }
                var ids = metadataDao.batchInsertCallables(callablesBatch);
                callablesIds.addAll(ids);
            }
            var lidToGidMap = new HashMap<Long, Long>();
            for (int i = 0; i < callables.size(); i++) {
                lidToGidMap.put(callables.get(i).getId(), callablesIds.get(i));
            }
            final var graph = callGraph.getGraph();
            final var numEdges = graph.getInternalCalls().size() + graph.getExternalCalls().size();
            var graphCalls = graph.getInternalCalls();
            graphCalls.putAll(graph.getExternalCalls());
            var edges = new ArrayList<EdgesRecord>(numEdges);
            for (var edgeEntry : graphCalls.entrySet()) {
                var localSource = (long) edgeEntry.getKey().get(0);
                var localTarget = (long) edgeEntry.getKey().get(1);
                var globalSource = lidToGidMap.get(localSource);
                var globalTarget = lidToGidMap.get(localTarget);
                var pc = Integer.parseInt(
                        edgeEntry.getValue().keySet().iterator().next().toString());
                var metadata = new JSONObject(edgeEntry.getValue().get(pc));
                metadata.put("pc", pc);
                edges.add(new EdgesRecord(globalSource, globalTarget,
                        JSONB.valueOf(metadata.toString())));
            }
            final var edgesIterator = edges.iterator();
            while (edgesIterator.hasNext()) {
                var edgesBatch = new ArrayList<EdgesRecord>(batchSize);
                while (edgesIterator.hasNext() && edgesBatch.size() < batchSize) {
                    edgesBatch.add(edgesIterator.next());
                }
                metadataDao.batchInsertEdges(edgesBatch);
            }
            this.gidGraph = new GidGraph(packageVersionId, callGraph.product, callGraph.version,
                    callablesIds, numInternal, edges);
            return packageVersionId;
        }

        private Timestamp getProperTimestamp(long timestamp) {
            if (timestamp == -1) {
                return null;
            } else {
                if (timestamp / (1000L * 60 * 60 * 24 * 365) < 1L) {
                    return new Timestamp(timestamp * 1000);
                } else {
                    return new Timestamp(timestamp);
                }
            }
        }

        @Override
        public String name() {
            return "Metadata plugin";
        }

        @Override
        public String description() {
            return "Metadata plugin. "
                    + "Consumes ExtendedRevisionCallgraph-formatted JSON objects from Kafka topic"
                    + " and populates metadata database with consumed data"
                    + " and writes graph of GIDs of callgraph to another Kafka topic.";
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
        }

        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

        @Override
        public Throwable getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }
    }
}
