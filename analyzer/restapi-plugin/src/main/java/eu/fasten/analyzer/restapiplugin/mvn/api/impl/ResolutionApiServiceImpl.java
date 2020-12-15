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

package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.api.ResolutionApiService;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.EnrichedGraph;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.DatabaseMerger;
import org.apache.commons.math3.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ResolutionApiServiceImpl implements ResolutionApiService {

    @Override
    public ResponseEntity<String> resolveDependencies(String package_name, String version, boolean transitive, long timestamp) {
        var groupId = package_name.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = package_name.split(Constants.mvnCoordinateSeparator)[1];
        var depSet = KnowledgeBaseConnector.graphResolver.resolveDependencies(groupId,
                artifactId, version, timestamp, KnowledgeBaseConnector.dbContext, transitive);
        var jsonArray = new JSONArray();
        depSet.stream().map(Revision::toJSON).peek(json -> {
            var group = json.getString("groupId");
            var artifact = json.getString("artifactId");
            var ver = json.getString("version");
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.limaUrl,
                    artifact.charAt(0), artifact, artifact, group, ver);
            json.put("url", url);
        }).forEach(jsonArray::put);
        var result = jsonArray.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> resolveDependents(String package_name, String version, boolean transitive, long timestamp) {
        var groupId = package_name.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = package_name.split(Constants.mvnCoordinateSeparator)[1];
        var depSet = KnowledgeBaseConnector.graphResolver.resolveDependents(groupId,
                artifactId, version, timestamp, transitive);
        var jsonArray = new JSONArray();
        depSet.stream().map(Revision::toJSON).peek(json -> {
            var group = json.getString("groupId");
            var artifact = json.getString("artifactId");
            var ver = json.getString("version");
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.limaUrl,
                    artifact.charAt(0), artifact, artifact, group, ver);
            json.put("url", url);
        }).forEach(jsonArray::put);
        var result = jsonArray.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> enrichArtifacts(List<String> mavenCoordinates, boolean enrichEdges, boolean stitch) {
        var json = new JSONObject(mavenCoordinates.size());
        if (stitch) {
            var coordinateIDsMap = KnowledgeBaseConnector.kbDao.getPackageVersionIDs(mavenCoordinates);
            var depIds = mavenCoordinates.stream().map(coordinateIDsMap::get).collect(Collectors.toSet());
            var databaseMerger = new DatabaseMerger(depIds, KnowledgeBaseConnector.dbContext, KnowledgeBaseConnector.graphDao);
            var graph = databaseMerger.mergeAllDeps();
            if (graph == null) {
                return new ResponseEntity<>("Could not stitch provided artifacts", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            json = directedGraphToEnrichedJSON(graph, enrichEdges);
        } else {
            for (var coordinate : mavenCoordinates) {
                DirectedGraph graph;
                var package_name = coordinate.split(Constants.mvnCoordinateSeparator)[0]
                        + Constants.mvnCoordinateSeparator
                        + coordinate.split(Constants.mvnCoordinateSeparator)[1];
                var version = coordinate.split(Constants.mvnCoordinateSeparator)[2];
                var packageVersionId = KnowledgeBaseConnector.kbDao.getPackageVersionID(package_name, version);
                if (packageVersionId == null) {
                    return new ResponseEntity<>("Package version not found", HttpStatus.NOT_FOUND);
                }
                try {
                    graph = KnowledgeBaseConnector.graphDao.getGraphData(packageVersionId);
                } catch (RocksDBException e) {
                    return new ResponseEntity<>("Could not retrieve callgraph from the graph database",
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
                if (graph == null) {
                    return new ResponseEntity<>("Callgraph not found in the graph database", HttpStatus.NOT_FOUND);
                }
                json.put(coordinate, directedGraphToEnrichedJSON(graph, enrichEdges));
            }
        }
        var result = json.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    protected static JSONObject directedGraphToEnrichedJSON(DirectedGraph graph, boolean enrichEdges) {
        Map<Long, JSONObject> nodesMetadata = KnowledgeBaseConnector.kbDao.getCallablesMetadata(graph.nodes());
        Map<Pair<Long, Long>, JSONObject> edgesMetadata = new HashMap<>();
        var edges = graph.edgeSet().stream().map(e -> new Pair<>(e.firstLong(), e.secondLong())).collect(Collectors.toSet());
        if (enrichEdges) {
            edgesMetadata = KnowledgeBaseConnector.kbDao.getEdgesMetadata(new ArrayList<>(edges));
        }
        var enrichedGraph = new EnrichedGraph(graph.nodes(), edges, nodesMetadata, edgesMetadata);
        return enrichedGraph.toJSON();
    }
}
