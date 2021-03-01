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
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StitchingApiTest {

    private StitchingApiService service;
    private StitchingApi api;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(StitchingApiService.class);
        api = new StitchingApi(service);
    }

    @Test
    void resolveCallablesTest() {
        var gids = List.of(1L, 2L, 3L);
        var response = new ResponseEntity<>("callable uri map", HttpStatus.OK);
        Mockito.when(service.resolveCallablesToUris(gids)).thenReturn(response);
        var result = api.resolveCallables(gids);
        assertEquals(response, result);
        Mockito.verify(service).resolveCallablesToUris(gids);
    }

    @Test
    void getCallablesMetadataTest() {
        var uris = List.of("uri1", "uri2", "uri3");
        var response = new ResponseEntity<>("callables metadata map", HttpStatus.OK);
        Mockito.when(service.getCallablesMetadata(uris, true, null)).thenReturn(response);
        var result = api.getCallablesMetadata(uris, true, null);
        assertEquals(response, result);
        Mockito.verify(service).getCallablesMetadata(uris, true, null);
    }

}
