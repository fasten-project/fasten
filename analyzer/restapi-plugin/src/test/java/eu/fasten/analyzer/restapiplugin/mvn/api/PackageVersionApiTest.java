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

public class PackageVersionApiTest {

    private PackageVersionApiService service;
    private PackageVersionApi api;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(PackageVersionApiService.class);
        api = new PackageVersionApi(service);
    }

    @Test
    public void getERCGLinkTest() {
        var id = 42L;
        var response = new ResponseEntity<>("ercg link", HttpStatus.OK);
        Mockito.when(service.getERCGLink(id)).thenReturn(response);
        var result = api.getERCGLink(id);
        assertEquals(response, result);
        Mockito.verify(service).getERCGLink(id);
    }
}
