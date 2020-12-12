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
import eu.fasten.analyzer.restapiplugin.mvn.api.StitchingApiService;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.DatabaseMerger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StitchingApiServiceImpl implements StitchingApiService {

    @Override
    public ResponseEntity<String> resolveCallablesToUris(List<Long> gidList) {
        var fastenUris = KnowledgeBaseConnector.kbDao.getFullFastenUris(gidList);
        var json = new JSONArray();
        fastenUris.forEach(json::put);
        return new ResponseEntity<>(json.toString(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getCallablesMetadata(List<String> fastenUris) {
        var metadataMap = KnowledgeBaseConnector.kbDao.getCallablesMetadata(fastenUris);
        var json = new JSONObject();
        for (var entry : metadataMap.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return new ResponseEntity<>(json.toString(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> resolveMultipleDependencies(List<String> mavenCoordinates) {
        var depMap = new HashMap<String, Set<Revision>>(mavenCoordinates.size());
        for (var coordinate : mavenCoordinates) {
            var groupId = coordinate.split(Constants.mvnCoordinateSeparator)[0];
            var artifactId = coordinate.split(Constants.mvnCoordinateSeparator)[1];
            var version = coordinate.split(Constants.mvnCoordinateSeparator)[2];
            var depSet = KnowledgeBaseConnector.graphResolver.resolveDependencies(groupId,
                    artifactId, version, -1, KnowledgeBaseConnector.dbContext, true);
            depMap.put(coordinate, depSet);
        }
        var finalSet = new HashSet<Revision>();
        // TODO: Use proper resolution to create a final dependency set
        for (var deps : depMap.values()) {
            finalSet.addAll(deps);
        }
        var jsonArray = new JSONArray();
        finalSet.stream().map(r -> {
            var json = new JSONObject();
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.limaUrl,
                    r.artifactId.charAt(0), r.artifactId, r.artifactId, r.groupId, r.version);
            json.put(String.valueOf(r.id), url);
            return json;
        }).forEach(jsonArray::put);
        return new ResponseEntity<>(jsonArray.toString(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getDirectedGraph(long packageVersionId, boolean needStitching, long timestamp) {
        DirectedGraph graph;
        if (needStitching) {
            var mavenCoordinate = KnowledgeBaseConnector.kbDao.getMavenCoordinate(packageVersionId);
            var groupId = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[0];
            var artifactId = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[1];
            var version = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[2];
            var depSet = KnowledgeBaseConnector.graphResolver.resolveDependencies(groupId,
                    artifactId, version, timestamp, KnowledgeBaseConnector.dbContext, true);
            var depIds = depSet.stream().map(r -> r.id).collect(Collectors.toSet());
            var databaseMerger = new DatabaseMerger(depIds, KnowledgeBaseConnector.dbContext, KnowledgeBaseConnector.graphDao);
            graph = databaseMerger.mergeWithCHA(packageVersionId);
        } else {
            try {
                graph = KnowledgeBaseConnector.graphDao.getGraphData(packageVersionId);
            } catch (RocksDBException e) {
                return new ResponseEntity<>("Could not retrieve callgraph from the graph database",
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
            if (graph == null) {
                return new ResponseEntity<>("Callgraph not found in the graph database", HttpStatus.NOT_FOUND);
            }
        }
        var json = new JSONObject();
        var nodesJson = new JSONArray();
        graph.nodes().stream().forEach(nodesJson::put);
        var edgesJson = new JSONArray();
        graph.edgeSet().stream().map(e -> new long[] {e.firstLong(), e.secondLong()}).forEach(edgesJson::put);
        json.put("nodes", nodesJson);
        json.put("edges", edgesJson);
        return new ResponseEntity<>(json.toString(), HttpStatus.OK);
    }
}
