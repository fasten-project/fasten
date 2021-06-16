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
import eu.fasten.core.data.ExtendedRevisionCCallGraph;
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

public class MetadataDatabaseCPluginTest {

    private MetadataDatabaseCPlugin.MetadataDBCExtension metadataDBExtension;

    @BeforeEach
    public void setUp() {
        var dslContext = Mockito.mock(DSLContext.class);
        metadataDBExtension = new MetadataDatabaseCPlugin.MetadataDBCExtension();
        metadataDBExtension.setTopic("fasten.debian.cg");
        metadataDBExtension.setDBConnection(new HashMap<>(Map.of(Constants.debianForge, dslContext)));
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
                "    \"product\": \"foo-dev\",\n" +
                "    \"source\": \"foo\",\n" +
                "    \"architecture\": \"amd64\",\n" +
                "    \"nodes\": 2,\n" +
                "    \"forge\": \"debian\",\n" +
                "    \"generator\": \"cscout\",\n" +
                "    \"version\": \"2.6\",\n" +
                "    \"functions\": {\n" +
                "        \"internal\": {\n" +
                "            \"binaries\": {\n" +
                "                \"foo\": {\n" +
                "                    \"methods\": {\n" +
                "                        \"0\": {\n" +
                "                            \"metadata\": {\n" +
                "                                \"access\": \"public\",\n" +
                "                                \"last\": 14,\n" +
                "                                \"defined\": true,\n" +
                "                                \"first\": 25\n" +
                "                            },\n" +
                "                            \"uri\": \"/foo;C/bar()\",\n" +
                "                            \"files\": [\"util.c\"]\n" +
                "                        }\n" +
                "                    }\n" +
                "                }\n" +
                "            },\n" +
                "            \"static_functions\": {\n" +
                "                \"methods\": {\n" +
                "                }\n" +
                "            }\n" +
                "        },\n" +
                "        \"external\": {\n" +
                "            \"products\": {\n" +
                "            },\n" +
                "            \"undefined\": {\n" +
                "                 \"methods\": {\n" +
                "                     \"1\": {\n" +
                "                         \"metadata\": {\n" +
                "                             \"access\": \"public\",\n" +
                "                             \"last\": 14,\n" +
                "                             \"defined\": true,\n" +
                "                             \"first\": 25\n" +
                "                         },\n" +
                "                         \"uri\": \"///libc.so;C/fgets()\",\n" +
                "                         \"files\": [\"/usr/include/x86_64-linux-gnu/bits/stdio2.h\"]\n" +
                "                     }\n" +
                "                 }\n" +
                "            },\n" +
                "            \"static_functions\": {\n" +
                "            }\n" +
                "        }\n" +
                "    },\n" +
                "    \"graph\": {\n" +
                "        \"internalCalls\": [],\n" +
                "        \"externalCalls\": [\n" +
                "            [\n" +
                "                \"0\",\n" +
                "                \"1\",\n" +
                "                {}\n" +
                "            ]\n" +
                "        ],\n" +
                "        \"resolvedCalls\": []\n" +
                "    },\n" +
                "    \"timestamp\": 123\n" +
                "}\n");
        long packageId = 8;
        Mockito.when(metadataDao.insertPackage(json.getString("product"), Constants.debianForge)).thenReturn(packageId);
        long packageVersionId = 42;
        Mockito.when(metadataDao.insertPackageVersion(Mockito.eq(packageId), Mockito.eq(json.getString("generator")),
                Mockito.eq(json.getString("version")), Mockito.eq(null), Mockito.eq(json.getString("architecture")), Mockito.eq(new Timestamp(json.getLong("timestamp") * 1000)), Mockito.any(JSONObject.class))).thenReturn(packageVersionId);
        long fileId = 4;
        Mockito.when(metadataDao.insertFile(packageVersionId, "util.c")).thenReturn(fileId);
        Mockito.when(metadataDao.insertCallablesSeparately(Mockito.anyList(), Mockito.anyInt())).thenReturn(List.of(64L, 65L));
        long internalModuleId = 17;
        Mockito.when(metadataDao.insertModule(packageVersionId, 0, null,
                null, null, null, null, null)).thenReturn(internalModuleId);
        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionCCallGraph(json), metadataDao);
        assertEquals(packageVersionId, id);
        Mockito.verify(metadataDao).insertPackage(json.getString("product"), Constants.debianForge);
        Mockito.verify(metadataDao).insertPackageVersion(Mockito.eq(packageId), Mockito.eq(json.getString("generator")),
                Mockito.eq(json.getString("version")), Mockito.eq(null), Mockito.eq(json.getString("architecture")), Mockito.eq(new Timestamp(json.getLong("timestamp") * 1000)), Mockito.any(JSONObject.class));
        Mockito.verify(metadataDao).insertFile(packageVersionId, "util.c");
        Mockito.verify(metadataDao).insertCallablesSeparately(Mockito.anyList(), Mockito.anyInt());
        Mockito.verify(metadataDao).batchInsertEdges(Mockito.anyList());
    }

    @Test
    public void saveToDatabaseEmptyJsonTest() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject();
        assertThrows(JSONException.class, () -> metadataDBExtension
                .saveToDatabase(new ExtendedRevisionCCallGraph(json), metadataDao));
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.debian.cg"));
        assertEquals(topics, metadataDBExtension.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.debian.cg"));
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
