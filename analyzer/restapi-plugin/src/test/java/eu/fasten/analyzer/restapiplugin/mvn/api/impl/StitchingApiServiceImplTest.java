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
import eu.fasten.core.data.metadatadb.MetadataDao;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StitchingApiServiceImplTest {

    private StitchingApiServiceImpl service;
    private MetadataDao kbDao;

    @BeforeEach
    void setUp() {
        service = new StitchingApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
        KnowledgeBaseConnector.dbContext = Mockito.mock(DSLContext.class);
    }

    @Test
    void resolveCallablesToUrisTest() {
        var gids = List.of(1L, 2L, 3L);
        var map = new HashMap<Long, String>(gids.size());
        gids.forEach(id -> map.put(id, "uri" + id));
        Mockito.when(kbDao.getFullFastenUris(gids)).thenReturn(map);
        var expected = new ResponseEntity<>(new JSONObject(map).toString(), HttpStatus.OK);
        var result = service.resolveCallablesToUris(gids);
        assertEquals(expected, result);
        Mockito.verify(kbDao, Mockito.times(1)).getFullFastenUris(gids);
    }

    @Test
    void getCallablesMetadataPositiveAllAttributesTest() {
        var uris = List.of("fasten://mvn!group:artifact$version/namespace/callable_uri");
        var map = new HashMap<String, JSONObject>(1);
        map.put(uris.get(0), new JSONObject("{\"hello\":\"world\", \"foo\":8}"));
        var allAttributes = true;
        List<String> attributes = new ArrayList<>();
        Mockito.when(kbDao.getCallablesMetadataByUri("mvn", "group:artifact", "version", List.of("/namespace/callable_uri"))).thenReturn(map);
        var expected = new ResponseEntity<>(new JSONObject(map).toString(), HttpStatus.OK);
        var result = service.getCallablesMetadata(uris, allAttributes, attributes);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getCallablesMetadataByUri("mvn", "group:artifact", "version", List.of("/namespace/callable_uri"));
    }

    @Test
    void getCallablesMetadataPositiveNotAllAttributesTest() {
        var uris = List.of("fasten://mvn!group:artifact$version/namespace/callable_uri");
        var map = new HashMap<String, JSONObject>(1);
        map.put(uris.get(0), new JSONObject("{\"hello\":\"world\", \"foo\":8}"));
        Mockito.when(kbDao.getCallablesMetadataByUri("mvn", "group:artifact", "version", List.of("/namespace/callable_uri"))).thenReturn(map);
        var allAttributes = false;
        List<String> attributes = List.of("foo");
        var result = service.getCallablesMetadata(uris, allAttributes, attributes);
        var expected = new ResponseEntity<>(new JSONObject("{\"fasten://mvn!group:artifact$version/namespace/callable_uri\":{\"foo\":8}}").toString(), HttpStatus.OK);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getCallablesMetadataByUri("mvn", "group:artifact", "version", List.of("/namespace/callable_uri"));
    }

    @Test
    void getCallablesMetadataNegativeTest() {
        var allAttributes = false;
        var attributes = List.of("foo");
        var result = service.getCallablesMetadata(List.of("invalid_uri"), allAttributes, attributes);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }
}
