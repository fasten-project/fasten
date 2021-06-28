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
import eu.fasten.analyzer.restapiplugin.mvn.api.PackageVersionApiService;
import eu.fasten.core.data.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class PackageVersionApiServiceImpl implements PackageVersionApiService {

    @Override
    public ResponseEntity<String> getERCGLink(long packageVersionId) {
        var artifact = KnowledgeBaseConnector.kbDao.getArtifactName(packageVersionId);
        if (artifact == null) {
            return new ResponseEntity<>("Package version not found", HttpStatus.NOT_FOUND);
        }
        var coordinate = artifact.split(Constants.mvnCoordinateSeparator);
        var groupId = coordinate[0];
        var artifactId = coordinate[1];
        var version = coordinate[2];
        String result = String.format("%smvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                artifactId.charAt(0), artifactId, artifactId, groupId, version);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
