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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mvn/packages")
public class PackageApi {

    @Autowired
    PackageApiService service;

    @GetMapping(value = "/{pkg}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageLastVersion(@PathVariable("pkg") String package_name) {
        return service.getPackageLastVersion(package_name);
    }

    @GetMapping(value = "/{pkg}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageVersions(@PathVariable("pkg") String package_name,
                                              @RequestParam(required = false, defaultValue = "0") int offset,
                                              @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        return service.getPackageVersions(package_name, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageVersion(@PathVariable("pkg") String package_name,
                                             @PathVariable("pkg_ver") String package_version,
                                             @RequestParam(required = false, defaultValue = "0") int offset,
                                             @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        return service.getPackageVersion(package_name, package_version, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageMetadata(@PathVariable("pkg") String package_name,
                                              @PathVariable("pkg_ver") String package_version,
                                              @RequestParam(required = false, defaultValue = "0") int offset,
                                              @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        return service.getPackageMetadata(package_name, package_version, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/callgraph", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageCallgraph(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestParam(required = false, defaultValue = "0") int offset,
                                               @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        return service.getPackageCallgraph(package_name, package_version, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageCallgraph(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestParam(required = false, defaultValue = "false") boolean stitch,
                                               @RequestParam(required = false, defaultValue = "false") boolean enrichEdges,
                                               @RequestParam(required = false, defaultValue = "-1") long timestamp) {
        return service.enrichArtifact(package_name, package_version, stitch, enrichEdges, timestamp);
    }

    @GetMapping(value = "/packages/search", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> searchPackageNames(@RequestParam("packageName") String packageName,
                                              @RequestParam(required = false, defaultValue = "0") int offset,
                                              @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) int limit) {
        return service.searchPackageNames(packageName, offset, limit);
    }
}

