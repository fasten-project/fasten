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

//    @Test TODO: Rewrite according to the new parsing
//    public void saveToDatabaseTest() {
//        var metadataDao = Mockito.mock(MetadataDao.class);
//        var json = new JSONObject("{\"product\": \"test.product\", "
//                + "\"version\": \"1.0.0\", \"timestamp\": 0, \"Generator\": \"OPAL\", \"cha\": " +
//                "{}}");
//        long packageId = 8;
//        Mockito.when(metadataDao.insertPackage(json.getString("product"), null, null,
//                new Timestamp(json.getLong("timestamp")))).thenReturn(packageId);
//        long packageVersionId = 42;
//        Mockito.when(metadataDao.insertPackageVersion(packageId, json.getString("Generator"),
//                json.getString("version"), new Timestamp(json.getLong("timestamp")), null))
//                .thenReturn(packageVersionId);
//        metadataPlugin.saveToDatabase(json, metadataDao);
//        assertTrue(metadataPlugin.recordProcessSuccessful());
//        assertTrue(metadataPlugin.getPluginError().isEmpty());
//    }

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
