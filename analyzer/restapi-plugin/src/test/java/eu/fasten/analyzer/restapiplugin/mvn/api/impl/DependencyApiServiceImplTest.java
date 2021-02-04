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
import eu.fasten.analyzer.restapiplugin.mvn.api.DependencyApi;
import eu.fasten.core.data.metadatadb.MetadataDao;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DependencyApiServiceImplTest {

    private DependencyApiServiceImpl service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new DependencyApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
    }

    @Test
    void getPackageDependenciesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var response = "dependencies";
        Mockito.when(kbDao.getPackageDependencies(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageDependencies(packageName, version, offset, limit, null, null);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getPackageDependencies(packageName, version, offset, limit);
    }

}
