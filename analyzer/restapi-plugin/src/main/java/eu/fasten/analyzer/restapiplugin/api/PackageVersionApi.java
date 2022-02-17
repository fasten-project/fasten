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

import eu.fasten.analyzer.restapiplugin.KnowledgeBaseConnector;
import eu.fasten.core.data.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/package_version")
public class PackageVersionApi {


    @GetMapping(value = "/{id}/rcg", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getERCGLink(@PathVariable("id") long packageVersionId) {
        String url;
        var artifact = KnowledgeBaseConnector.kbDao.getArtifactName(packageVersionId);
        if (artifact == null) {
            return new ResponseEntity<>("Package version not found", HttpStatus.NOT_FOUND);
        }
        var coordinate = artifact.split(Constants.mvnCoordinateSeparator);
        switch (KnowledgeBaseConnector.forge) {
            case Constants.mvnForge: {
                var groupId = coordinate[0];
                var artifactId = coordinate[1];
                var version = coordinate[2];
                url = String.format("%smvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                artifactId.charAt(0), artifactId, artifactId, groupId, version);
                break;
            }
            case Constants.pypiForge: {
                var packageName = coordinate[0];
                var version = coordinate[1];
                url = String.format("%s/%s/%s/%s/%s/cg.json", KnowledgeBaseConnector.rcgBaseUrl + KnowledgeBaseConnector.forge + "/" + KnowledgeBaseConnector.forge,
                        "callgraphs", packageName.charAt(0), packageName, version).replace("\\/", "/");
                break;
            }
            case Constants.debianForge: {
                var packageName = coordinate[0];
                var version = coordinate[1];
                url = String.format("%s/%s/%s/%s/buster/%s/amd64/file.json", KnowledgeBaseConnector.rcgBaseUrl + KnowledgeBaseConnector.forge,
                        "callgraphs", packageName.charAt(0), packageName, version).replace("\\/", "/");
                break;
            }
            default:
                return new ResponseEntity<>("Incorrect forge", HttpStatus.BAD_REQUEST);
        }
        url = url.replace("\\/", "/");
        return new ResponseEntity<>(url, HttpStatus.OK);
    }
}

