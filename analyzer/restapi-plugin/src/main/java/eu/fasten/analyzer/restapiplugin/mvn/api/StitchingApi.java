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
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class StitchingApi {

    private final StitchingApiService service;

    public StitchingApi(StitchingApiService service) {
        this.service = service;
    }

    @PostMapping(value = "/callables", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveCallables(@RequestBody List<Long> gidList) {
        return service.resolveCallablesToUris(gidList);
    }

    @PostMapping(value = "/metadata/callables", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getCallablesMetadata(@RequestBody List<String> fastenUris,
                                                @RequestParam(required = false, defaultValue = "false") boolean allAttributes,
                                                @RequestParam(required = false, defaultValue = "[]") List<String> attributes) {
        return service.getCallablesMetadata(fastenUris, allAttributes, attributes);
    }

    @PostMapping(value = "/resolve_dependencies", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveMultipleDependencies(@RequestBody List<String> mavenCoordinates) {
        return service.resolveMultipleDependencies(mavenCoordinates);
    }

    @GetMapping(value = "/__INTERNAL__/packages/{pkg_version_id}/directedgraph", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getDirectedGraph(@PathVariable("pkg_version_id") long packageVersionId,
                                            @RequestParam(required = false, defaultValue = "false") boolean needStitching,
                                            @RequestParam(required = false, defaultValue = "-1") long timestamp) {
        return service.getDirectedGraph(packageVersionId, needStitching, timestamp);
    }
}
