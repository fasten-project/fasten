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

public class BinaryModuleApiTest {

    private BinaryModuleApiService service;
    private BinaryModuleApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(BinaryModuleApiService.class);
        api = new BinaryModuleApi(service);
    }

    @Test
    public void getPackageBinaryModulesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package binary modules", HttpStatus.OK);
        Mockito.when(service.getPackageBinaryModules(packageName, version, offset, limit, null, null)).thenReturn(response);
        var result = api.getPackageBinaryModules(packageName, version, offset, limit, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getPackageBinaryModules(packageName, version, offset, limit, null, null);
    }

    @Test
    public void getBinaryModuleMetadataTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var binModule = "binary module";
        var response = new ResponseEntity<>("binary module metadata", HttpStatus.OK);
        Mockito.when(service.getBinaryModuleMetadata(packageName, version, binModule, null, null)).thenReturn(response);
        var result = api.getBinaryModuleMetadata(packageName, version, binModule, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getBinaryModuleMetadata(packageName, version, binModule, null, null);
    }

    @Test
    public void getBinaryModuleFilesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var binModule = "binary module";
        var response = new ResponseEntity<>("binary module files", HttpStatus.OK);
        Mockito.when(service.getBinaryModuleFiles(packageName, version, binModule, offset, limit, null, null)).thenReturn(response);
        var result = api.getBinaryModuleFiles(packageName, version, binModule, offset, limit, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getBinaryModuleFiles(packageName, version, binModule, offset, limit, null, null);
    }
}
