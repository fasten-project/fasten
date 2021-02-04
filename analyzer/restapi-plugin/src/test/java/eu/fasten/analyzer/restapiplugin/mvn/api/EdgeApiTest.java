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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EdgeApiTest {

    private EdgeApiService service;
    private EdgeApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(EdgeApiService.class);
        api = new EdgeApi(service);
    }

    @Test
    public void getPackageEdgesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package version edges", HttpStatus.OK);
        Mockito.when(service.getPackageEdges(packageName, version, offset, limit, null, null)).thenReturn(response);
        var result = api.getPackageEdges(packageName, version, offset, limit, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getPackageEdges(packageName, version, offset, limit, null, null);
    }
}
