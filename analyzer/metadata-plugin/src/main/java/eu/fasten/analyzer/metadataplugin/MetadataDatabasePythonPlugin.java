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
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.ExtendedRevisionPythonCallGraph;
import eu.fasten.core.data.Graph;
import eu.fasten.core.data.PythonScope;
import eu.fasten.core.data.PythonType;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.metadatadb.codegen.enums.CallableAccess;
import eu.fasten.core.data.metadatadb.codegen.enums.CallableType;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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

public class MetadataDatabasePythonPlugin extends Plugin {
    public MetadataDatabasePythonPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MetadataDBPythonExtension extends MetadataDBExtension {
        private static DSLContext dslContext;

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            MetadataDBPythonExtension.dslContext = dslContexts.get(Constants.pypiForge);
        }

        @Override
        public DSLContext getDBConnection() {
            return MetadataDBPythonExtension.dslContext;
        }

        protected Map<String, Long> getNamespaceMap(ExtendedRevisionCallGraph graph, MetadataDao metadataDao) {
            ExtendedRevisionPythonCallGraph pyGraph = (ExtendedRevisionPythonCallGraph) graph;
            var namespaces = new HashSet<String>();
            namespaces.addAll(pyGraph.getClassHierarchy().get(PythonScope.internal).keySet());
            namespaces.addAll(pyGraph.getClassHierarchy().get(PythonScope.external).keySet());
            var namespaceMap = metadataDao.getNamespaceMap(new ArrayList<>(namespaces));
            namespaceMap.keySet().forEach(namespaces::remove);
            namespaceMap.putAll(metadataDao.insertNamespaces(namespaces));
            return namespaceMap;
        }

        public Pair<ArrayList<CallablesRecord>, Integer> insertDataExtractCallables(ExtendedRevisionCallGraph callgraph,
                                                                                    MetadataDao metadataDao, long packageVersionId,
                                                                                    Map<String, Long> namespaceMap) {
            ExtendedRevisionPythonCallGraph pythonCallGraph = (ExtendedRevisionPythonCallGraph) callgraph;

            var callables = new ArrayList<CallablesRecord>();
            var cha = pythonCallGraph.getClassHierarchy();
            var internals = cha.get(PythonScope.internal);
            // Insert all modules, files, module contents and extract callables from internal types
            for (var entry : internals.entrySet()) {
                var type = entry.getValue();
                var moduleName = entry.getKey();
                var moduleId = metadataDao.insertModule(packageVersionId, namespaceMap.get(moduleName),
                                                        null, new JSONObject());
                var fileId = metadataDao.insertFile(packageVersionId, type.getSourceFileName());
                metadataDao.insertModuleContent(moduleId, fileId);
                callables.addAll(extractCallablesFromType(type, moduleId, true));
            }

            var numInternal = callables.size();

            var externals = cha.get(PythonScope.external);
            // Extract all external callables
            for (var entry : externals.entrySet()) {
                var type = entry.getValue();
                callables.addAll(extractCallablesFromType(type, -1L, false));
            }

            return new ImmutablePair<>(callables, numInternal);
        }

        private List<CallablesRecord> extractCallablesFromType(PythonType type,
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
                }
                callableMetadata.remove("first");
                Integer lastLine = null;
                if (callableMetadata.has("last")
                        && !(callableMetadata.get("last") instanceof String)) {
                    lastLine = callableMetadata.getInt("last");
                }
                callableMetadata.remove("last");
                CallableType callableType = null;
                if (callableMetadata.has("type") && (callableMetadata.get("type") instanceof String)) {
                    callableType = getCallableType(callableMetadata.getString("type"));
                }
                callableMetadata.remove("type");
                boolean callableDefined = false;
                if (callableMetadata.has("defined") && (callableMetadata.get("defined") instanceof Boolean)) {
                    callableDefined = callableMetadata.getBoolean("defined");
                }
                callableMetadata.remove("defined");
                CallableAccess callableAccess = null;
                if (callableMetadata.has("access") && (callableMetadata.get("access") instanceof String)) {
                    callableAccess = getCallableAccess(callableMetadata.getString("access"));
                }
                callableMetadata.remove("access");
                // Add a record to the list
                callables.add(new CallablesRecord(localId, moduleId, uri, isInternal, null,
                        firstLine, lastLine, callableType, callableDefined, callableAccess,
                        JSONB.valueOf(callableMetadata.toString())));
            }
            return callables;
        }

        protected List<EdgesRecord> insertEdges(Graph graph, Long2LongOpenHashMap lidToGidMap,
                                                Map<String, Long> namespaceMap, MetadataDao metadataDao) {
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

                // Add edge record to the list of records
                edges.add(new EdgesRecord(source, target, null, null));
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
