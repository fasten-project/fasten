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

package eu.fasten.analyzer.restapiplugin.mvn.api;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.core.dependents.GraphResolver;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Lazy
@RestController
public class PyPiResolutionApi {

    private static final Logger logger = LoggerFactory.getLogger(PyPiResolutionApi.class);
    private GraphResolver graphPyPiResolver;

    public PyPiResolutionApi() {
        try {
            var graphPyPiResolver = new GraphResolver();
            graphPyPiResolver.buildDependencyGraph(KnowledgeBaseConnector.dbContext, KnowledgeBaseConnector.dependencyGraphPath);
            this.graphPyPiResolver = graphPyPiResolver;
        } catch (Exception e) {
            logger.error("Error constructing PyPi dependency graph resolver", e);
            System.exit(1);
        }
    }

    @GetMapping(value = "/pypi/packages/{pkg}/{pkg_ver}/resolve/dependents", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveDependents(@PathVariable("pkg") String package_name,
                                             @PathVariable("pkg_ver") String package_version,
                                             @RequestParam(required = false, defaultValue = "true") boolean transitive,
                                             @RequestParam(required = false, defaultValue = "-1") long timestamp) {

        timestamp = ((timestamp == -1) ? this.graphPyPiResolver.getCreatedAt(package_name, package_version, KnowledgeBaseConnector.dbContext): timestamp);
        var depSet = this.graphPyPiResolver.resolveDependents(package_name,
            package_version, timestamp, transitive);
        var jsonArray = new JSONArray();
        depSet.stream().map(eu.fasten.core.dependents.data.Revision::toJSON).peek(json -> {
            var dep_name = json.getString("package");
            var ver = json.getString("version");
            var url = String.format("%spypi/pypi/callgraphs/%s/%s/%s/cg.json", KnowledgeBaseConnector.rcgBaseUrl,
                    dep_name.charAt(0), dep_name, ver);
            json.put("url", url);
        }).forEach(jsonArray::put);
        var result = jsonArray.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
