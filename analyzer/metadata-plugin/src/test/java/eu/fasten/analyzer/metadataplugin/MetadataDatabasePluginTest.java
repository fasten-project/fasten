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

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import eu.fasten.analyzer.metadataplugin.db.MetadataDao;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

public class MetadataDatabasePluginTest {

    private MetadataDatabasePlugin.MetadataDBExtension metadataDBExtension;

    @BeforeEach
    public void setUp() {
        var dslContext = Mockito.mock(DSLContext.class);
        metadataDBExtension = new MetadataDatabasePlugin.MetadataDBExtension();
        metadataDBExtension.setTopic("opal_callgraphs");
        metadataDBExtension.setDBConnection(dslContext);
    }

    @Test
    public void consumeJsonErrorTest() {
        var topic = "opal_callgraphs";
        var record = new ConsumerRecord<>(topic, 0, 0L, "test", "{\"foo\":\"bar\"}");
        metadataDBExtension.consume(topic, record);
        assertFalse(metadataDBExtension.recordProcessSuccessful());
    }

    @Test
    public void saveToDatabaseTest() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject("{\n" +
                "  \"product\": \"test.product\",\n" +
                "  \"forge\": \"mvn\",\n" +
                "  \"generator\": \"OPAL\",\n" +
                "  \"depset\": [],\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"cha\": {\n" +
                "    \"/package/class\": {\n" +
                "      \"methods\": {\n" +
                "        \"1\": \"/package/class.method()%2Fjava.lang%2FVoid\",\n" +
                "        \"2\": \"/package/class.toString()%2Fjava.lang%2FString\"\n" +
                "      },\n" +
                "      \"superInterfaces\": [],\n" +
                "      \"sourceFile\": \"file.java\",\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"graph\": {\n" +
                "    \"internalCalls\": [\n" +
                "      [\n" +
                "        1,\n" +
                "        2\n" +
                "      ]\n" +
                "    ],\n" +
                "    \"externalCalls\": [\n" +
                "      [\n" +
                "        \"1\",\n" +
                "        \"///dep/service.call()%2Fjava.lang%2FObject\",\n" +
                "        {\n" +
                "          \"invokevirtual\": \"1\"\n" +
                "        }\n" +
                "      ]\n" +
                "    ]\n" +
                "  },\n" +
                "  \"timestamp\": 123\n" +
                "}");
        long packageId = 8;
        Mockito.when(metadataDao.insertPackage(json.getString("product"), "mvn", null, null,
                null)).thenReturn(packageId);
        long packageVersionId = 42;
        Mockito.when(metadataDao.insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), new Timestamp(json.getLong("timestamp")), null)).thenReturn(packageVersionId);
        long moduleId = 10;
        var moduleMetadata = new JSONObject("{\"superInterfaces\": [],\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]}");
        Mockito.when(metadataDao.insertModule(packageVersionId, "package", null,
                moduleMetadata)).thenReturn(moduleId);
        long fileId = 3;
        Mockito.when(metadataDao.insertFile(packageVersionId, "file.java", null, null, null)).thenReturn(fileId);
        Mockito.when(metadataDao.batchInsertCallables(Mockito.anyList())).thenReturn(List.of(64L, 65L, 100L));
        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionCallGraph(json), metadataDao);
        assertEquals(packageId, id);

        Mockito.verify(metadataDao).insertPackage(json.getString("product"), "mvn", null, null, null);
        Mockito.verify(metadataDao).insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), new Timestamp(json.getLong("timestamp") * 1000), null);
    }

    @Test
    public void saveToDatabaseTest2() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject("{\n" +
                "  \"product\": \"test.product\",\n" +
                "  \"forge\": \"mvn\",\n" +
                "  \"generator\": \"OPAL\",\n" +
                "  \"depset\": [" +
                "       [\n" +
                "           {\n" +
                "               \"product\": \"test.dependency\",\n" +
                "               \"forge\": \"mvn\",\n" +
                "               \"constraints\": [\n" +
                "                 \"[1.0.0]\"\n" +
                "               ]\n" +
                "           }" +
                "       ]\n" +
                "],\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"cha\": {\n" +
                "    \"/package/class\": {\n" +
                "      \"methods\": {\n" +
                "        \"1\": \"/package/class.method()%2Fjava.lang%2FVoid\",\n" +
                "        \"2\": \"/package/class.toString()%2Fjava.lang%2FString\"\n" +
                "      },\n" +
                "      \"superInterfaces\": [],\n" +
                "      \"sourceFile\": \"file.java\",\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"graph\": {\n" +
                "    \"internalCalls\": [\n" +
                "      [\n" +
                "        1,\n" +
                "        2\n" +
                "      ]\n" +
                "    ],\n" +
                "    \"externalCalls\": [\n" +
                "      [\n" +
                "        \"1\",\n" +
                "        \"///dep/service.call()%2Fjava.lang%2FObject\",\n" +
                "        {\n" +
                "          \"invokevirtual\": \"1\"\n" +
                "        }\n" +
                "      ]\n" +
                "    ]\n" +
                "  },\n" +
                "}");
        long packageId = 8;
        Mockito.when(metadataDao.insertPackage(json.getString("product"), "mvn", null, null,
                null)).thenReturn(packageId);

        long packageVersionId = 42;
        Mockito.when(metadataDao.insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null))
                .thenReturn(packageVersionId);

        long depPackageId = 128;
        Mockito.when(metadataDao.insertPackage("test.dependency", "mvn", null, null, null))
                .thenReturn(depPackageId);

        long moduleId = 10;
        var moduleMetadata = new JSONObject("{\"superInterfaces\": [],\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]}");
        Mockito.when(metadataDao.insertModule(packageVersionId, "package", null,
                moduleMetadata)).thenReturn(moduleId);
        long fileId = 3;
        Mockito.when(metadataDao.insertFile(packageVersionId, "file.java", null, null, null)).thenReturn(fileId);
        Mockito.when(metadataDao.batchInsertCallables(Mockito.anyList())).thenReturn(List.of(64L, 65L, 100L));
        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionCallGraph(json), metadataDao);
        assertEquals(packageId, id);

        Mockito.verify(metadataDao).insertPackage(json.getString("product"), "mvn", null, null,
                null);
        Mockito.verify(metadataDao).insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null);
        Mockito.verify(metadataDao).insertPackage("test.dependency", "mvn", null, null, null);
    }

    @Test
    public void saveToDatabaseTest3() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject("{\n" +
                "  \"product\": \"test.product\",\n" +
                "  \"forge\": \"mvn\",\n" +
                "  \"generator\": \"OPAL\",\n" +
                "  \"depset\": [" +
                "       [\n" +
                "           {\n" +
                "               \"product\": \"test.dependency\",\n" +
                "               \"forge\": \"mvn\",\n" +
                "               \"constraints\": [\n" +
                "                 \"[1.0.0]\"\n" +
                "               ]\n" +
                "           }" +
                "       ]\n" +
                "],\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"cha\": {\n" +
                "    \"/package/class\": {\n" +
                "      \"methods\": {\n" +
                "        \"1\": \"/package/class.method()%2Fjava.lang%2FVoid\",\n" +
                "        \"2\": \"/package/class.toString()%2Fjava.lang%2FString\"\n" +
                "      },\n" +
                "      \"superInterfaces\": [],\n" +
                "      \"sourceFile\": \"file.java\",\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"graph\": {\n" +
                "    \"internalCalls\": [\n" +
                "      [\n" +
                "        1,\n" +
                "        2\n" +
                "      ]\n" +
                "    ],\n" +
                "    \"externalCalls\": [\n" +
                "      [\n" +
                "        \"1\",\n" +
                "        \"///dep/service.call()%2Fjava.lang%2FObject\",\n" +
                "        {\n" +
                "          \"invokevirtual\": \"1\"\n" +
                "        }\n" +
                "      ]\n" +
                "    ]\n" +
                "  },\n" +
                "}");
        long packageId = 8;
        Mockito.when(metadataDao.insertPackage(json.getString("product"), "mvn", null, null,
                null)).thenReturn(packageId);

        long packageVersionId = 42;
        Mockito.when(metadataDao.insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null))
                .thenReturn(packageVersionId);

        long depPackageId = 128;
        Mockito.when(metadataDao.insertPackage("test.dependency", "mvn", null, null, null)).thenReturn(depPackageId);

        long moduleId = 10;
        var moduleMetadata = new JSONObject("{\"superInterfaces\": [],\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]}");
        Mockito.when(metadataDao.insertModule(packageVersionId, "package", null,
                moduleMetadata)).thenReturn(moduleId);
        long fileId = 3;
        Mockito.when(metadataDao.insertFile(packageVersionId, "file.java", null, null, null)).thenReturn(fileId);
        Mockito.when(metadataDao.batchInsertCallables(Mockito.anyList())).thenReturn(List.of(64L, 65L, 100L));
        metadataDBExtension.setPluginError(new RuntimeException());
        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionCallGraph(json), metadataDao);
        assertEquals(packageId, id);

        Mockito.verify(metadataDao).insertPackage(json.getString("product"), "mvn", null, null,
                null);
        Mockito.verify(metadataDao).insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null);
    }

    @Test
    public void saveToDatabaseEmptyJsonTest() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject();
        assertThrows(JSONException.class, () -> metadataDBExtension
                .saveToDatabase(new ExtendedRevisionCallGraph(json), metadataDao));
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Collections.singletonList("opal_callgraphs");
        assertEquals(topics, metadataDBExtension.consumerTopics());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Collections.singletonList("opal_callgraphs");
        assertEquals(topics1, metadataDBExtension.consumerTopics());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Collections.singletonList(differentTopic);
        metadataDBExtension.setTopic(differentTopic);
        assertEquals(topics2, metadataDBExtension.consumerTopics());
    }

    @Test
    public void recordProcessSuccessfulTest() {
        assertFalse(metadataDBExtension.recordProcessSuccessful());
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
                + " and writes edges of callgraph with Global IDs to another Kafka topic.";
        assertEquals(description, metadataDBExtension.description());
    }
}
