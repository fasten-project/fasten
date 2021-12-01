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
import eu.fasten.analyzer.restapiplugin.mvn.api.DebianResolutionApiService;
import eu.fasten.core.dependents.GraphResolver;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


@Lazy
@Service
public class DebianResolutionApiServiceImpl implements DebianResolutionApiService {

    private static final Logger logger = LoggerFactory.getLogger(DebianResolutionApiServiceImpl.class);
    private GraphResolver graphDebianResolver;

    public DebianResolutionApiServiceImpl() {
        try {
            var graphDebianResolver = new GraphResolver();
            graphDebianResolver.buildDependencyGraph(KnowledgeBaseConnector.dbCContext, KnowledgeBaseConnector.dependencyDebianGraphPath);
            this.graphDebianResolver = graphDebianResolver;
        } catch (Exception e) {
            logger.error("Error constructing Debian dependency graph resolver", e);
            System.exit(1);
        }
    }

    @Override
    public ResponseEntity<String> resolveDependents(String package_name, String version, boolean transitive, long timestamp) {
        timestamp = ((timestamp == -1) ? this.graphDebianResolver.getCreatedAt(package_name, version, KnowledgeBaseConnector.dbCContext): timestamp);
        var depSet = this.graphDebianResolver.resolveDependents(package_name,
            version, timestamp, transitive);
        var jsonArray = new JSONArray();
        depSet.stream().map(eu.fasten.core.dependents.data.Revision::toJSON).peek(json -> {
            var dep_name = json.getString("package");
            var ver = json.getString("version");
            var url = String.format("%sdebian/callgraphs/%s/%s/buster/%s/amd64/file.json", KnowledgeBaseConnector.rcgBaseUrl,
                    dep_name.charAt(0), dep_name, ver);
            json.put("url", url);
        }).forEach(jsonArray::put);
        var result = jsonArray.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

}
