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

package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import eu.fasten.core.data.metadatadb.MetadataDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BinaryModuleApiServiceImplTest {

    private BinaryModuleApiServiceImpl service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new BinaryModuleApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
    }

    @Test
    void getPackageBinaryModulesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var response = "modules";
        Mockito.when(kbDao.getPackageBinaryModules(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageBinaryModules(packageName, version, offset, limit, null, null);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getPackageBinaryModules(packageName, version, offset, limit);
    }

    @Test
    void getBinaryModuleMetadataTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var module = "bin module";
        var response = "module metadata";
        Mockito.when(kbDao.getBinaryModuleMetadata(packageName, version, module)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getBinaryModuleMetadata(packageName, version, module, null, null);
        assertEquals(expected, result);

        Mockito.when(kbDao.getBinaryModuleMetadata(packageName, version, module)).thenReturn(null);
        result = service.getBinaryModuleMetadata(packageName, version, module, null, null);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(2)).getBinaryModuleMetadata(packageName, version, module);
    }

    @Test
    void getBinaryModuleFilesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var module = "bin module";
        var response = "module files";
        Mockito.when(kbDao.getBinaryModuleFiles(packageName, version, module, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getBinaryModuleFiles(packageName, version, module, offset, limit, null, null);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getBinaryModuleFiles(packageName, version, module, offset, limit);
    }
}
