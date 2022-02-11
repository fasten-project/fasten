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
import eu.fasten.analyzer.restapiplugin.LazyIngestionProvider;
import eu.fasten.analyzer.restapiplugin.RestApplication;
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.data.PackageVersionNotFoundException;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/packages")
public class PackageApi {

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getAllPackages(@RequestParam(required = false, defaultValue = "0") int offset,
                                          @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        var result = KnowledgeBaseConnector.kbDao.getAllPackages(offset, limit);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }


    @GetMapping(value = "/{pkg}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageLastVersion(@PathVariable("pkg") String package_name) {
        String result = KnowledgeBaseConnector.kbDao.getPackageLastVersion(package_name);
        if (result == null) {
            return new ResponseEntity<>("Package not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }


    @GetMapping(value = "/{pkg}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageVersions(@PathVariable("pkg") String package_name,
                                              @RequestParam(required = false, defaultValue = "0") int offset,
                                              @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageVersions(KnowledgeBaseConnector.forge, package_name, offset, limit);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageVersion(@PathVariable("pkg") String package_name,
                                             @PathVariable("pkg_ver") String package_version,
                                             @RequestParam(value = "artifactRepository", required = false) String artifactRepo,
                                             @RequestParam(required = false) Long releaseDate) {
            String result = KnowledgeBaseConnector.kbDao.getPackageVersion(package_name, package_version);
            if (result == null) {
                try {
                    try {
                        LazyIngestionProvider.ingestArtifactIfNecessary(package_name, package_version, artifactRepo, releaseDate);
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
                    } catch (IOException ex) {
                        return new ResponseEntity<>("Couldn't ingest the artifact", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                } catch (IllegalArgumentException ex) {
                    return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
                }
                return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later", HttpStatus.CREATED);
            }
            result = result.replace("\\/", "/");
            return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageMetadata(@PathVariable("pkg") String package_name,
                                              @PathVariable("pkg_ver") String package_version) {
        String result = KnowledgeBaseConnector.kbDao.getPackageMetadata(
                package_name, package_version);
        if (result == null) {
            return new ResponseEntity<>("Package version not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/callgraph", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageCallgraph(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestParam(required = false, defaultValue = "0") int offset,
                                               @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit,
                                               @RequestParam(value = "artifactRepository", required = false) String artifactRepo,
                                               @RequestParam(required = false) Long releaseDate) {
        try {
            String result;
            try {
                result = KnowledgeBaseConnector.kbDao.getPackageCallgraph(
                        package_name, package_version, offset, limit);
            } catch (PackageVersionNotFoundException e) {
                LazyIngestionProvider.ingestArtifactIfNecessary(package_name, package_version, artifactRepo, releaseDate);
                return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later", HttpStatus.CREATED);
            }
            result = result.replace("\\/", "/");
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> searchPackageNames(@RequestParam("packageName") String packageName,
                                              @RequestParam(required = false, defaultValue = "0") int offset,
                                              @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        var result = KnowledgeBaseConnector.kbDao.searchPackageNames(packageName, offset, limit);
        if (result == null) {
            return new ResponseEntity<>("Packages version not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/rcg", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getERCGLink(@PathVariable("pkg") String packageName,
                                       @PathVariable("pkg_ver") String version,
                                       @RequestParam(value = "artifactRepository", required = false) String artifactRepo,
                                       @RequestParam(required = false) Long releaseDate) {
        String result;
        String url;
        if (!KnowledgeBaseConnector.kbDao.assertPackageExistence(packageName, version)) {
            try {
                LazyIngestionProvider.ingestArtifactIfNecessary(packageName, version, artifactRepo, releaseDate);
            } catch (IllegalArgumentException ex) {
                return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);     
            } catch (IOException ex) {
                return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later", HttpStatus.CREATED);
        }
        switch (KnowledgeBaseConnector.forge) {
            case "maven": {
                var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
                var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[1];
                url = String.format("%smvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                        artifactId.charAt(0), artifactId, artifactId, groupId, version).replace("\\/", "/");
                break;
            }
            case "pypi": {
                url = String.format("%s/%s/%s/%s/%s/cg.json", KnowledgeBaseConnector.rcgBaseUrl + KnowledgeBaseConnector.forge + "/" + KnowledgeBaseConnector.forge,
                    "callgraphs", packageName.charAt(0), packageName, version).replace("\\/", "/");
                break;
            }
            case "debian": {
                url = String.format("%s/%s/%s/%s/buster/%s/amd64/file.json", KnowledgeBaseConnector.rcgBaseUrl + KnowledgeBaseConnector.forge,
                    "callgraphs", packageName.charAt(0), packageName, version).replace("\\/", "/");
                break;
            }
            default:
                return new ResponseEntity<>("Incorrect forge", HttpStatus.BAD_REQUEST);
        }
        result = MavenUtilities.sendGetRequest(url);
        if (result == null) {
            return new ResponseEntity<>("Could not find the requested data at " + url, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}

