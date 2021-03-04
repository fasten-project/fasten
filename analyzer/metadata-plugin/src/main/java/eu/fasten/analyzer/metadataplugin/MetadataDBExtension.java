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

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.ExtendedRevisionCCallGraph;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.ExtendedRevisionPythonCallGraph;
import eu.fasten.core.data.Graph;
import eu.fasten.core.data.graphdb.ExtendedGidGraph;
import eu.fasten.core.data.graphdb.GidGraph;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataDBExtension implements KafkaPlugin, DBConnector {

    protected String consumerTopic = "fasten.OPAL.out";
    private static DSLContext dslContext;
    protected boolean processedRecord = false;
    protected Exception pluginError = null;
    protected final Logger logger = LoggerFactory.getLogger(MetadataDBExtension.class.getName());
    protected boolean restartTransaction = false;
    protected GidGraph gidGraph = null;
    protected String outputPath;
    private String artifactRepository = null;

    @Override
    public void setDBConnection(Map<String, DSLContext> dslContexts) {
        MetadataDBExtension.dslContext = dslContexts.get("");
    }

    public DSLContext getDBConnection() {
        return MetadataDBExtension.dslContext;
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
            if (!consumedJson.has("forge")) {
                throw new JSONException("forge");
            }
            final String forge = consumedJson.get("forge").toString();
            callgraph = getExtendedRevisionCallGraph(forge, consumedJson);
        } catch (JSONException e) {
            logger.error("Error parsing JSON callgraph for '"
                    + Paths.get(path).getFileName() + "'", e);
            processedRecord = false;
            setPluginError(e);
            return;
        }

        this.artifactRepository = consumedJson.optString("artifactRepository",
                (callgraph instanceof ExtendedRevisionJavaCallGraph) ? MavenUtilities.getRepos().get(0) : null);
        var revision = callgraph.product + Constants.mvnCoordinateSeparator + callgraph.version;

        int transactionRestartCount = 0;
        do {
            setPluginError(null);
            try {
                var metadataDao = new MetadataDao(getDBConnection());
                getDBConnection().transaction(transaction -> {
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
            return Optional.of(gidGraph.toJSON().toString());
        }
    }

    @Override
    public String getOutputPath() {
        return outputPath;
    }

    /**
     * Sets outputPath to a JSON file where plugin's output can be stored.
     *
     * @param callgraph Callgraph which contains information needed for output path
     */
    protected void setOutputPath(ExtendedRevisionCallGraph callgraph) {
        var forge = callgraph.forge;
        var product = callgraph.getRevisionName();
        var firstLetter = product.substring(0, 1);
        this.outputPath = File.separator + forge + File.separator
                + firstLetter + File.separator + product + ".json";
    }

    /**
     * Factory method for ExtendedRevisionCallGraph
     */
    public ExtendedRevisionCallGraph getExtendedRevisionCallGraph(String forge, JSONObject json) {
        if (forge.equals(Constants.mvnForge)) {
            return new ExtendedRevisionJavaCallGraph(json);
        } else if (forge.equals(Constants.debianForge)) {
            return new ExtendedRevisionCCallGraph(json);
        } else if (forge.equals(Constants.pypiForge)) {
            return new ExtendedRevisionPythonCallGraph(json);
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

        var artifactRepoId = artifactRepository != null ? metadataDao.insertArtifactRepository(artifactRepository) : null;

        // Insert package version record
        final long packageVersionId = metadataDao.insertPackageVersion(packageId,
                callGraph.getCgGenerator(), callGraph.version, artifactRepoId, null,
                getProperTimestamp(callGraph.timestamp), new JSONObject());

        var allCallables = insertDataExtractCallables(callGraph, metadataDao, packageVersionId);
        var callables = allCallables.getLeft();
        var numInternal = allCallables.getRight();

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
        this.gidGraph = new ExtendedGidGraph(packageVersionId, callGraph.product, callGraph.version,
                callablesIds, numInternal, edges);
        return packageVersionId;
    }

    // All classes that implements this class must provide an implementation
    // for this method. We cannot convert this class to an abstract class.
    public Pair<ArrayList<CallablesRecord>, Integer> insertDataExtractCallables(
            ExtendedRevisionCallGraph callgraph, MetadataDao metadataDao, long packageVersionId) {
        return new ImmutablePair<>(new ArrayList<>(), 0);
    }

    protected List<EdgesRecord> insertEdges(Graph graph,
                                            Long2LongOpenHashMap lidToGidMap, MetadataDao metadataDao) {
        return new ArrayList<EdgesRecord>();
    }

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

    public void setPluginError(Exception throwable) {
        this.pluginError = throwable;
    }

    @Override
    public Exception getPluginError() {
        return this.pluginError;
    }

    @Override
    public void freeResource() {

    }

    @Override
    public long getMaxConsumeTimeout() {
        return 900000; //The MetadataDB plugin takes up to 15 minutes to process a record.
    }
}
