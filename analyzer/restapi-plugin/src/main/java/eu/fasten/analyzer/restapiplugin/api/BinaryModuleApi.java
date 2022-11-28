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

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.fasten.analyzer.restapiplugin.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.LazyIngestionProvider;
import eu.fasten.analyzer.restapiplugin.RestApplication;
import eu.fasten.core.maven.data.PackageVersionNotFoundException;

@RestController
@RequestMapping("/packages")
public class BinaryModuleApi {

    private LazyIngestionProvider ingestion = new LazyIngestionProvider();

    public void setLazyIngestionProvider(LazyIngestionProvider ingestion) {
        this.ingestion = ingestion;
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/binary-modules", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageBinaryModules(@PathVariable("pkg") String packageName,
                                                   @PathVariable("pkg_ver") String packageVersion,
                                                   @RequestParam(required = false, defaultValue = "0") int offset,
                                                   @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit,
                                                   @RequestParam(required = false) String artifactRepository,
                                                   @RequestParam(required = false) Long releaseDate) {
        String result;
        try {
            result = KnowledgeBaseConnector.kbDao.getPackageBinaryModules(
                    packageName, packageVersion, offset, limit);
        } catch (PackageVersionNotFoundException e) {
            ingestion.ingestArtifactIfNecessary(packageName, packageVersion, artifactRepository, releaseDate);
            return Responses.lazyIngestion();
        }
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/binary-modules/{binary}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getBinaryModuleMetadata(@PathVariable("pkg") String packageName,
                                                   @PathVariable("pkg_ver") String packageVersion,
                                                   @PathVariable("binary") String binary_module,
                                                   @RequestParam(required = false) String artifactRepository,
                                                   @RequestParam(required = false) Long releaseDate) {
        String result;
        try {
            result = KnowledgeBaseConnector.kbDao.getBinaryModuleMetadata(
                    packageName, packageVersion, binary_module);
        } catch (PackageVersionNotFoundException e) {
            ingestion.ingestArtifactIfNecessary(packageName, packageVersion, artifactRepository, releaseDate);
            return Responses.lazyIngestion();
        }
        if (result == null) {
            return Responses.binaryModuleNotFound();
        }
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/binary-modules/{binary}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getBinaryModuleFiles(@PathVariable("pkg") String packageName,
                                                @PathVariable("pkg_ver") String packageVersion,
                                                @PathVariable("binary") String binary_module,
                                                @RequestParam(required = false, defaultValue = "0") int offset,
                                                @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit,
                                                @RequestParam(required = false) String artifactRepository,
                                                @RequestParam(required = false) Long releaseDate) {
        String result;
        try {
            result = KnowledgeBaseConnector.kbDao.getBinaryModuleFiles(
                    packageName, packageVersion, binary_module, offset, limit);
        } catch (PackageVersionNotFoundException e) {
            ingestion.ingestArtifactIfNecessary(packageName, packageVersion, artifactRepository, releaseDate);
            return Responses.lazyIngestion();
        }
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }
}