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
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.data.PackageVersionNotFoundException;
import eu.fasten.core.maven.utils.MavenUtilities;

@RestController
@RequestMapping("/packages")
public class PackageApi {

    private LazyIngestionProvider ingestion = new LazyIngestionProvider();

    public void setLazyIngestionProvider(LazyIngestionProvider ingestion) {
        this.ingestion = ingestion;
    };

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getAllPackages(@RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        var result = KnowledgeBaseConnector.kbDao.getAllPackages(offset, limit);
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/{pkg}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageLastVersion(@PathVariable("pkg") String packageName) {
        String result = KnowledgeBaseConnector.kbDao.getPackageLastVersion(packageName);
        if (result == null) {
            return Responses.packageNotFound();
        }
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/{pkg}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageVersions(@PathVariable("pkg") String packageName,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageVersions(packageName, offset, limit);
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageVersion(@PathVariable("pkg") String packageName,
            @PathVariable("pkg_ver") String packageVersion,
            @RequestParam(value = "artifactRepository", required = false) String artifactRepo,
            @RequestParam(required = false) Long releaseDate) {

        var hasNeededIngestion = ingestion.ingestArtifactIfNecessary(packageName, packageVersion,
                artifactRepo, releaseDate);
        if (hasNeededIngestion) {
            return Responses.lazyIngestion();
        }
        var result = KnowledgeBaseConnector.kbDao.getPackageVersion(packageName, packageVersion);
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageMetadata(@PathVariable("pkg") String packageName,
            @PathVariable("pkg_ver") String packageVersion) {
        String result = KnowledgeBaseConnector.kbDao.getPackageMetadata(packageName, packageVersion);
        if (result == null) {
            return Responses.packageVersionNotFound();
        }
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/callgraph", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageCallgraph(@PathVariable("pkg") String packageName,
            @PathVariable("pkg_ver") String packageVersion,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit,
            @RequestParam(value = "artifactRepository", required = false) String artifactRepo,
            @RequestParam(required = false) Long releaseDate) {
        String result;
        try {
            result = KnowledgeBaseConnector.kbDao.getPackageCallgraph(packageName, packageVersion, offset, limit);
        } catch (PackageVersionNotFoundException e) {
            ingestion.ingestArtifactIfNecessary(packageName, packageVersion, artifactRepo, releaseDate);
            return Responses.lazyIngestion();
        }
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> searchPackageNames(@RequestParam("packageName") String packageName,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        var result = KnowledgeBaseConnector.kbDao.searchPackageNames(packageName, offset, limit);
        if (result == null) {
            return Responses.packageVersionNotFound();
        }
        result = result.replace("\\/", "/");
        return Responses.ok(result);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/rcg", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getERCGLink(@PathVariable("pkg") String packageName, @PathVariable("pkg_ver") String version,
            @RequestParam(value = "artifactRepository", required = false) String artifactRepo,
            @RequestParam(required = false) Long releaseDate) {
        String result;
        String url;
        if (!KnowledgeBaseConnector.kbDao.assertPackageExistence(packageName, version)) {
            ingestion.ingestArtifactIfNecessary(packageName, version, artifactRepo, releaseDate);
            return Responses.lazyIngestion();
        }
        switch (KnowledgeBaseConnector.forge) {
        case Constants.mvnForge: {
            var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
            var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[1];
            url = String.format("%smvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl, artifactId.charAt(0),
                    artifactId, artifactId, groupId, version).replace("\\/", "/");
            break;
        }
        case Constants.pypiForge: {
            url = String.format("%s/%s/%s/%s/%s/cg.json",
                    KnowledgeBaseConnector.rcgBaseUrl + KnowledgeBaseConnector.forge.toLowerCase() + "/"
                            + KnowledgeBaseConnector.forge.toLowerCase(),
                    "callgraphs", packageName.charAt(0), packageName, version).replace("\\/", "/");
            break;
        }
        case Constants.debianForge: {
            url = String.format("%s/%s/%s/%s/buster/%s/amd64/file.json",
                    KnowledgeBaseConnector.rcgBaseUrl + KnowledgeBaseConnector.forge, "callgraphs",
                    packageName.charAt(0), packageName, version).replace("\\/", "/");
            break;
        }
        default:
            return Responses.incorrectForge();
        }
        result = MavenUtilities.sendGetRequest(url);
        if (result == null) {
            return Responses.dataNotFound();
        }
        return Responses.ok(result);
    }
}