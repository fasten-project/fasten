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
import eu.fasten.analyzer.restapiplugin.mvn.api.PackageApiService;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.merge.DatabaseMerger;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class PackageApiServiceImpl implements PackageApiService {

    @Override
    public ResponseEntity<String> getAllPackages(int offset, int limit) {
        var result = KnowledgeBaseConnector.kbDao.getAllPackages(offset, limit);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getPackageLastVersion(String package_name) {
        String result = KnowledgeBaseConnector.kbDao.getPackageLastVersion(package_name);
        if (result == null) {
            return new ResponseEntity<>("Latest package version not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getPackageVersions(String package_name,
                                                     int offset,
                                                     int limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageVersions(package_name, offset, limit);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getPackageVersion(String package_name,
                                                    String package_version) {
        String result = KnowledgeBaseConnector.kbDao.getPackageVersion(
                package_name, package_version);
        if (result == null) {
            return new ResponseEntity<>("Package version not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getPackageMetadata(String package_name,
                                                     String package_version) {
        String result = KnowledgeBaseConnector.kbDao.getPackageMetadata(
                package_name, package_version);
        if (result == null) {
            return new ResponseEntity<>("Package version not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getPackageCallgraph(String package_name,
                                                      String package_version,
                                                      int offset,
                                                      int limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageCallgraph(
                package_name, package_version, offset, limit);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> enrichArtifact(String package_name, String version, boolean stitch,
                                                 boolean enrichEdges, long timestamp) {
        var json = new JSONObject();
        var packageVersionId = KnowledgeBaseConnector.kbDao.getPackageVersionID(package_name, version);
        if (stitch) {
            var groupId = package_name.split(Constants.mvnCoordinateSeparator)[0];
            var artifactId = package_name.split(Constants.mvnCoordinateSeparator)[1];
            var depSet = KnowledgeBaseConnector.graphResolver.resolveDependencies(groupId,
                    artifactId, version, timestamp, KnowledgeBaseConnector.dbContext, true);
            var depIds = depSet.stream().map(r -> r.id).collect(Collectors.toSet());
            var databaseMerger = new DatabaseMerger(depIds, KnowledgeBaseConnector.dbContext, KnowledgeBaseConnector.graphDao);
            var graph = databaseMerger.mergeWithCHA(packageVersionId);
            if (graph == null) {
                return new ResponseEntity<>("Could not stitch provided artifacts", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            json = ResolutionApiServiceImpl.directedGraphToEnrichedJSON(graph, enrichEdges);
        } else {
            DirectedGraph graph;
            try {
                graph = KnowledgeBaseConnector.graphDao.getGraphData(packageVersionId);
            } catch (RocksDBException e) {
                return new ResponseEntity<>("Could not retrieve the graph from the graph database", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            if (graph == null) {
                return new ResponseEntity<>("Could not find the graph", HttpStatus.NOT_FOUND);
            }
            json = ResolutionApiServiceImpl.directedGraphToEnrichedJSON(graph, enrichEdges);
        }
        var result = json.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> searchPackageNames(String packageName, int offset, int limit) {
        var result = KnowledgeBaseConnector.kbDao.searchPackageNames(packageName, offset, limit);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getERCGLink(String packageName, String version) {
        var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[1];
        String result = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.limaUrl,
                artifactId.charAt(0), artifactId, artifactId, groupId, version);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
