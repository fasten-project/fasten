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

package eu.fasten.analyzer.metadataplugin;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.ExtendedRevisionPythonCallGraph;
import eu.fasten.core.data.metadatadb.MetadataDao;
import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

public class MetadataDatabasePythonPluginTest {

    private MetadataDatabasePythonPlugin.MetadataDBPythonExtension metadataDBExtension;

    @BeforeEach
    public void setUp() {
        var dslContext = Mockito.mock(DSLContext.class);
        metadataDBExtension = new MetadataDatabasePythonPlugin.MetadataDBPythonExtension();
        metadataDBExtension.setTopic("fasten.pypi.cg");
        metadataDBExtension.setDBConnection(new HashMap<>(Map.of(Constants.pypiForge, dslContext)));
    }

    @Test
    public void consumeJsonErrorTest() {
        metadataDBExtension.consume("{\"payload\":{\"foo\":\"bar\"}}");
        assertNotNull(metadataDBExtension.getPluginError());
    }

   @Test
    public void saveToDatabaseTest() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject("{\n" +
           "     \"product\": \"foo\",\n" +
           "     \"forge\": \"PyPI\",\n" +
           "     \"nodes\": 3,\n" +
           "     \"generator\": \"PyCG\",\n" +
           "     \"depset\": [],\n" +
           "     \"version\": \"3.10.0.7\",\n" +
           "     \"modules\": {\n" +
           "         \"internal\": {\n" +
           "             \"/module.name/\": {\n" +
           "                 \"sourceFile\": \"module/name.py\",\n" +
           "                 \"namespaces\": {\n" +
           "                     \"0\": {\n" +
           "                         \"namespace\": \"/module.name/\",\n" +
           "                         \"metadata\": {\n" +
           "                             \"first\": 1\n" +
           "                         }\n" +
           "                     },\n" +
           "                     \"1\": {\n" +
           "                         \"namespace\": \"/module.name/Cls\",\n" +
           "                         \"metadata\": {\n" +
           "                             \"first\": 4,\n" +
           "                             \"superClasses\": [\"/other.module/Parent\", \"//external//external.package.SomeClass\"]\n" +
           "                         }\n" +
           "                     },\n" +
           "                 }\n" +
           "             }\n" +
           "         },\n" +
           "         \"external\": {\n" +
           "             \"external\": {\n" +
           "                 \"sourceFile\": \"\",\n" +
           "                 \"namespaces\": {\n" +
           "                     \"2\": {\n" +
           "                         \"namespace\": \"//external//external.package.method\",\n" +
           "                         \"metadata\": {}\n" +
           "                     }\n" +
           "                 }\n" +
           "             }\n" +
           "         }\n" +
           "     },\n" +
           "     \"graph\": {\n" +
           "         \"internalCalls\": [\n" +
           "             [\"0\", \"1\"],\n" +
           "         ],\n" +
           "         \"externalCalls\": [\n" +
           "             [\"1\", \"2\"]\n" +
           "         ],\n" +
           "         \"resolvedCalls\": []\n" +
           "     },\n" +
           "     \"timestamp\": 123\n" +
           " }\n");
        long packageId = 8;
       var namespacesMap = new HashMap<String, Long>(2);
       namespacesMap.put("/module.name/", 1L);
       namespacesMap.put("//external//", 2L);
       Mockito.when(metadataDao.insertNamespaces(Mockito.anySet())).thenReturn(namespacesMap);
        Mockito.when(metadataDao.insertPackage(json.getString("product"), Constants.pypiForge)).thenReturn(packageId);
        long packageVersionId = 42;
        Mockito.when(metadataDao.insertPackageVersion(Mockito.eq(packageId), Mockito.eq(json.getString("generator")),
                Mockito.eq(json.getString("version")), Mockito.eq(null), Mockito.eq(null), Mockito.eq(new Timestamp(json.getLong("timestamp") * 1000)), Mockito.any(JSONObject.class))).thenReturn(packageVersionId);
        long fileId = 4;
        Mockito.when(metadataDao.insertFile(packageVersionId, "module/name.py")).thenReturn(fileId);
        Mockito.when(metadataDao.insertCallablesSeparately(Mockito.anyList(), Mockito.anyInt())).thenReturn(List.of(64L, 65L, 66L));
        long internalModuleId = 17;
        Mockito.when(metadataDao.insertModule(Mockito.eq(packageVersionId), Mockito.eq(1L), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null),
                Mockito.eq(null), Mockito.eq(null))).thenReturn(internalModuleId);

        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionPythonCallGraph(json), metadataDao);
        assertEquals(packageVersionId, id);
        Mockito.verify(metadataDao).insertPackage(json.getString("product"), Constants.pypiForge);
        Mockito.verify(metadataDao).insertPackageVersion(Mockito.eq(packageId), Mockito.eq(json.getString("generator")),
                Mockito.eq(json.getString("version")), Mockito.eq(null), Mockito.eq(null), Mockito.eq(new Timestamp(json.getLong("timestamp") * 1000)), Mockito.any(JSONObject.class));
        Mockito.verify(metadataDao).insertFile(packageVersionId, "module/name.py");
        Mockito.verify(metadataDao).insertCallablesSeparately(Mockito.anyList(), Mockito.anyInt());
        Mockito.verify(metadataDao).batchInsertEdges(Mockito.anyList());
        Mockito.verify(metadataDao).insertModule(Mockito.eq(packageVersionId), Mockito.eq(1L), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null));
    }

    @Test
    public void saveToDatabaseEmptyJsonTest() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject();
        assertThrows(JSONException.class, () -> metadataDBExtension
                .saveToDatabase(new ExtendedRevisionPythonCallGraph(json), metadataDao));
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.pypi.cg"));
        assertEquals(topics, metadataDBExtension.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.pypi.cg"));
        assertEquals(topics1, metadataDBExtension.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        metadataDBExtension.setTopic(differentTopic);
        assertEquals(topics2, metadataDBExtension.consumeTopic());
    }

    @Test
    public void nameTest() {
        var name = "Metadata plugin";
        assertEquals(name, metadataDBExtension.name());
    }

    @Test
    public void descriptionTest() {
        var description = "Metadata plugin. "
                + "Consumes ExtendedRevisionCallgraph-formatted JSON objects from Kafka topic"
                + " and populates metadata database with consumed data"
                + " and writes graph of GIDs of callgraph to another Kafka topic.";
        assertEquals(description, metadataDBExtension.description());
    }
}
