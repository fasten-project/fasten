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

import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class CallableApi {

    private final CallableApiService service;

    public CallableApi(CallableApiService service) {
        this.service = service;
    }

    @GetMapping(value = "/mvn/packages/{pkg}/{pkg_ver}/callables", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageCallables(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestParam(required = false, defaultValue = "0") int offset,
                                               @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit,
                                               @RequestParam(required = false) String artifactRepository,
                                               @RequestParam(required = false) Long releaseDate) {
        return service.getPackageCallables(package_name, package_version, offset, limit, artifactRepository, releaseDate);
    }

    @PostMapping(value = "/mvn/packages/{pkg}/{pkg_ver}/callable/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getCallableMetadata(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestBody String fasten_uri,
                                               @RequestParam(value = "artifactRepository", required = false) String artifactRepo,
                                               @RequestParam(required = false) Long releaseDate) {
        return service.getCallableMetadata(package_name, package_version, fasten_uri, artifactRepo, releaseDate);
    }

    @PostMapping(value = "/callables", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getCallables(@RequestBody List<Long> callableIDs) {
        return service.getCallables(callableIDs);
    }
}
