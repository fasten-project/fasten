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
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import org.jooq.DSLContext;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.sql.Timestamp;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResolutionApiServiceImplTest {

    private ResolutionApiServiceImpl service;
    private GraphMavenResolver resolver;

    @BeforeEach
    void setUp() {
        service = new ResolutionApiServiceImpl();
        resolver = Mockito.mock(GraphMavenResolver.class);
        KnowledgeBaseConnector.graphResolver = resolver;
        KnowledgeBaseConnector.dbContext = Mockito.mock(DSLContext.class);
    }

    @Test
    void resolveDependenciesTest() {
        var packageName = "group:artifact";
        var version = "version";
        var transitive = true;
        var timestamp = -1L;
        var deps = Set.of(
                new Revision(1L, "g1", "a1", "v1", new Timestamp(-1)),
                new Revision(2L, "g2", "a2", "v1", new Timestamp(-1)),
                new Revision(3L, "g3", "a3", "v1", new Timestamp(-1))
        );
        Mockito.when(resolver.resolveDependencies(packageName.split(Constants.mvnCoordinateSeparator)[0], packageName.split(Constants.mvnCoordinateSeparator)[1], version, timestamp, KnowledgeBaseConnector.dbContext, transitive)).thenReturn(deps);
        var result = service.resolveDependencies(packageName, version, transitive, timestamp);
        var jsonArray = new JSONArray();
        KnowledgeBaseConnector.limaUrl = "http://lima.ewi.tudelft.nl";
        deps.stream().peek(r -> {
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.limaUrl,
                    r.artifactId.charAt(0), r.artifactId, r.artifactId, r.groupId, r.version);
            var json = r.toJSON();
            json.put("url", url);
        }).forEach(jsonArray::put);
        var expected = new ResponseEntity<>(jsonArray.toString(), HttpStatus.OK);
        assertEquals(expected, result);
        Mockito.verify(resolver).resolveDependencies(packageName.split(Constants.mvnCoordinateSeparator)[0], packageName.split(Constants.mvnCoordinateSeparator)[1], version, timestamp, KnowledgeBaseConnector.dbContext, transitive);
    }

    @Test
    void resolveDependentsTest() {
        var packageName = "group:artifact";
        var version = "version";
        var transitive = true;
        var timestamp = -1L;
        var dependents = Set.of(
                new Revision(1L, "g1", "a1", "v1", new Timestamp(-1)),
                new Revision(2L, "g2", "a2", "v1", new Timestamp(-1)),
                new Revision(3L, "g3", "a3", "v1", new Timestamp(-1))
        );
        Mockito.when(resolver.resolveDependents(packageName.split(Constants.mvnCoordinateSeparator)[0], packageName.split(Constants.mvnCoordinateSeparator)[1], version, timestamp, transitive)).thenReturn(dependents);
        var result = service.resolveDependents(packageName, version, transitive, timestamp);
        var jsonArray = new JSONArray();
        KnowledgeBaseConnector.limaUrl = "http://lima.ewi.tudelft.nl";
        dependents.stream().peek(r -> {
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.limaUrl,
                    r.artifactId.charAt(0), r.artifactId, r.artifactId, r.groupId, r.version);
            var json = r.toJSON();
            json.put("url", url);
        }).forEach(jsonArray::put);
        var expected = new ResponseEntity<>(jsonArray.toString(), HttpStatus.OK);
        assertEquals(expected, result);
        Mockito.verify(resolver).resolveDependents(packageName.split(Constants.mvnCoordinateSeparator)[0], packageName.split(Constants.mvnCoordinateSeparator)[1], version, timestamp, transitive);
    }
}
