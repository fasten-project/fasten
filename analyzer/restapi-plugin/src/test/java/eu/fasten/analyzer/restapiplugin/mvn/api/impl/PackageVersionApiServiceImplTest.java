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

public class PackageVersionApiServiceImplTest {

    private PackageVersionApiServiceImpl service;
    private MetadataDao kbDao;

    @BeforeEach
    void setUp() {
        service = new PackageVersionApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
    }

    @Test
    void getERCGLinkTest() {
        var coordinate = "group:artifact:version";
        var id = 42L;
        Mockito.when(kbDao.getArtifactName(id)).thenReturn(coordinate);
        KnowledgeBaseConnector.rcgBaseUrl = "http://lima.ewi.tudelft.nl/";
        var expected = new ResponseEntity<>("http://lima.ewi.tudelft.nl/mvn/a/artifact/artifact_group_version.json", HttpStatus.OK);
        var result = service.getERCGLink(id);
        assertEquals(expected, result);

        Mockito.when(kbDao.getArtifactName(id)).thenReturn(null);
        result = service.getERCGLink(id);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(2)).getArtifactName(id);
    }
}
