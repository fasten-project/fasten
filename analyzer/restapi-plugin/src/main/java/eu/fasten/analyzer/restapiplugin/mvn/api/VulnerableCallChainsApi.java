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

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/{forge}/packages/{pkg}/{pkg_ver}/vulnerable-call-chains")
public class VulnerableCallChainsApi {

    private final VulnerableCallChainsApiService service;

    public VulnerableCallChainsApi(VulnerableCallChainsApiService service) {
        this.service = service;
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getChainsForPackage(@PathVariable("forge") String forge,
                                               @PathVariable("pkg") String packageName,
                                               @PathVariable("pkg_ver") String packageVersion) {
        return service.getChainsForPackage(forge, packageName, packageVersion);
    }

    @GetMapping(value = "/module", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getChainsForPackage(@PathVariable("forge") String forge,
                                               @PathVariable("pkg") String packageName,
                                               @PathVariable("pkg_ver") String packageVersion,
                                               @RequestParam("raw_path") String rawPath) {
        return service.getChainsForModule(forge, packageName, packageVersion, rawPath);
    }

    @GetMapping(value = "/callable", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getChainsForCallable(@PathVariable("forge") String forge,
                                                @PathVariable("pkg") String packageName,
                                                @PathVariable("pkg_ver") String packageVersion,
                                                @RequestParam("raw_path") String rawPath) {
        return service.getChainsForCallable(forge, packageName, packageVersion, rawPath);
    }

}
