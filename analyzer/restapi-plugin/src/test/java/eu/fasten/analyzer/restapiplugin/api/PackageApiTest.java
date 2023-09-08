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
import eu.fasten.analyzer.restapiplugin.LazyIngestionProvider;
import eu.fasten.analyzer.restapiplugin.RestApplication;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PackageApiTest {

    private static final int offset = 0;
    private static final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    private PackageApi sut;
    private LazyIngestionProvider ingestion;
    private MetadataDao kbDao;

    @BeforeEach
    void setUp() {
        kbDao = Mockito.mock(MetadataDao.class);
        ingestion = mock(LazyIngestionProvider.class);
        sut = new PackageApi();
        sut.setLazyIngestionProvider(ingestion);
        KnowledgeBaseConnector.kbDao = kbDao;
        KnowledgeBaseConnector.forge = Constants.mvnForge;
    }

    @Test
    void getAllPackagesTest() {
        var response = "all packages";
        Mockito.when(kbDao.getAllPackages(offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = sut.getAllPackages(offset, limit);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getAllPackages(offset, limit);
    }

    @Test
    void getPackageLastVersionPositiveTest() {
        var packageName = "group:artifact";
        var response = "a package";
        Mockito.when(kbDao.getPackageLastVersion(packageName)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = sut.getPackageLastVersion(packageName);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getPackageLastVersion(packageName);
    }

    @Test
    void getPackageLastVersionNegativeTest() {
        var packageName = "group:artifact";
        Mockito.when(kbDao.getPackageLastVersion(packageName)).thenReturn(null);
        var result = sut.getPackageLastVersion(packageName);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).getPackageLastVersion(packageName);
    }

    @Test
    void getPackageVersionsTest() {
        var packageName = "group:artifact";
        var response = "package versions";
        Mockito.when(kbDao.getPackageVersions(packageName, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = sut.getPackageVersions(packageName, offset, limit);
        assertEquals(expected, result);
        Mockito.verify(kbDao, Mockito.times(1)).getPackageVersions(packageName, offset, limit);
    }

    @Test
    void getPackageVersionPositiveTest() {
        var packageName = "group:artifact";
        var version = "version";
        var response = "package version";
        when(kbDao.getPackageVersion(packageName, version)).thenReturn(response);
        
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = sut.getPackageVersion(packageName, version, null, null);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getPackageVersion(packageName, version);
    }

    @Test
    void getPackageVersionNeededIngestion() {
        var packageName = "junit:junit";
        var version = "4.12";
        when(kbDao.getPackageVersion(packageName, version)).thenReturn(null);
        when(ingestion.ingestArtifactIfNecessary(anyString(), anyString(), isNull(), isNull())).thenReturn(true);

        var result = sut.getPackageVersion(packageName, version, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(0)).getPackageVersion(packageName, version);
    }

    @Test
    void getPackageVersionPreingested() {
        var packageName = "junit:junit";
        var version = "4.12";
        when(ingestion.ingestArtifactIfNecessary(anyString(), anyString(), anyString(), anyLong())).thenReturn(false);
        when(kbDao.getPackageVersion(packageName, version)).thenReturn("a\\/b");

        var result = sut.getPackageVersion(packageName, version, null, null);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("a/b", result.getBody());
    }

    @Test
    void getPVMetadataPositiveTest() {
        var packageName = "group:artifact";
        var version = "version";
        var response = "package metadata";
        Mockito.when(kbDao.getPackageMetadata(packageName, version)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = sut.getPackageMetadata(packageName, version, null, null);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getPackageMetadata(packageName, version);
    }

    @Test
    void getPVMetadataNeededIngestion() {
        var packageName = "group:artifact";
        var version = "version";
        when(kbDao.getPackageVersion(packageName, version)).thenReturn(null);
        when(ingestion.ingestArtifactIfNecessary(anyString(), anyString(), isNull(), isNull())).thenReturn(true);
        var result = sut.getPackageMetadata(packageName, version, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(0)).getPackageMetadata(packageName, version);
    }

    @Test
    void getPackageCallgraphPositiveTest() throws IOException {
        var packageName = "group:artifact";
        var version = "version";
        var response = "package callgraph";
        Mockito.when(kbDao.getPackageCallgraph(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = sut.getPackageCallgraph(packageName, version, offset, limit, null, null);
        Mockito.verify(kbDao).getPackageCallgraph(packageName, version, offset, limit);
        assertEquals(expected, result);
    }

    @Test
    void getPackageCallgraphIngestionTest() throws IOException {
        var packageName = "junit:junit";
        var version = "4.12";
        Mockito.when(kbDao.getPackageCallgraph(packageName, version, offset, limit)).thenReturn(null);
        when(ingestion.ingestArtifactIfNecessary(anyString(), anyString(), isNull(), isNull())).thenReturn(true);
        var result = sut.getPackageCallgraph(packageName, version, offset, limit, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(0)).getPackageCallgraph(packageName, version, offset, limit);
    }

    @Test
    void searchPackageNamesPositiveTest() {
        var packageName = "group:artifact";
        var response = "matching package versions";
        Mockito.when(kbDao.searchPackageNames(packageName, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = sut.searchPackageNames(packageName, offset, limit);
        assertEquals(expected, result);
        Mockito.verify(kbDao).searchPackageNames(packageName, offset, limit);
    }

    @Test
    void searchPackageNamesNegativeTest() {
        var packageName = "group:artifact";
        Mockito.when(kbDao.searchPackageNames(packageName, offset, limit)).thenReturn(null);
        var result = sut.searchPackageNames(packageName, offset, limit);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.verify(kbDao).searchPackageNames(packageName, offset, limit);
    }

    @Test
    @Disabled(value = "Server is inactive")
    void getERCGLinkPositiveTest() throws IOException {
        var packageName = "junit:junit";
        var version = "4.12";
        Mockito.when(kbDao.assertPackageExistence(packageName, version)).thenReturn(true);
        KnowledgeBaseConnector.rcgBaseUrl = "http://lima.ewi.tudelft.nl/";
        var result = sut.getERCGLink(packageName, version, null, null);
        assertNotNull(result);

        Mockito.verify(kbDao, Mockito.times(1)).assertPackageExistence(packageName, version);

    }

    @Test
    void getERCGLinkIngestionTest() throws IOException {
        var packageName = "junit:junit";
        var version = "4.12";
        Mockito.when(kbDao.assertPackageExistence(packageName, version)).thenReturn(false);
        var result = sut.getERCGLink(packageName, version, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).assertPackageExistence(packageName, version);
    }
}
