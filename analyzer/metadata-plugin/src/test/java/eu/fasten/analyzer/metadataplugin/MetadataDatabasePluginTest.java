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

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import eu.fasten.analyzer.metadataplugin.db.MetadataDao;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

public class MetadataDatabasePluginTest {

    private MetadataDatabasePlugin.MetadataDBExtension metadataDBExtension;

    @BeforeEach
    public void setUp() throws SQLException {
        metadataDBExtension = new MetadataDatabasePlugin.MetadataDBExtension();
        metadataDBExtension.setTopic("opal_callgraphs");
        metadataDBExtension.getDBAccess("jdbc:postgresql:postgres", "postgres", "pass123");
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
                "      \"sourceFile\": \"file.java\",\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]}");
        Mockito.when(metadataDao.insertModule(packageVersionId, "package", null, null,
                moduleMetadata)).thenReturn(moduleId);

        Mockito.when(metadataDao.insertCallable(moduleId, "/package/class.method()%2Fjava" +
                ".lang%2FVoid", true, null, null)).thenReturn(64L);
        Mockito.when(metadataDao.insertCallable(moduleId, "/package/class.toString()%2Fjava" +
                ".lang%2FString", true, null, null)).thenReturn(65L);
        Mockito.when(metadataDao.insertEdge(64L, 65L, null)).thenReturn(1L);

        Mockito.when(metadataDao.insertCallable(null, "///dep/service.call()%2Fjava" +
                ".lang%2FObject", false, null, null)).thenReturn(100L);
        var callMetadata = new JSONObject("{\"invokevirtual\": \"1\"}");
        Mockito.when(metadataDao.insertEdge(64L, 100L, callMetadata)).thenReturn(5L);

        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionCallGraph(json), metadataDao);
        assertEquals(packageId, id);

        Mockito.verify(metadataDao).insertPackage(json.getString("product"), "mvn", null, null, null);
        Mockito.verify(metadataDao).insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), new Timestamp(json.getLong("timestamp")), null);
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

        Mockito.when(metadataDao.getPackageIdByNameAndForge("test.dependency", "mvn")).thenReturn(-1L);
        long depPackageId = 128;
        Mockito.when(metadataDao.insertPackage("test.dependency", "mvn", null, null, null))
                .thenReturn(depPackageId);

        long moduleId = 10;
        var moduleMetadata = new JSONObject("{\"superInterfaces\": [],\n" +
                "      \"sourceFile\": \"file.java\",\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]}");
        Mockito.when(metadataDao.insertModule(packageVersionId, "package", null, null,
                moduleMetadata)).thenReturn(moduleId);

        Mockito.when(metadataDao.insertCallable(moduleId, "/package/class.method()%2Fjava" +
                ".lang%2FVoid", true, null, null)).thenReturn(64L);
        Mockito.when(metadataDao.insertCallable(moduleId, "/package/class.toString()%2Fjava" +
                ".lang%2FString", true, null, null)).thenReturn(65L);
        Mockito.when(metadataDao.insertEdge(64L, 65L, null)).thenReturn(1L);

        Mockito.when(metadataDao.insertCallable(null, "///dep/service.call()%2Fjava" +
                ".lang%2FObject", false, null, null)).thenReturn(100L);
        var callMetadata = new JSONObject("{\"invokevirtual\": \"1\"}");
        Mockito.when(metadataDao.insertEdge(64L, 100L, callMetadata)).thenReturn(5L);

        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionCallGraph(json), metadataDao);
        assertEquals(packageId, id);

        Mockito.verify(metadataDao).insertPackage(json.getString("product"), "mvn", null, null,
                null);
        Mockito.verify(metadataDao).insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null);
        Mockito.verify(metadataDao).getPackageIdByNameAndForge("test.dependency", "mvn");
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
        Mockito.when(metadataDao.getPackageIdByNameAndForge("test.dependency", "mvn")).thenReturn(depPackageId);

        long moduleId = 10;
        var moduleMetadata = new JSONObject("{\"superInterfaces\": [],\n" +
                "      \"sourceFile\": \"file.java\",\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]}");
        Mockito.when(metadataDao.insertModule(packageVersionId, "package", null, null,
                moduleMetadata)).thenReturn(moduleId);

        Mockito.when(metadataDao.insertCallable(moduleId, "/package/class.method()%2Fjava" +
                ".lang%2FVoid", true, null, null)).thenReturn(64L);
        Mockito.when(metadataDao.insertCallable(moduleId, "/package/class.toString()%2Fjava" +
                ".lang%2FString", true, null, null)).thenReturn(65L);
        Mockito.when(metadataDao.insertEdge(64L, 65L, null)).thenReturn(1L);

        Mockito.when(metadataDao.insertCallable(null, "///dep/service.call()%2Fjava" +
                ".lang%2FObject", false, null, null)).thenReturn(100L);
        var callMetadata = new JSONObject("{\"invokevirtual\": \"1\"}");
        Mockito.when(metadataDao.insertEdge(64L, 100L, callMetadata)).thenReturn(5L);

        metadataDBExtension.setPluginError(new RuntimeException());
        long id = metadataDBExtension.saveToDatabase(new ExtendedRevisionCallGraph(json), metadataDao);
        assertEquals(packageId, id);

        Mockito.verify(metadataDao).insertPackage(json.getString("product"), "mvn", null, null,
                null);
        Mockito.verify(metadataDao).insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null);
        Mockito.verify(metadataDao).getPackageIdByNameAndForge("test.dependency", "mvn");
        Mockito.verify(metadataDao, Mockito.never()).insertPackage("test.dependency", "mvn", null, null,
                null);
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
                + " and populates metadata database with consumed data.";
        assertEquals(description, metadataDBExtension.description());
    }
}
