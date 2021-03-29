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
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallableApiServiceImplTest {

    private CallableApiServiceImpl service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new CallableApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
    }

    @Test
    void getPackageCallablesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var response = "callables";
        Mockito.when(kbDao.getPackageCallables(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageCallables(packageName, version, offset, limit, null, null);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getPackageCallables(packageName, version, offset, limit);
    }

    @Test
    void getCallableMetadataTest() {
        var packageName = "group:artifact";
        var version = "version";
        var callable = "callable uri";
        var response = "callable metadata";
        Mockito.when(kbDao.getCallableMetadata(packageName, version, callable)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getCallableMetadata(packageName, version, callable, null, null);
        assertEquals(expected, result);

        Mockito.when(kbDao.getCallableMetadata(packageName, version, callable)).thenReturn(null);
        result = service.getCallableMetadata(packageName, version, callable, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(2)).getCallableMetadata(packageName, version, callable);

        packageName = "junit:junit";
        version = "4.12";
        Mockito.when(kbDao.getCallableMetadata(packageName, version, callable)).thenReturn(null);
        result = service.getCallableMetadata(packageName, version, callable, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).getCallableMetadata(packageName, version, callable);
    }

    @Test
    void getCallablesTest() {
        var ids = List.of(1L, 2L, 3L);
        var map = new HashMap<Long, JSONObject>(3);
        map.put(1L, new JSONObject("{\"foo\":\"bar\"}"));
        map.put(2L, new JSONObject("{\"hello\":\"world\"}"));
        map.put(3L, new JSONObject("{\"baz\":42}"));
        Mockito.when(kbDao.getCallables(ids)).thenReturn(map);
        var json = new JSONObject();
        for (var id : ids) {
            json.put(String.valueOf(id), map.get(id));
        }
        var expected = new ResponseEntity<>(json.toString(), HttpStatus.OK);
        var result = service.getCallables(ids);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getCallables(ids);
    }
}
