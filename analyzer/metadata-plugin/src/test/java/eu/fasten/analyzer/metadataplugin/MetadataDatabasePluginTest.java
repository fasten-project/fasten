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
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import eu.fasten.analyzer.metadataplugin.db.MetadataDao;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

public class MetadataDatabasePluginTest {

    private MetadataDatabasePlugin.MetadataPlugin metadataPlugin;

    @BeforeEach
    public void setUp() {
        DSLContext context = Mockito.mock(DSLContext.class);
        metadataPlugin = new MetadataDatabasePlugin.MetadataPlugin(context);
    }

    @Test
    public void consumeTest() {
        var topic = "opal_callgraphs";
        var record = new ConsumerRecord<>(topic, 0, 0L, "test", "{\"foo\":\"bar\"}");
        metadataPlugin.consume(topic, record);
        assertFalse(metadataPlugin.recordProcessSuccessful());
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
                "    \"resolvedCalls\": [\n" +
                "      [\n" +
                "        1,\n" +
                "        2\n" +
                "      ]\n" +
                "    ],\n" +
                "    \"unresolvedCalls\": [\n" +
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
                new Timestamp(json.getLong("timestamp")))).thenReturn(packageId);

        long packageVersionId = 42;
        Mockito.when(metadataDao.insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null)).thenReturn(packageVersionId);
        long fileId = 10;
        var fileMetadata = new JSONObject("{\"superInterfaces\": [],\n" +
                "      \"sourceFile\": \"file.java\",\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]}");
        Mockito.when(metadataDao.insertFile(packageVersionId, "package", null, null,
                fileMetadata)).thenReturn(fileId);

        Mockito.when(metadataDao.insertCallable(fileId, "/package/class.method()%2Fjava" +
                ".lang%2FVoid", true, null, null)).thenReturn(64L);
        Mockito.when(metadataDao.insertCallable(fileId, "/package/class.toString()%2Fjava" +
                ".lang%2FString", true, null, null)).thenReturn(65L);
        Mockito.when(metadataDao.insertEdge(64L, 65L, null)).thenReturn(1L);

        Mockito.when(metadataDao.insertCallable(null, "///dep/service.call()%2Fjava" +
                ".lang%2FObject", false, null, null)).thenReturn(100L);
        var callMetadata = new JSONObject("{\"invokevirtual\": \"1\"}");
        Mockito.when(metadataDao.insertEdge(64L, 100L, callMetadata)).thenReturn(5L);

        metadataPlugin.saveToDatabase(json, metadataDao);

        assertTrue(metadataPlugin.recordProcessSuccessful());
        assertTrue(metadataPlugin.getPluginError().isEmpty());

        Mockito.verify(metadataDao).insertPackage(json.getString("product"), "mvn", null, null,
                new Timestamp(json.getLong("timestamp")));
        Mockito.verify(metadataDao).insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null);
//        Mockito.verify(metadataDao).insertFile(packageVersionId, "package", null, null,
//                metadata);
//        Mockito.verify(metadataDao).insertCallable(fileId, "/package/class.method()%2Fjava" +
//                ".lang%2FVoid", null, null);
//        Mockito.verify(metadataDao).insertCallable(fileId, "/package/class.toString()%2Fjava" +
//                ".lang%2FString", null, null);
//        Mockito.verify(metadataDao).insertEdge(64L, 65L, null);
//        Mockito.verify(metadataDao).insertCallable(null, "///dep/service.call()%2Fjava" +
//                ".lang%2FObject", false, null, null);
//        Mockito.verify(metadataDao).insertEdge(64L, 100L, callMetadata);
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
                "    \"resolvedCalls\": [\n" +
                "      [\n" +
                "        1,\n" +
                "        2\n" +
                "      ]\n" +
                "    ],\n" +
                "    \"unresolvedCalls\": [\n" +
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

        long fileId = 10;
        var fileMetadata = new JSONObject("{\"superInterfaces\": [],\n" +
                "      \"sourceFile\": \"file.java\",\n" +
                "      \"superClasses\": [\n" +
                "        \"/java.lang/Object\"\n" +
                "      ]}");
        Mockito.when(metadataDao.insertFile(packageVersionId, "package", null, null,
                fileMetadata)).thenReturn(fileId);

        Mockito.when(metadataDao.insertCallable(fileId, "/package/class.method()%2Fjava" +
                ".lang%2FVoid", true, null, null)).thenReturn(64L);
        Mockito.when(metadataDao.insertCallable(fileId, "/package/class.toString()%2Fjava" +
                ".lang%2FString", true, null, null)).thenReturn(65L);
        Mockito.when(metadataDao.insertEdge(64L, 65L, null)).thenReturn(1L);

        Mockito.when(metadataDao.insertCallable(null, "///dep/service.call()%2Fjava" +
                ".lang%2FObject", false, null, null)).thenReturn(100L);
        var callMetadata = new JSONObject("{\"invokevirtual\": \"1\"}");
        Mockito.when(metadataDao.insertEdge(64L, 100L, callMetadata)).thenReturn(5L);

        metadataPlugin.saveToDatabase(json, metadataDao);

        assertTrue(metadataPlugin.recordProcessSuccessful());
        assertTrue(metadataPlugin.getPluginError().isEmpty());

        Mockito.verify(metadataDao).insertPackage(json.getString("product"), "mvn", null, null,
                null);
        Mockito.verify(metadataDao).insertPackageVersion(packageId, json.getString("generator"),
                json.getString("version"), null, null);
        Mockito.verify(metadataDao).insertPackage("test.dependency", "mvn", null, null, null);
        Mockito.verify(metadataDao).insertDependencies(packageId,
                Collections.singletonList(depPackageId), Collections.singletonList("[1.0.0]"));
//        Mockito.verify(metadataDao).insertFile(packageVersionId, "package", null, null,
//                metadata);
//        Mockito.verify(metadataDao).insertCallable(fileId, "/package/class.method()%2Fjava" +
//                ".lang%2FVoid", null, null);
//        Mockito.verify(metadataDao).insertCallable(fileId, "/package/class.toString()%2Fjava" +
//                ".lang%2FString", null, null);
//        Mockito.verify(metadataDao).insertEdge(64L, 65L, null);
//        Mockito.verify(metadataDao).insertCallable(null, "///dep/service.call()%2Fjava" +
//                ".lang%2FObject", false, null, null);
//        Mockito.verify(metadataDao).insertEdge(64L, 100L, callMetadata);
    }

    @Test
    public void saveToDatabaseEmptyJsonTest() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var json = new JSONObject();
        metadataPlugin.saveToDatabase(json, metadataDao);
        assertFalse(metadataPlugin.recordProcessSuccessful());
        assertFalse(metadataPlugin.getPluginError().isEmpty());
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Collections.singletonList("opal_callgraphs");
        assertEquals(topics, metadataPlugin.consumerTopics());
    }

    @Test
    public void recordProcessSuccessfulTest() {
        assertFalse(metadataPlugin.recordProcessSuccessful());
    }

    @Test
    public void nameTest() {
        var name = "Metadata plugin";
        assertEquals(name, metadataPlugin.name());
    }

    @Test
    public void descriptionTest() {
        var description = "Metadata plugin. "
                + "Consumes kafka topic and populates metadata database with consumed data.";
        assertEquals(description, metadataPlugin.description());
    }
}
