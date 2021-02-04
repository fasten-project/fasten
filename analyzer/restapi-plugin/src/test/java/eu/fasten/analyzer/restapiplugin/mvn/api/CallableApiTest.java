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
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallableApiTest {

    private CallableApiService service;
    private CallableApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(CallableApiService.class);
        api = new CallableApi(service);
    }

    @Test
    public void getPackageCallablesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package binary callables", HttpStatus.OK);
        Mockito.when(service.getPackageCallables(packageName, version, offset, limit, null, null)).thenReturn(response);
        var result = api.getPackageCallables(packageName, version, offset, limit, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getPackageCallables(packageName, version, offset, limit, null, null);
    }

    @Test
    public void getCallableMetadataTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var callable = "callable";
        var response = new ResponseEntity<>("callable metadata", HttpStatus.OK);
        Mockito.when(service.getCallableMetadata(packageName, version, callable, null, null)).thenReturn(response);
        var result = api.getCallableMetadata(packageName, version, callable, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getCallableMetadata(packageName, version, callable, null, null);
    }

    @Test
    public void getCallablesTest() {
        var ids = List.of(1L, 2L, 3L);
        var response = new ResponseEntity<>("callables metadata map", HttpStatus.OK);
        Mockito.when(service.getCallables(ids)).thenReturn(response);
        var result = api.getCallables(ids);
        assertEquals(response, result);
        Mockito.verify(service).getCallables(ids);
    }
}
