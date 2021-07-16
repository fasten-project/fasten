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

import org.json.JSONArray;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class StitchingApi {

    private final StitchingApiService service;

    public StitchingApi(StitchingApiService service) {
        this.service = service;
    }

    @PostMapping(value = "/callable_uris", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveCallables(@RequestBody List<Long> gidList) {
        return service.resolveCallablesToUris(gidList);
    }

    @PostMapping(value = "/metadata/callables", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getCallablesMetadata(@RequestBody List<String> fastenUris,
                                                @RequestParam(required = false, defaultValue = "false") boolean allAttributes,
                                                @RequestParam(required = false, defaultValue = "[]") List<String> attributes) {
        return service.getCallablesMetadata(fastenUris, allAttributes, attributes);
    }

    @PostMapping(value = "/__INTERNAL__/ingest/batch", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> batchIngestArtifacts(@RequestBody String jsonArtifacts) {
        return service.batchIngestArtifacts(new JSONArray(jsonArtifacts));
    }
}
