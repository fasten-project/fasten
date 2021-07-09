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
import eu.fasten.analyzer.restapiplugin.mvn.api.PackageApiService;
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.data.PackageVersionNotFoundException;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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
            return new ResponseEntity<>("Package not found", HttpStatus.NOT_FOUND);
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
                                                    String package_version, String artifactRepo,
                                                    Long date) {
        String result = KnowledgeBaseConnector.kbDao.getPackageVersion(
                package_name, package_version);
        if (result == null) {
            try {
                try {
                    LazyIngestionProvider.ingestArtifactIfNecessary(package_name, package_version, artifactRepo, date);
                } catch (IllegalArgumentException ex) {
                    return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
                }
            } catch (IllegalArgumentException ex) {
                return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later", HttpStatus.CREATED);
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
                                                      int limit,
                                                      String artifactRepo,
                                                      Long date) {
        String result;
        try {
            result = KnowledgeBaseConnector.kbDao.getPackageCallgraph(
                    package_name, package_version, offset, limit);
        } catch (PackageVersionNotFoundException e) {
            LazyIngestionProvider.ingestArtifactIfNecessary(package_name, package_version, artifactRepo, date);
            return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later", HttpStatus.CREATED);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> searchPackageNames(String packageName, int offset, int limit) {
        var result = KnowledgeBaseConnector.kbDao.searchPackageNames(packageName, offset, limit);
        if (result == null) {
            return new ResponseEntity<>("Packages version not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getERCGLink(String packageName, String version, String artifactRepo, Long date) {
        if (KnowledgeBaseConnector.kbDao.assertPackageExistence(packageName, version)) {
            var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
            var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[1];
            var url = String.format("%smvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                    artifactId.charAt(0), artifactId, artifactId, groupId, version).replace("\\/", "/");
            var result = MavenUtilities.sendGetRequest(url);
            if (result == null) {
                return new ResponseEntity<>("Could not find the requested data at " + url, HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } else {
            LazyIngestionProvider.ingestArtifactIfNecessary(packageName, version, artifactRepo, date);
            return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later", HttpStatus.CREATED);
        }
    }
}
