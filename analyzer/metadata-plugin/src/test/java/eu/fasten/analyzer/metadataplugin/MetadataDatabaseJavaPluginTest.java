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
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.metadatadb.codegen.enums.Access;
import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class MetadataDatabaseJavaPluginTest {

    private MetadataDatabaseJavaPlugin.MetadataDBJavaExtension metadataDBExtension;

    @BeforeEach
    public void setUp() {
        var dslContext = Mockito.mock(DSLContext.class);
        metadataDBExtension = new MetadataDatabaseJavaPlugin.MetadataDBJavaExtension();
        metadataDBExtension.setTopic("fasten.OPAL.out");
        metadataDBExtension.setDBConnection(new HashMap<>(Map.of(Constants.mvnForge, dslContext)));
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
                "    \"product\": \"groupID:artifactID\",\n" +
                "    \"nodes\": 2,\n" +
                "    \"forge\": \"mvn\",\n" +
                "    \"generator\": \"OPAL\",\n" +
                "    \"version\": \"2.6\",\n" +
                "    \"cha\": {\n" +
                "        \"externalTypes\": {\n" +
                "            \"/external.package/A\": {\n" +
                "                \"access\": \"\",\n" +
                "                \"methods\": {\n" +
                "                    \"1\": {\n" +
                "                        \"metadata\": {},\n" +
                "                        \"uri\": \"/external.package/A.someMethod()%2Fjava.lang%2FObject\"\n" +
                "                    }\n" +
                "                },\n" +
                "                \"final\": false,\n" +
                "                \"superInterfaces\": [],\n" +
                "                \"sourceFile\": \"\",\n" +
                "                \"superClasses\": []\n" +
                "            }\n" +
                "        },\n" +
                "        \"internalTypes\": {\n" +
                "            \"/internal.package/B\": {\n" +
                "                \"access\": \"public\",\n" +
                "                \"methods\": {\n" +
                "                    \"2\": {\n" +
                "                        \"metadata\": {\n" +
                "                            \"access\": \"public\",\n" +
                "                            \"last\": 14,\n" +
                "                            \"defined\": true,\n" +
                "                            \"first\": 25\n" +
                "                        },\n" +
                "                        \"uri\": \"/internal.package/B.internalMethod(%2Fjava.lang%2FClass)%2Fjava.lang%2FVoidType\"\n" +
                "                    }\n" +
                "                },\n" +
                "                \"final\": false,\n" +
                "                \"superInterfaces\": [\n" +
                "                    \"/internal.package/BInterface\"\n" +
                "                ],\n" +
                "                \"sourceFile\": \"B.java\",\n" +
                "                \"superClasses\": [\n" +
                "                    \"/java.lang/Object\"\n" +
                "                ]\n" +
                "            }\n" +
                "        },\n" +
                "        \"resolvedTypes\": {}\n" +
                "    },\n" +
                "    \"call-sites\": [\n" +
                "            [\n" +
                "                \"2\",\n" +
                "                \"1\",\n" +
                "                {\"1\": {\n" +
                "                    \"receiver\": \"[/java.lang/Object]\",\n" +
                "                    \"line\": 42,\n" +
                "                    \"type\": \"invokespecial\"\n" +
                "                }}\n" +
                "            ]\n" +
                "    ],\n" +
                "    \"timestamp\": 123\n" +
                "}\n");
        var namespacesMap = new HashMap<String, Long>(2);
        namespacesMap.put("/internal.package/B", 1L);
        namespacesMap.put("/internal.package/BInterface", 3L);
        namespacesMap.put("/external.package/A", 2L);
        namespacesMap.put("/java.lang/Object", 4L);
        Mockito.when(metadataDao.insertNamespaces(Mockito.anySet())).thenReturn(namespacesMap);
        long packageId = 8;
        Mockito.when(metadataDao.insertPackage(json.getString("product"), Constants.mvnForge)).thenReturn(packageId);
        long packageVersionId = 42;
        Mockito.when(metadataDao.insertPackageVersion(Mockito.eq(packageId), Mockito.eq(json.getString("generator")),
                Mockito.eq(json.getString("version")), Mockito.eq(null), Mockito.eq(null), Mockito.eq(new Timestamp(json.getLong("timestamp") * 1000)), Mockito.any(JSONObject.class))).thenReturn(packageVersionId);
        long fileId = 4;
        Mockito.when(metadataDao.insertFile(packageVersionId, "B.java")).thenReturn(fileId);
        Mockito.when(metadataDao.insertCallablesSeparately(Mockito.anyList(), Mockito.anyInt())).thenReturn(List.of(64L, 65L));
        long internalModuleId = 17;
        Mockito.when(metadataDao.insertModule(packageVersionId, 1L, false, Access.public_, new Long[]{3L}, new Long[]{4L},
                null, null)).thenReturn(internalModuleId);
        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionJavaCallGraph(json), metadataDao);
        assertEquals(packageVersionId, id);
        Mockito.verify(metadataDao).insertPackage(json.getString("product"), Constants.mvnForge);
        Mockito.verify(metadataDao).insertPackageVersion(Mockito.eq(packageId), Mockito.eq(json.getString("generator")),
                Mockito.eq(json.getString("version")), Mockito.eq(null), Mockito.eq(null), Mockito.eq(new Timestamp(json.getLong("timestamp") * 1000)), Mockito.any(JSONObject.class));
        Mockito.verify(metadataDao).insertFile(packageVersionId, "B.java");
        Mockito.verify(metadataDao).insertCallablesSeparately(Mockito.anyList(), Mockito.anyInt());
        Mockito.verify(metadataDao).batchInsertEdges(Mockito.anyList());
    }

    @Test
    public void saveToDatabaseEmptyJsonTest() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject();
        assertThrows(JSONException.class, () -> metadataDBExtension
                .saveToDatabase(new ExtendedRevisionJavaCallGraph(json), metadataDao));
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.OPAL.out"));
        assertEquals(topics, metadataDBExtension.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.OPAL.out"));
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
