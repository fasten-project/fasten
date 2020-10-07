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

import eu.fasten.core.data.CScope;
import eu.fasten.core.data.CNode;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.ExtendedRevisionCCallGraph;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.Graph;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.graphdb.GidGraph;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.metadatadb.codegen.enums.ReceiverType;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.BatchUpdateException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MetadataDatabasePlugin extends Plugin {

    public MetadataDatabasePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static abstract class MetadataDBExtension implements KafkaPlugin, DBConnector {

        protected String consumerTopic = "fasten.OPAL.out";
        protected DSLContext dslContext;
        protected boolean processedRecord = false;
        protected Throwable pluginError = null;
        protected final Logger logger = LoggerFactory.getLogger(MetadataDBExtension.class.getName());
        protected boolean restartTransaction = false;
        protected GidGraph gidGraph = null;
        protected String outputPath;

        @Override
        public void setDBConnection(DSLContext dslContext) {
            this.dslContext = dslContext;
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
            final ExtendedRevisionCallGraph callgraph;
            if (!path.isEmpty()) {
                // Parse ERCG from file
                try {
                    JSONTokener tokener = new JSONTokener(new FileReader(path));
                    consumedJson = new JSONObject(tokener);
                } catch (JSONException | IOException e) {
                    logger.error("Error parsing JSON callgraph from path for '"
                            + Paths.get(path).getFileName() + "'", e);
                    processedRecord = false;
                    setPluginError(e);
                    return;
                }
            }
            try {
                if (!consumedJson.has("forge"))
                    throw new JSONException("forge");
                final String forge = consumedJson.get("forge").toString();
                callgraph =  getExtendedRevisionCallGraph(forge, consumedJson);
            } catch (JSONException e) {
                logger.error("Error parsing JSON callgraph for '"
                        + Paths.get(path).getFileName() + "'", e);
                processedRecord = false;
                setPluginError(e);
                return;
            }

            var revision = setOutputPath(callgraph);

            int transactionRestartCount = 0;
            do {
                setPluginError(null);
                try {
                    var metadataDao = new MetadataDao(dslContext);
                    dslContext.transaction(transaction -> {
                        // Start transaction
                        metadataDao.setContext(DSL.using(transaction));
                        long id;
                        try {
                            id = saveToDatabase(callgraph, metadataDao);
                        } catch (RuntimeException e) {
                            processedRecord = false;
                            logger.error("Error saving to the database: '" + revision + "'", e);
                            setPluginError(e);
                            if (e instanceof DataAccessException) {
                                // Database connection error
                                if (e.getCause() instanceof BatchUpdateException) {
                                    var exception = ((BatchUpdateException) e.getCause())
                                            .getNextException();
                                    setPluginError(exception);
                                }
                                logger.info("Restarting transaction for '" + revision + "'");
                                // It could be a deadlock, so restart transaction
                                restartTransaction = true;
                            } else {
                                restartTransaction = false;
                            }
                            throw e;
                        }
                        if (getPluginError() == null) {
                            processedRecord = true;
                            restartTransaction = false;
                            logger.info("Saved the '" + revision + "' callgraph metadata "
                                    + "to the database with package version ID = " + id);
                        }
                    });
                } catch (Exception expected) {
                }
                transactionRestartCount++;
            } while (restartTransaction && !processedRecord
                    && transactionRestartCount < Constants.transactionRestartLimit);
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

        // FIXME Add comments, decouple logic, make it language agnostic
        protected String setOutputPath(ExtendedRevisionCallGraph callgraph) {
            var product = callgraph.product;
            var version = callgraph.version;
            var forge = callgraph.forge;
            String revision;
            if (forge.equals("mvn")) {
                revision = product + Constants.mvnCoordinateSeparator + version;
                final String groupId = callgraph.product.split(Constants.mvnCoordinateSeparator)[0];
                final String artifactId = callgraph.product.split(Constants.mvnCoordinateSeparator)[1];

                product = artifactId + "_" + groupId + "_" + version;
                var firstLetter = artifactId.substring(0, 1);
                outputPath = File.separator + forge + File.separator
                        + firstLetter + File.separator
                        + artifactId + File.separator + product + ".json";
            } else {
                var firstLetter = product.substring(0, 1);
                outputPath = File.separator + forge + File.separator
                        + firstLetter + File.separator
                        + product + ".json";
                revision = product + "_" + version;
            }
            return revision;
        }

        /**
         * Factory method for ExtendedRevisionCallGraph
         */
        public ExtendedRevisionCallGraph getExtendedRevisionCallGraph(String forge, JSONObject json) {
            if (forge.equals("mvn")) {
                return new ExtendedRevisionJavaCallGraph(json);
            } else if (forge.equals("debian")) {
                return new ExtendedRevisionCCallGraph(json);
            }

            return null;
        }

        /**
         * Saves a callgraph of new format to the database to appropriate tables.
         *
         * @param callGraph   Call graph to save to the database.
         * @param metadataDao Data Access Object to insert records in the database
         * @return Package ID saved in the database
         */
        protected long saveToDatabase(ExtendedRevisionCallGraph callGraph, MetadataDao metadataDao) {
            // Insert package record
            final long packageId = metadataDao.insertPackage(callGraph.product, callGraph.forge);

            // Insert package version record
            final long packageVersionId = metadataDao.insertPackageVersion(packageId,
                    callGraph.getCgGenerator(), callGraph.version, null,
                    getProperTimestamp(callGraph.timestamp), new JSONObject());

            var callables = instertDataExtractCallables(callGraph, metadataDao, packageVersionId);
            final var numInternal = callables.size();

            var callablesIds = new LongArrayList(callables.size());
            // Save all callables in the database
            callablesIds.addAll(metadataDao.insertCallablesSeparately(callables, numInternal));

            // Build a map from callable Local ID to Global ID
            var lidToGidMap = new Long2LongOpenHashMap();
            for (int i = 0; i < callables.size(); i++) {
                lidToGidMap.put(callables.get(i).getId().longValue(), callablesIds.getLong(i));
            }

            // Insert all the edges
            var edges = insertEdges(callGraph.getGraph(), lidToGidMap, metadataDao);

            // Remove duplicate nodes
            var internalIds = new LongArrayList(numInternal);
            var externalIds = new LongArrayList(callablesIds.size() - numInternal);
            for (int i = 0; i < numInternal; i++) {
                internalIds.add(callablesIds.getLong(i));
            }
            for (int i = numInternal; i < callablesIds.size(); i++) {
                externalIds.add(callablesIds.getLong(i));
            }
            var internalNodesSet = new LongLinkedOpenHashSet(internalIds);
            var externalNodesSet = new LongLinkedOpenHashSet(externalIds);
            numInternal = internalNodesSet.size();
            callablesIds = new LongArrayList(internalNodesSet.size() + externalNodesSet.size());
            callablesIds.addAll(internalNodesSet);
            callablesIds.addAll(externalNodesSet);

            // Create a GID Graph for production
            this.gidGraph = new GidGraph(packageVersionId, callGraph.product, callGraph.version,
                    callablesIds, numInternal, edges);
            return packageVersionId;
        }

        public abstract ArrayList<CallablesRecord> instertDataExtractCallables(ExtendedRevisionCallGraph callgraph, MetadataDao metadataDao, long packageVersionId);

        protected abstract List<EdgesRecord> insertEdges(Graph graph,
                                 Long2LongOpenHashMap lidToGidMap, MetadataDao metadataDao);

        protected Timestamp getProperTimestamp(long timestamp) {
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
            return "0.1.2";
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

    @Extension
    public static class MetadataDBJavaExtension extends MetadataDBExtension {

        public ArrayList<CallablesRecord> instertDataExtractCallables(ExtendedRevisionCallGraph callgraph, MetadataDao metadataDao, long packageVersionId) {
            ExtendedRevisionJavaCallGraph javaCallGraph = (ExtendedRevisionJavaCallGraph) callgraph;
            var callables = new ArrayList<CallablesRecord>();
            var cha = javaCallGraph.getClassHierarchy();
            var internalTypes = cha.get(JavaScope.internalTypes);
            // Insert all modules, files, module contents and extract callables from internal types
            for (var fastenUri : internalTypes.keySet()) {
                var type = internalTypes.get(fastenUri);
                var moduleId = insertModule(type, fastenUri, packageVersionId, metadataDao);
                var fileId = metadataDao.insertFile(packageVersionId, type.getSourceFileName());
                metadataDao.insertModuleContent(moduleId, fileId);
                callables.addAll(extractCallablesFromType(type, moduleId, true));
            }

            var externalTypes = cha.get(JavaScope.externalTypes);
            // Extract all external callables
            for (var fastenUri : externalTypes.keySet()) {
                var type = externalTypes.get(fastenUri);
                callables.addAll(extractCallablesFromType(type, -1L, false));
            }
            return callables;
        }

        protected long insertModule(JavaType type, FastenURI fastenUri,
                                  long packageVersionId, MetadataDao metadataDao) {
            // Collect metadata of the module
            var moduleMetadata = new JSONObject();
            moduleMetadata.put("superInterfaces",
                    JavaType.toListOfString(type.getSuperInterfaces()));
            moduleMetadata.put("superClasses",
                    JavaType.toListOfString(type.getSuperClasses()));
            moduleMetadata.put("access", type.getAccess());
            moduleMetadata.put("final", type.isFinal());

            // Put everything in the database
            return metadataDao.insertModule(packageVersionId, fastenUri.toString(),
                    null, moduleMetadata);
        }

        private List<CallablesRecord> extractCallablesFromType(JavaType type,
                                                               long moduleId, boolean isInternal) {
            // Extracts a list of all callable records and their metadata from the type
            var callables = new ArrayList<CallablesRecord>(type.getMethods().size());

            for (var methodEntry : type.getMethods().entrySet()) {
                // Get Local ID
                var localId = (long) methodEntry.getKey();

                // Get FASTEN URI
                var uri = methodEntry.getValue().getUri().toString();

                // Collect metadata
                var callableMetadata = new JSONObject(methodEntry.getValue().getMetadata());
                Integer firstLine = null;
                if (callableMetadata.has("first")
                        && !(callableMetadata.get("first") instanceof String)) {
                    firstLine = callableMetadata.getInt("first");
                    callableMetadata.remove("first");
                }
                Integer lastLine = null;
                if (callableMetadata.has("last")
                        && !(callableMetadata.get("last") instanceof String)) {
                    lastLine = callableMetadata.getInt("last");
                    callableMetadata.remove("last");
                }

                // Add a record to the list
                callables.add(new CallablesRecord(localId, moduleId, uri, isInternal, null,
                        firstLine, lastLine, JSONB.valueOf(callableMetadata.toString())));
            }
            return callables;
        }

        protected List<EdgesRecord> insertEdges(Graph graph,
                                 Long2LongOpenHashMap lidToGidMap, MetadataDao metadataDao) {
            final var numEdges = graph.getInternalCalls().size() + graph.getExternalCalls().size();

            // Map of all edges (internal and external)
            var graphCalls = graph.getInternalCalls();
            graphCalls.putAll(graph.getExternalCalls());

            var edges = new ArrayList<EdgesRecord>(numEdges);
            for (var edgeEntry : graphCalls.entrySet()) {

                // Get Global ID of the source callable
                var source = lidToGidMap.get((long) edgeEntry.getKey().get(0));
                // Get Global ID of the target callable
                var target = lidToGidMap.get((long) edgeEntry.getKey().get(1));

                // Create receivers
                var receivers = new ReceiverRecord[edgeEntry.getValue().size()];
                var counter = 0;
                for (var obj : edgeEntry.getValue().keySet()) {
                    var pc = obj.toString();
                    // Get edge metadata
                    var metadataMap = (Map<String, Object>) edgeEntry.getValue()
                            .get(Integer.parseInt(pc));
                    var callMetadata = new JSONObject();
                    for (var key : metadataMap.keySet()) {
                        callMetadata.put(key, metadataMap.get(key));
                    }

                    // Extract receiver information from the metadata
                    int line = callMetadata.optInt("line", -1);
                    var type = this.getReceiverType(callMetadata.optString("type"));
                    String receiverUri = callMetadata.optString("receiver");
                    receivers[counter++] = new ReceiverRecord(line, type, receiverUri);
                }

                // Add edge record to the list of records
                edges.add(new EdgesRecord(source, target, receivers, JSONB.valueOf("{}")));
            }

            // Batch insert all edges
            final var edgesIterator = edges.iterator();
            while (edgesIterator.hasNext()) {
                var edgesBatch = new ArrayList<EdgesRecord>(Constants.insertionBatchSize);
                while (edgesIterator.hasNext()
                        && edgesBatch.size() < Constants.insertionBatchSize) {
                    edgesBatch.add(edgesIterator.next());
                }
                metadataDao.batchInsertEdges(edgesBatch);
            }
            return edges;
        }

        private ReceiverType getReceiverType(String type) {
            switch (type) {
                case "invokestatic":
                    return ReceiverType.static_;
                case "invokespecial":
                    return ReceiverType.special;
                case "invokevirtual":
                    return ReceiverType.virtual;
                case "invokedynamic":
                    return ReceiverType.dynamic;
                case "invokeinterface":
                    return ReceiverType.interface_;
                default:
                    return null;
            }
        }
    }

    @Extension
    public static class MetadataDBCExtension extends MetadataDBExtension {

        /**
         * Saves a callgraph of new format to the database to appropriate tables.
         * We override this method because we want to save the architecture
         * of the package.
         *
         * @param callGraph   Call graph to save to the database.
         * @param metadataDao Data Access Object to insert records in the database
         * @return Package ID saved in the database
         */
        protected long saveToDatabase(ExtendedRevisionCallGraph callGraph, MetadataDao metadataDao) {
            ExtendedRevisionCCallGraph CCallGraph = (ExtendedRevisionCCallGraph) callGraph;
            // Insert package record
            final long packageId = metadataDao.insertPackage(CCallGraph.product, CCallGraph.forge);

            // Insert package version record
            // TODO Add architecture
            final long packageVersionId = metadataDao.insertPackageVersion(packageId,
                    CCallGraph.getCgGenerator(), CCallGraph.version,
                    getProperTimestamp(CCallGraph.timestamp), new JSONObject());

            var callables = instertDataExtractCallables(callGraph, metadataDao, packageVersionId);
            final var numInternal = callables.size();

            var callablesIds = new LongArrayList(callables.size());
            // Save all callables in the database
            callablesIds.addAll(metadataDao.insertCallablesSeparately(callables, numInternal));

            // Build a map from callable Local ID to Global ID
            var lidToGidMap = new Long2LongOpenHashMap();
            for (int i = 0; i < callables.size(); i++) {
                lidToGidMap.put(callables.get(i).getId().longValue(), callablesIds.getLong(i));
            }

            // Insert all the edges
            var edges = insertEdges(callGraph.getGraph(), lidToGidMap, metadataDao);

            // Create a GID Graph for production
            this.gidGraph = new GidGraph(packageVersionId, callGraph.product, callGraph.version,
                    callablesIds, numInternal, edges);
            return packageVersionId;
        }

        /**
         * Get callables from CScope, and insert files and modules for internal
         * scopes and external static functions.
         */
        public ArrayList<CallablesRecord> getCallables(final Map<CScope, Map<String, Map<Integer, CNode>>> cha, CScope scope,
                boolean isInternal, boolean saveFiles, long packageVersionId, MetadataDao metadataDao) {
            var callables = new ArrayList<CallablesRecord>();
            for (final var name : cha.get(scope).entrySet()) {
                for (final var method : name.getValue().entrySet()) {
                    // FIXME Create correctly dummy modules
                    // We use "" for dummy modules
                    String namespace = isInternal ? "" : "C";
                    var moduleId = -1L;
                    // Save module
                    if (saveFiles) {
                        // FIXME Create correctly dummy modules
                        moduleId = metadataDao.insertModule(packageVersionId, namespace, null, null);
                        // We save only the first file of a CNode
                        var fileId = metadataDao.insertFile(packageVersionId, method.getValue().getFile());
                        metadataDao.insertModuleContent(moduleId, fileId);
                        // Save binary Module
                        if (scope.equals(CScope.internalBinaries)) {
                            var binModuleId = metadataDao.insertBinaryModule(packageVersionId, name.getKey(), null, null);
                            metadataDao.insertBinaryModuleContent(binModuleId, fileId);
                        }
                        // FIXME we should change the check_module_id constraint in Postgres
                        // to handle static functions.
                        moduleId = isInternal ? moduleId : -1L;
                    }
                    // Save Callable
                    var localId = (long) method.getKey();
                    String uri = method.getValue().getUri().toString();
                    var callableMetadata = new JSONObject(method.getValue().getMetadata());
                    Integer firstLine = null;
                    if (callableMetadata.has("first")) {
                        if (!(callableMetadata.get("first") instanceof String))
                            firstLine = callableMetadata.getInt("first");
                        else
                            firstLine = Integer.parseInt(callableMetadata.getString("first"));
                        callableMetadata.remove("first");
                    }
                    Integer lastLine = null;
                    if (callableMetadata.has("last")) {
                        if (!(callableMetadata.get("last") instanceof String))
                            lastLine = callableMetadata.getInt("last");
                        else
                            lastLine = Integer.parseInt(callableMetadata.getString("last"));
                        callableMetadata.remove("last");
                    }
                    callableMetadata.put("type", CScope.internalBinaries);
                    callables.add(new CallablesRecord(localId, moduleId, uri, isInternal, null,
                        firstLine, lastLine, JSONB.valueOf(callableMetadata.toString())));
                }
            }
            return callables;
        }

        public ArrayList<CallablesRecord> instertDataExtractCallables(ExtendedRevisionCallGraph callgraph, MetadataDao metadataDao, long packageVersionId) {
            ExtendedRevisionCCallGraph CCallGraph = (ExtendedRevisionCCallGraph) callgraph;
            var callables = new ArrayList<CallablesRecord>();
            var cha = CCallGraph.getClassHierarchy();

            callables.addAll(getCallables(cha, CScope.internalBinaries, true, true, packageVersionId, metadataDao));
            callables.addAll(getCallables(cha, CScope.internalStaticFunctions, true, true, packageVersionId, metadataDao));
            callables.addAll(getCallables(cha, CScope.externalProducts, false, false, packageVersionId, metadataDao));
            callables.addAll(getCallables(cha, CScope.externalUndefined, false, false, packageVersionId, metadataDao));
            callables.addAll(getCallables(cha, CScope.externalStraticFunctions, false, true, packageVersionId, metadataDao));

            return callables;
        }

        protected List<EdgesRecord> insertEdges(Graph graph,
                                 Long2LongOpenHashMap lidToGidMap, MetadataDao metadataDao) {
            final var numEdges = graph.getInternalCalls().size() + graph.getExternalCalls().size();

            // Map of all edges (internal and external)
            var graphCalls = graph.getInternalCalls();
            graphCalls.putAll(graph.getExternalCalls());

            var edges = new ArrayList<EdgesRecord>(numEdges);
            for (var edgeEntry : graphCalls.entrySet()) {

                // Get Global ID of the source callable
                var source = lidToGidMap.get((long) edgeEntry.getKey().get(0));
                // Get Global ID of the target callable
                var target = lidToGidMap.get((long) edgeEntry.getKey().get(1));

                // Create receivers
                var receivers = new ReceiverRecord[0];

                // Add edge record to the list of records
                edges.add(new EdgesRecord(source, target, receivers, JSONB.valueOf("{}")));
            }

            // Batch insert all edges
            final var edgesIterator = edges.iterator();
            while (edgesIterator.hasNext()) {
                var edgesBatch = new ArrayList<EdgesRecord>(Constants.insertionBatchSize);
                while (edgesIterator.hasNext()
                        && edgesBatch.size() < Constants.insertionBatchSize) {
                    edgesBatch.add(edgesIterator.next());
                }
                metadataDao.batchInsertEdges(edgesBatch);
            }
            return edges;
        }
    }
}
