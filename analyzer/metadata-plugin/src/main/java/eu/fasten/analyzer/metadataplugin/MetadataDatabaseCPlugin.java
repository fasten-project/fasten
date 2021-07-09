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

import eu.fasten.core.data.CNode;
import eu.fasten.core.data.CScope;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.ExtendedRevisionCCallGraph;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.Graph;
import eu.fasten.core.data.graphdb.GidGraph;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.metadatadb.codegen.enums.Access;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallSitesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class MetadataDatabaseCPlugin extends Plugin {
    public MetadataDatabaseCPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MetadataDBCExtension extends MetadataDBExtension {
        private static DSLContext dslContext;

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            MetadataDBCExtension.dslContext = dslContexts.get(Constants.debianForge);
        }

        @Override
        public DSLContext getDBConnection() {
            return MetadataDBCExtension.dslContext;
        }

        private static final String globalNamespace = "C";
        private static final String moduleNamespaceAddition = "_module";

        protected Map<String, Long> getNamespaceMap(ExtendedRevisionCallGraph graph, MetadataDao metadataDao) {
            ExtendedRevisionCCallGraph cGraph = (ExtendedRevisionCCallGraph) graph;
            var namespaces = new HashSet<String>();
            namespaces.add(globalNamespace);
            cGraph.getClassHierarchy().get(CScope.externalProduct).values().forEach(v -> v.values().forEach(m -> namespaces.add(m.getFile() + moduleNamespaceAddition)));
            cGraph.getClassHierarchy().get(CScope.internalBinary).values().forEach(v -> v.values().forEach(m -> namespaces.add(m.getFile() + moduleNamespaceAddition)));
            cGraph.getClassHierarchy().get(CScope.externalStaticFunction).values().forEach(v -> v.values().forEach(m -> namespaces.add(m.getFile() + moduleNamespaceAddition)));
            cGraph.getClassHierarchy().get(CScope.externalUndefined).values().forEach(v -> v.values().forEach(m -> namespaces.add(m.getFile() + moduleNamespaceAddition)));
            cGraph.getClassHierarchy().get(CScope.internalStaticFunction).values().forEach(v -> v.values().forEach(m -> namespaces.add(m.getFile() + moduleNamespaceAddition)));
            return metadataDao.insertNamespaces(namespaces);
        }

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
            final long packageVersionId = metadataDao.insertPackageVersion(packageId,
                    CCallGraph.getCgGenerator(), CCallGraph.version, null, CCallGraph.architecture,
                    getProperTimestamp(CCallGraph.timestamp), new JSONObject());

            var namespaceMap = getNamespaceMap(callGraph, metadataDao);
            var allCallables = insertDataExtractCallables(callGraph, metadataDao, packageVersionId, namespaceMap);
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
            this.gidGraph = new GidGraph(packageVersionId, callGraph.product, callGraph.version,
                    callablesIds, numInternal, edges);
            return packageVersionId;
        }

        /**
         * Get callables from CScope, and insert files and modules for internal
         * scopes and external static functions.
         */
        public ArrayList<CallablesRecord> getCallables(final Map<CScope, Map<String, Map<Integer, CNode>>> cha, CScope scope,
                boolean isInternal, boolean saveFiles, long packageVersionId, MetadataDao metadataDao, Map<String, Long> namespaceMap) {
            var callables = new ArrayList<CallablesRecord>();
            for (final var name : cha.get(scope).entrySet()) {
                for (final var method : name.getValue().entrySet()) {
                    // We use dummy modules to connect files to callables
                    // otherwise we set the global "C" as the namespace.
                    String namespace = saveFiles ? method.getValue().getFile() + moduleNamespaceAddition : globalNamespace;
                    var moduleId = -1L;
                    // Save module
                    if (saveFiles) {
                        // We save only the first file of a CNode
                        var fileId = metadataDao.insertFile(packageVersionId, method.getValue().getFile());
                        // Check if dummy module already exist for this file.
                        moduleId = metadataDao.getModuleContent(fileId);
                        if (moduleId == -1L) {
                            moduleId = metadataDao.insertModule(packageVersionId, namespaceMap.get(namespace), null, true);
                        }
                        metadataDao.insertModuleContent(moduleId, fileId);
                        // Save binary Module
                        if (scope.equals(CScope.internalBinary)) {
                            var binModuleId = metadataDao.insertBinaryModule(packageVersionId, name.getKey(), null, null);
                            metadataDao.insertBinaryModuleContent(binModuleId, fileId);
                        }
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
                    }
                    callableMetadata.remove("first");
                    Integer lastLine = null;
                    if (callableMetadata.has("last")) {
                        if (!(callableMetadata.get("last") instanceof String))
                            lastLine = callableMetadata.getInt("last");
                        else
                            lastLine = Integer.parseInt(callableMetadata.getString("last"));
                    }
                    callableMetadata.remove("last");
                    var callableType = getCallableType(String.valueOf(scope));
                    Boolean callableDefined = null;
                    if (callableMetadata.has("defined") && (callableMetadata.get("defined") instanceof Boolean)) {
                        callableDefined = callableMetadata.getBoolean("defined");
                    }
                    callableMetadata.remove("defined");
                    Access callableAccess = null;
                    if (callableMetadata.has("access") && (callableMetadata.get("access") instanceof String)) {
                        callableAccess = getAccess(callableMetadata.getString("access"));
                    }
                    callableMetadata.remove("access");
                    callables.add(new CallablesRecord(localId, moduleId, uri, isInternal,
                        firstLine, lastLine, callableType, callableDefined, callableAccess,
                            JSONB.valueOf(callableMetadata.toString())));
                }
            }
            return callables;
        }

        public Pair<ArrayList<CallablesRecord>, Integer> insertDataExtractCallables(ExtendedRevisionCallGraph callgraph, MetadataDao metadataDao,
                                                                                    long packageVersionId, Map<String, Long> namespaceMap) {
            ExtendedRevisionCCallGraph CCallGraph = (ExtendedRevisionCCallGraph) callgraph;
            var callables = new ArrayList<CallablesRecord>();
            var cha = CCallGraph.getClassHierarchy();

            callables.addAll(getCallables(cha, CScope.internalBinary, true, true, packageVersionId, metadataDao, namespaceMap));
            callables.addAll(getCallables(cha, CScope.internalStaticFunction, true, true, packageVersionId, metadataDao, namespaceMap));
            var numInternal = callables.size();

            callables.addAll(getCallables(cha, CScope.externalProduct, false, false, packageVersionId, metadataDao, namespaceMap));
            callables.addAll(getCallables(cha, CScope.externalUndefined, false, false, packageVersionId, metadataDao, namespaceMap));
            callables.addAll(getCallables(cha, CScope.externalStaticFunction, false, true, packageVersionId, metadataDao, namespaceMap));

            return new ImmutablePair<>(callables, numInternal);
        }

        protected List<CallSitesRecord> insertEdges(Graph graph,
                                                    Long2LongOpenHashMap lidToGidMap, MetadataDao metadataDao) {
            final var numEdges = graph.getInternalCalls().size() + graph.getExternalCalls().size();

            // Map of all edges (internal and external)
            var graphCalls = graph.getInternalCalls();
            graphCalls.putAll(graph.getExternalCalls());

            var edges = new ArrayList<CallSitesRecord>(numEdges);
            for (var edgeEntry : graphCalls.entrySet()) {

                // Get Global ID of the source callable
                var source = lidToGidMap.get((long) edgeEntry.getKey().firstInt());
                // Get Global ID of the target callable
                var target = lidToGidMap.get((long) edgeEntry.getKey().secondInt());

                // Add edge record to the list of records
                edges.add(new CallSitesRecord(source, target, null, null, null, null));
            }

            // Batch insert all edges
            final var edgesIterator = edges.iterator();
            while (edgesIterator.hasNext()) {
                var edgesBatch = new ArrayList<CallSitesRecord>(Constants.insertionBatchSize);
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

