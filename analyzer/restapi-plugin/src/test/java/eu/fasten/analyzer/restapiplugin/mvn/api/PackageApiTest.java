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

import static org.junit.jupiter.api.Assertions.assertEquals;
import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PackageApiTest {

    private PackageApiService service;
    private PackageApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(PackageApiService.class);
        api = new PackageApi(service);
    }

    @Test
    void getAllPackagesTest() {
        var response = new ResponseEntity<>("all packages", HttpStatus.OK);
        Mockito.when(service.getAllPackages(offset, limit)).thenReturn(response);
        var result = api.getAllPackages(offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getAllPackages(offset, limit);
    }

    @Test
    void getPackageLastVersionTest() {
        var packageName = "pkg name";
        var response = new ResponseEntity<>("latest package version", HttpStatus.OK);
        Mockito.when(service.getPackageLastVersion(packageName)).thenReturn(response);
        var result = api.getPackageLastVersion(packageName);
        assertEquals(response, result);
        Mockito.verify(service).getPackageLastVersion(packageName);
    }

    @Test
    void getPackageVersionsTest() {
        var packageName = "pkg name";
        var response = new ResponseEntity<>("packages", HttpStatus.OK);
        Mockito.when(service.getPackageVersions(packageName, offset, limit)).thenReturn(response);
        var result = api.getPackageVersions(packageName, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getPackageVersions(packageName, offset, limit);
    }

    @Test
    void getPackageVersionTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package version", HttpStatus.OK);
        Mockito.when(service.getPackageVersion(packageName, version, null, null)).thenReturn(response);
        var result = api.getPackageVersion(packageName, version, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getPackageVersion(packageName, version, null, null);
    }

    @Test
    void getPackageMetadataTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package version metadata", HttpStatus.OK);
        Mockito.when(service.getPackageMetadata(packageName, version)).thenReturn(response);
        var result = api.getPackageMetadata(packageName, version);
        assertEquals(response, result);
        Mockito.verify(service).getPackageMetadata(packageName, version);
    }

    @Test
    void getPackageCallgraphTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package version callgraph", HttpStatus.OK);
        Mockito.when(service.getPackageCallgraph(packageName, version, offset, limit, null, null)).thenReturn(response);
        var result = api.getPackageCallgraph(packageName, version, offset, limit, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getPackageCallgraph(packageName, version, offset, limit, null, null);
    }

    @Test
    void searchPackageNamesTest() {
        var packageName = "pkg name";
        var response = new ResponseEntity<>("matching packages", HttpStatus.OK);
        Mockito.when(service.searchPackageNames(packageName, offset, limit)).thenReturn(response);
        var result = api.searchPackageNames(packageName, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).searchPackageNames(packageName, offset, limit);
    }

    @Test
    void getERCGLinkTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("link to ercg", HttpStatus.OK);
        Mockito.when(service.getERCGLink(packageName, version, null, null)).thenReturn(response);
        var result = api.getERCGLink(packageName, version, null, null);
        assertEquals(response, result);
        Mockito.verify(service).getERCGLink(packageName, version, null, null);
    }
}