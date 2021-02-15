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
import eu.fasten.analyzer.restapiplugin.mvn.LazyIngestionProvider;
import eu.fasten.analyzer.restapiplugin.mvn.api.ResolutionApiService;
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.MavenResolver;
import eu.fasten.core.maven.data.Revision;
import org.json.JSONArray;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class ResolutionApiServiceImpl implements ResolutionApiService {

    @Override
    public ResponseEntity<String> resolveDependencies(String package_name, String version, boolean transitive, long timestamp, boolean useDepGraph) {
        if (!KnowledgeBaseConnector.kbDao.assertPackageExistence(package_name, version)) {
            LazyIngestionProvider.ingestArtifactWithDependencies(package_name, version);
            return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later", HttpStatus.CREATED);
        }
        var groupId = package_name.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = package_name.split(Constants.mvnCoordinateSeparator)[1];
        Set<Revision> depSet;
        if (useDepGraph) {
            depSet = KnowledgeBaseConnector.graphResolver.resolveDependencies(groupId,
                    artifactId, version, timestamp, KnowledgeBaseConnector.dbContext, transitive);
        } else {
            var mavenResolver = new MavenResolver();
            depSet = mavenResolver.resolveFullDependencySetOnline(groupId, artifactId, version, timestamp, KnowledgeBaseConnector.dbContext);
        }
        var jsonArray = new JSONArray();
        depSet.stream().map(Revision::toJSON).peek(json -> {
            var group = json.getString("groupId");
            var artifact = json.getString("artifactId");
            var ver = json.getString("version");
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
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
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                    artifact.charAt(0), artifact, artifact, group, ver);
            json.put("url", url);
        }).forEach(jsonArray::put);
        var result = jsonArray.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
