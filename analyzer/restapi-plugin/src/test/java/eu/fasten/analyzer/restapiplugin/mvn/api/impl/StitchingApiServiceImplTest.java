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
import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import org.jooq.DSLContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StitchingApiServiceImplTest {

    private StitchingApiServiceImpl service;
    private MetadataDao kbDao;
    private GraphMavenResolver resolver;
    private RocksDao graphDao;

    @BeforeEach
    void setUp() {
        service = new StitchingApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        resolver = Mockito.mock(GraphMavenResolver.class);
        graphDao = Mockito.mock(RocksDao.class);
        KnowledgeBaseConnector.graphDao = graphDao;
        KnowledgeBaseConnector.graphResolver = resolver;
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
        Mockito.verify(kbDao).getFullFastenUris(gids);
    }

    @Test
    void getCallablesMetadataTest() {
        var uris = List.of("fasten://mvn!group:artifact$version/namespace/callable_uri");
        var map = new HashMap<String, JSONObject>(1);
        map.put(uris.get(0), new JSONObject("{\"hello\":\"world\", \"foo\":8}"));
        var allAttributes = true;
        List<String> attributes = new ArrayList<>();
        Mockito.when(kbDao.getCallablesMetadataByUri("mvn", "group:artifact", "version", List.of("/namespace/callable_uri"))).thenReturn(map);
        var expected = new ResponseEntity<>(new JSONObject(map).toString(), HttpStatus.OK);
        var result = service.getCallablesMetadata(uris, allAttributes, attributes);
        assertEquals(expected, result);

        allAttributes = false;
        attributes = List.of("foo");
        result = service.getCallablesMetadata(uris, allAttributes, attributes);
        expected = new ResponseEntity<>(new JSONObject("{\"fasten://mvn!group:artifact$version/namespace/callable_uri\":{\"foo\":8}}").toString(), HttpStatus.OK);
        assertEquals(expected, result);

        result = service.getCallablesMetadata(List.of("invalid_uri"), allAttributes, attributes);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(2)).getCallablesMetadataByUri("mvn", "group:artifact", "version", List.of("/namespace/callable_uri"));
    }

    @Test
    void resolveMultipleDependenciesTest() {
        var coordinates = List.of("group:artifact:version");
        var revisions = Set.of(new Revision(1, "group", "artifact", "version", new Timestamp(-1)));
        var id = 42L;
        Mockito.when(kbDao.getPackageVersionID("group:artifact", "version")).thenReturn(id);
        var virtualNode = new Revision(0, "pg", "pa", "pv", new Timestamp(-1));
        Mockito.when(resolver.addVirtualNode(revisions)).thenReturn(virtualNode);
        var deps = Set.of(new Revision(2, "depgroup", "departifact", "depversion", new Timestamp(-1)));
        Mockito.when(resolver.resolveDependencies(virtualNode, KnowledgeBaseConnector.dbContext, true)).thenReturn(deps);
        var jsonArray = new JSONArray();
        KnowledgeBaseConnector.rcgBaseUrl = "http://lima.ewi.tudelft.nl";
        deps.stream().map(r -> {
            var json = new JSONObject();
            var url = String.format("%s/mvn/%s/%s/%s_%s_%s.json", KnowledgeBaseConnector.rcgBaseUrl,
                    r.artifactId.charAt(0), r.artifactId, r.artifactId, r.groupId, r.version);
            json.put(String.valueOf(r.id), url);
            return json;
        }).forEach(jsonArray::put);
        var result = service.resolveMultipleDependencies(coordinates);
        var expected = new ResponseEntity<>(jsonArray.toString(), HttpStatus.OK);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getPackageVersionID("group:artifact", "version");
        Mockito.verify(resolver).addVirtualNode(revisions);
        Mockito.verify(resolver).resolveDependencies(virtualNode, KnowledgeBaseConnector.dbContext, true);
        Mockito.verify(resolver).removeVirtualNode(virtualNode);
    }

    @Test
    void getDirectedGraphTest() throws RocksDBException {
        var id = 42L;
        var needStitching = false;
        var timestamp = -1L;
        var graph = new ArrayImmutableDirectedGraph.Builder().build();
        Mockito.when(graphDao.getGraphData(id)).thenReturn(graph);
        var json = new JSONObject();
        var nodesJson = new JSONArray();
        graph.nodes().stream().forEach(nodesJson::put);
        var edgesJson = new JSONArray();
        graph.edgeSet().stream().map(e -> new long[]{e.firstLong(), e.secondLong()}).forEach(edgesJson::put);
        json.put("nodes", nodesJson);
        json.put("edges", edgesJson);
        var expected = new ResponseEntity<>(json.toString(), HttpStatus.OK);
        var result = service.getDirectedGraph(id, needStitching, timestamp);
        assertEquals(expected, result);

        Mockito.when(graphDao.getGraphData(id)).thenReturn(null);
        result = service.getDirectedGraph(id, needStitching, timestamp);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.when(graphDao.getGraphData(id)).thenThrow(RocksDBException.class);
        result = service.getDirectedGraph(id, needStitching, timestamp);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

        Mockito.verify(graphDao, Mockito.times(3)).getGraphData(id);
    }

    @Test
    void getPathsToVulnerableNodeDFSTest() {
        var builder = new ArrayImmutableDirectedGraph.Builder();
        builder.addInternalNode(0);
        builder.addInternalNode(1);
        builder.addInternalNode(2);
        builder.addInternalNode(3);
        builder.addInternalNode(4);
        builder.addInternalNode(5);
        builder.addInternalNode(6);
        builder.addInternalNode(7);
        builder.addArc(0, 1);
        builder.addArc(0, 3);
        builder.addArc(1, 2);
        builder.addArc(1, 4);
        builder.addArc(3, 5);
        builder.addArc(2, 7);
        builder.addArc(4, 7);
        builder.addArc(5, 7);
        builder.addArc(5, 6);
        var graph = builder.build();
        assertEquals(List.of(1L, 3L), graph.successors(0L));
        assertEquals(List.of(2L, 4L), graph.successors(1L));
        assertEquals(List.of(5L), graph.successors(3L));
        assertEquals(List.of(7L), graph.successors(2L));
        assertEquals(List.of(7L), graph.successors(4L));
        assertEquals(List.of(6L, 7L), graph.successors(5L));
        var expected = List.of(
                List.of(0L, 1L, 2L, 7L),
                List.of(0L, 1L, 4L, 7L),
                List.of(0L, 3L, 5L, 7L)
        );
        var actual = service.getPathsToVulnerableNode(graph, 0L, 7L, new HashSet<>(), new LinkedList<>(), new ArrayList<>());
        assertEquals(expected, actual);
    }
}
