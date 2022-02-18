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

package eu.fasten.analyzer.restapiplugin.api;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.dependents.GraphResolver;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.MavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.analyzer.restapiplugin.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.LazyIngestionProvider;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Lazy
@RestController
public class ResolutionApi {

    private static final Logger logger = LoggerFactory.getLogger(ResolutionApi.class);
    private GraphMavenResolver graphMavenResolver;
    private GraphResolver graphResolver ;

    public ResolutionApi() {
        switch(KnowledgeBaseConnector.forge) {
            case Constants.mvnForge: {
                try {
                    var graphMavenResolver = new GraphMavenResolver();
                    graphMavenResolver.buildDependencyGraph(KnowledgeBaseConnector.dbContext, KnowledgeBaseConnector.dependencyGraphPath);
                    this.graphMavenResolver = graphMavenResolver;
                } catch (Exception e) {
                    logger.error("Error constructing dependency graph maven resolver", e);
                    System.exit(1);
                }
                break;
            }
            default: {
                try {
                    var graphResolver = new GraphResolver();
                    graphResolver.buildDependencyGraph(KnowledgeBaseConnector.dbContext, KnowledgeBaseConnector.dependencyGraphPath);
                    this.graphResolver = graphResolver;
                } catch (Exception e) {
                    logger.error("Error constructing dependency graph resolver", e);
                    System.exit(1);
                }
            }
        }
    }
    @GetMapping(value = "/packages/{pkg}/{pkg_ver}/resolve/dependencies", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveDependencies(@PathVariable("pkg") String packageName,
                                               @PathVariable("pkg_ver") String packageVersion,
                                               @RequestParam(required = false, defaultValue = "true") boolean transitive,
                                               @RequestParam(required = false, defaultValue = "-1") long timestamp,
                                               @RequestParam(required = false, defaultValue = "true") boolean useDepGraph) {
        switch (KnowledgeBaseConnector.forge) {
            case Constants.mvnForge: {
                if (!KnowledgeBaseConnector.kbDao.assertPackageExistence(packageName, packageVersion)) {
                    try {
                        LazyIngestionProvider.ingestArtifactWithDependencies(packageName, packageVersion);
                    } catch (IllegalArgumentException e) {
                        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
                    } catch (IOException e) {
                        return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later", HttpStatus.CREATED);
                }
                var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
                var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[1];
                Set<Revision> depSet;
                if (useDepGraph) {
                    depSet = this.graphMavenResolver.resolveDependencies(groupId,
                            artifactId, packageVersion, timestamp, KnowledgeBaseConnector.dbContext, transitive);
                } else {
                    var mavenResolver = new MavenResolver();
                    depSet = mavenResolver.resolveDependencies(groupId + ":" + artifactId + ":" + packageVersion);
                }
                var jsonArray = new JSONArray();
                depSet.stream().map(Revision::toJSON).peek(json -> {
                    var group = json.getString("groupId");
                    var artifact = json.getString("artifactId");
                    var ver = json.getString("version");
                    var url = String.format("%smvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                            artifact.charAt(0), artifact, artifact, group, ver);
                    json.put("url", url);
                }).forEach(jsonArray::put);
                var result = jsonArray.toString();
                result = result.replace("\\/", "/");
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
            default: {
                var query = KnowledgeBaseConnector.dependencyResolverAddress+"/dependencies/"+packageName+"/"+packageVersion;
                var result = MavenUtilities.sendGetRequest(query);
                if (result == null || result.contains("\"error\"")) {
                    return new ResponseEntity<>("Could not find the requested data", HttpStatus.NOT_FOUND);
                }
                result = result.replaceAll("\\s+","");
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
        }
    }

    @GetMapping(value = "/packages/{pkg}/{pkg_ver}/resolve/dependents", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveDependents(@PathVariable("pkg") String packageName,
                                             @PathVariable("pkg_ver") String packageVersion,
                                             @RequestParam(required = false, defaultValue = "true") boolean transitive,
                                             @RequestParam(required = false, defaultValue = "-1") long timestamp) {
        JSONArray jsonArray = new JSONArray();
        switch (KnowledgeBaseConnector.forge) {
            case Constants.mvnForge: {
                var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
                var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[1];
                try {
                    var depSet = this.graphMavenResolver.resolveDependents(groupId,
                        artifactId, packageVersion, timestamp, transitive);
                     depSet.stream().map(eu.fasten.core.maven.data.Revision::toJSON).peek(json -> {
                        var group = json.getString("groupId");
                        var artifact = json.getString("artifactId");
                        var ver = json.getString("version");
                        var url = String.format("%smvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                                artifact.charAt(0), artifact, artifact, group, ver);
                        json.put("url", url);
                    }).forEach(jsonArray::put);
               } catch (RuntimeException e) {
                    return new ResponseEntity<>("Failed to resolve dependents for revision "+packageName+":"+ packageVersion , HttpStatus.NOT_FOUND);
               }
                break;
            }
            case Constants.pypiForge: {
                try {
                    var depSet = this.graphResolver.resolveDependents(packageName,
                    packageVersion, timestamp, transitive);
                    depSet.stream().map(eu.fasten.core.dependents.data.Revision::toJSON).peek(json -> {
                        var dep_name = json.getString("package");
                        var ver = json.getString("version");
                        var url = String.format("%spypi/pypi/callgraphs/%s/%s/%s/cg.json", KnowledgeBaseConnector.rcgBaseUrl,
                            dep_name.charAt(0), dep_name, ver);
                        json.put("url", url);
                    }).forEach(jsonArray::put);
                } catch (RuntimeException e) {
                    return new ResponseEntity<>("Failed to resolve dependents for revision "+packageName+":"+ packageVersion , HttpStatus.NOT_FOUND);
                }
                break;
            }
            case Constants.debianForge: {
                try {
                    var depSet = this.graphResolver.resolveDependents(packageName,
                    packageVersion, timestamp, transitive);
                    depSet.stream().map(eu.fasten.core.dependents.data.Revision::toJSON).peek(json -> {
                        var dep_name = json.getString("package");
                        var ver = json.getString("version");
                        var url = String.format("%sdebian/callgraphs/%s/%s/buster/%s/amd64/file.json", KnowledgeBaseConnector.rcgBaseUrl,
                                dep_name.charAt(0), dep_name, ver);
                        json.put("url", url);
                    }).forEach(jsonArray::put);
                } catch (RuntimeException e) {
                    return new ResponseEntity<>("Failed to resolve dependents for revision "+packageName+":"+ packageVersion , HttpStatus.NOT_FOUND);
                }
                break;
            }
            default:
                return new ResponseEntity<>("Incorrect forge", HttpStatus.BAD_REQUEST);
        }
        var result = jsonArray.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping(value = "/resolve_dependencies", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveMultipleDependencies(@RequestBody List<String> coordinates) {
        switch (KnowledgeBaseConnector.forge) {
            case Constants.mvnForge: {
                var revisions = coordinates.stream().map(c -> {
                    var groupId = c.split(Constants.mvnCoordinateSeparator)[0];
                    var artifactId = c.split(Constants.mvnCoordinateSeparator)[1];
                    var version = c.split(Constants.mvnCoordinateSeparator)[2];
                    var id = KnowledgeBaseConnector.kbDao.getPackageVersionID(groupId + Constants.mvnCoordinateSeparator + artifactId, version);
                    return new Revision(id, groupId, artifactId, version, new Timestamp(-1));
                }).collect(Collectors.toSet());
                var virtualNode = this.graphMavenResolver.addVirtualNode(new ObjectLinkedOpenHashSet<>(revisions));
                var depSet = this.graphMavenResolver.resolveDependencies(virtualNode, KnowledgeBaseConnector.dbContext, true);
                this.graphMavenResolver.removeVirtualNode(virtualNode);
                var jsonArray = new JSONArray();
                depSet.stream().map(r -> {
                    var json = new JSONObject();
                    var url = String.format("%smvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                            r.artifactId.charAt(0), r.artifactId, r.artifactId, r.groupId, r.version);
                    json.put(String.valueOf(r.id), url);
                    return json;
                }).forEach(jsonArray::put);
                var result = jsonArray.toString();
                result = result.replace("\\/", "/");
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
            default: {
                ObjectLinkedOpenHashSet deps = new ObjectLinkedOpenHashSet<String>();
                coordinates.forEach(c -> {
                    var packageName = c.split(Constants.mvnCoordinateSeparator)[0];
                    var packageVersion = c.split(Constants.mvnCoordinateSeparator)[1];
                    var query = KnowledgeBaseConnector.dependencyResolverAddress+"/dependencies/"+packageName+"/"+packageVersion;
                    var requestResult = MavenUtilities.sendGetRequest(query);
                    var depList = new JSONArray(requestResult);
                     depList.forEach(item -> {
                        JSONObject obj = (JSONObject) item;
                        if (!deps.contains(obj.toString())) {
                            deps.add(obj.toString());
                        }
                    });
                });
            List<String> result = new ArrayList<String>(deps);
            return new ResponseEntity<>(result.toString(), HttpStatus.OK);
            }
        }
    }

    @GetMapping(value = "/__INTERNAL__/packages/{pkg_version_id}/directedgraph", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getDirectedGraph(@PathVariable("pkg_version_id") long packageVersionId,
                                            @RequestParam(required = false, defaultValue = "false") boolean needStitching,
                                            @RequestParam(required = false, defaultValue = "-1") long timestamp) {
        DirectedGraph graph;
        if (needStitching) {
            var mavenCoordinate = KnowledgeBaseConnector.kbDao.getMavenCoordinate(packageVersionId);
            if (mavenCoordinate == null) {
                return new ResponseEntity<>("Package version ID not found", HttpStatus.NOT_FOUND);
            }
            var groupId = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[0];
            var artifactId = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[1];
            var version = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[2];
            var depSet = this.graphMavenResolver.resolveDependencies(groupId,
                    artifactId, version, timestamp, KnowledgeBaseConnector.dbContext, true);
            var depIds = depSet.stream().map(r -> r.id).collect(Collectors.toSet());
            var databaseMerger = new CGMerger(depIds, KnowledgeBaseConnector.dbContext, KnowledgeBaseConnector.graphDao);
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
        graph.edgeSet().stream().map(e -> new long[]{e.firstLong(), e.secondLong()}).forEach(edgesJson::put);
        json.put("nodes", nodesJson);
        json.put("edges", edgesJson);
        var result = json.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
