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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResolutionApiTest {

    private ResolutionApiService service;
    private ResolutionApi api;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ResolutionApiService.class);
        api = new ResolutionApi(service);
    }

    @Test
    public void resolveDependenciesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var transitive = true;
        var timestamp = -1L;
        var response = new ResponseEntity<>("dependencies", HttpStatus.OK);
        Mockito.when(service.resolveDependencies(packageName, version, transitive, timestamp, true)).thenReturn(response);
        var result = api.resolveDependencies(packageName, version, transitive, timestamp, true);
        assertEquals(response, result);
        Mockito.verify(service).resolveDependencies(packageName, version, transitive, timestamp, true);
    }

    @Test
    public void resolveDependentsTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var transitive = true;
        var timestamp = -1L;
        var response = new ResponseEntity<>("dependents", HttpStatus.OK);
        Mockito.when(service.resolveDependents(packageName, version, transitive, timestamp)).thenReturn(response);
        var result = api.resolveDependents(packageName, version, transitive, timestamp);
        assertEquals(response, result);
        Mockito.verify(service).resolveDependents(packageName, version, transitive, timestamp);
    }
}
