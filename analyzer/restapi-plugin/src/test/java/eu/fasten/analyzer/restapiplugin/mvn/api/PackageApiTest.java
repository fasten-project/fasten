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

package eu.fasten.analyzer.restapiplugin.api;

import eu.fasten.analyzer.restapiplugin.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.RestApplication;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.maven.data.PackageVersionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PackageApiTest {

    private PackageApi service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new PackageApi();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
        KnowledgeBaseConnector.forge = "mvn";
    }

    @Test
    void getAllPackagesTest() {
        var response = "all packages";
        Mockito.when(kbDao.getAllPackages(offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getAllPackages(offset, limit);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getAllPackages(offset, limit);
    }

    @Test
    void getPackageLastVersionPositiveTest() {
        var packageName = "group:artifact";
        var response = "a package";
        Mockito.when(kbDao.getPackageLastVersion(packageName)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageLastVersion(packageName);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getPackageLastVersion(packageName);
    }

    @Test
    void getPackageLastVersionNegativeTest() {
        var packageName = "group:artifact";
        Mockito.when(kbDao.getPackageLastVersion(packageName)).thenReturn(null);
        var result = service.getPackageLastVersion(packageName);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).getPackageLastVersion(packageName);
    }

    @Test
    void getPackageVersionsTest() {
        var packageName = "group:artifact";
        var response = "package versions";
        Mockito.when(kbDao.getPackageVersions(packageName, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageVersions(packageName, offset, limit);
        assertEquals(expected, result);
        Mockito.verify(kbDao, Mockito.times(1)).getPackageVersions(packageName, offset, limit);
    }

    @Test
    void getPackageVersionPositiveTest() {
        var packageName = "group:artifact";
        var version = "version";
        var response = "package version";
        Mockito.when(kbDao.getPackageVersion(packageName, version)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageVersion(packageName, version, null, null);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getPackageVersion(packageName, version);
    }

    @Test
    void getPackageVersionNegativeTest() {
        var packageName = "group:artifact";
        var version = "version";
        Mockito.when(kbDao.getPackageVersion(packageName, version)).thenReturn(null);
        var result = service.getPackageVersion(packageName, version, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).getPackageVersion(packageName, version);
    }

    @Test
    void getPackageVersionIngestionTest() {
        var packageName = "junit:junit";
        var version = "4.12";
        Mockito.when(kbDao.getPackageVersion(packageName, version)).thenReturn(null);
        var result = service.getPackageVersion(packageName, version, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).getPackageVersion(packageName, version);
    }

    @Test
    void getPackageMetadataPositiveTest() {
        var packageName = "group:artifact";
        var version = "version";
        var response = "package metadata";
        Mockito.when(kbDao.getPackageMetadata(packageName, version)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageMetadata(packageName, version);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getPackageMetadata(packageName, version);
    }

    @Test
    void getPackageNegativeMetadataTest() {
        var packageName = "group:artifact";
        var version = "version";
        Mockito.when(kbDao.getPackageMetadata(packageName, version)).thenReturn(null);
        var result = service.getPackageMetadata(packageName, version);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).getPackageMetadata(packageName, version);
    }

    @Test
    void getPackageCallgraphPositiveTest() throws IOException {
        var packageName = "group:artifact";
        var version = "version";
        var response = "package callgraph";
        Mockito.when(kbDao.getPackageCallgraph(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageCallgraph(packageName, version, offset, limit, null, null);
        Mockito.verify(kbDao).getPackageCallgraph(packageName, version, offset, limit);
        assertEquals(expected, result);
    }

    @Test
    void getPackageCallgraphIngestionTest() throws IOException {
        var packageName = "junit:junit";
        var version = "4.12";
        Mockito.when(kbDao.getPackageCallgraph(packageName, version, offset, limit)).thenThrow(new PackageVersionNotFoundException("Error"));
        var result = service.getPackageCallgraph(packageName, version, offset, limit, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao).getPackageCallgraph(packageName, version, offset, limit);
    }

    @Test
    void searchPackageNamesPositiveTest() {
        var packageName = "group:artifact";
        var response = "matching package versions";
        Mockito.when(kbDao.searchPackageNames(packageName, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.searchPackageNames(packageName, offset, limit);
        assertEquals(expected, result);
        Mockito.verify(kbDao).searchPackageNames(packageName, offset, limit);
    }

    @Test
    void searchPackageNamesNegativeTest() {
        var packageName = "group:artifact";
        Mockito.when(kbDao.searchPackageNames(packageName, offset, limit)).thenReturn(null);
        var result = service.searchPackageNames(packageName, offset, limit);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.verify(kbDao).searchPackageNames(packageName, offset, limit);
    }

    @Test
    void getERCGLinkPositiveTest() throws IOException {
        var packageName = "junit:junit";
        var version = "4.12";
        Mockito.when(kbDao.assertPackageExistence(packageName, version)).thenReturn(true);
        KnowledgeBaseConnector.rcgBaseUrl = "http://lima.ewi.tudelft.nl/";
        var result = service.getERCGLink(packageName, version, null, null);
        assertNotNull(result);

        Mockito.verify(kbDao, Mockito.times(1)).assertPackageExistence(packageName, version);

    }

    @Test
    void getERCGLinkIngestionTest() throws IOException {
        var packageName = "junit:junit";
        var version = "4.12";
        Mockito.when(kbDao.assertPackageExistence(packageName, version)).thenReturn(false);
        var result = service.getERCGLink(packageName, version, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).assertPackageExistence(packageName, version);
    }
}
