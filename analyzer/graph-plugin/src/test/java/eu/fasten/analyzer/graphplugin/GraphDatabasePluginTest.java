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

package eu.fasten.analyzer.graphplugin;

import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import eu.fasten.analyzer.graphplugin.db.RocksDao;
import eu.fasten.core.data.metadatadb.graph.Graph;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

public class GraphDatabasePluginTest {

    private GraphDatabasePlugin.GraphDBExtension graphDBExtension;

    @BeforeEach
    public void setUp() {
        graphDBExtension = new GraphDatabasePlugin.GraphDBExtension();
        graphDBExtension.setTopic("fasten.cg.edges");
    }

    @Test
    public void saveToDatabaseTest() {
        var rocksDao = Mockito.mock(RocksDao.class);
        var json = new JSONObject("{" +
                "\"product\": \"test\"," +
                "\"version\": \"0.0.1\"," +
                "\"nodes\": [1, 2, 3]," +
                "\"numInternalNodes\": 2," +
                "\"edges\": [[1, 2], [2, 3]]" +
                "}");
        var graph = Graph.getGraph(json);
        graphDBExtension.saveToDatabase(graph, rocksDao);
        Mockito.verify(rocksDao).saveToRocksDb(graph.getNodes(), graph.getNumInternalNodes(), graph.getEdges());
    }

    @Test
    public void consumeTest() {
        var json = new JSONObject("{" +
                "\"product\": \"test\"," +
                "\"version\": \"0.0.1\"," +
                "\"nodes\": [1, 2, 3]," +
                "\"numInternalNodes\": 2," +
                "\"edges\": [[1, 2], [2, 3]]" +
                "}");
        var graph = Graph.getGraph(json);
        var record = new ConsumerRecord<>("fasten.cg.edges", 1, 0L, graph.getProduct(), graph.toJSONString());
        graphDBExtension.consume("fasten.cg.edges", record);
    }

    @Test
    public void consumeJsonErrorTest() {
        var topic = "fasten.cg.edges";
        var record = new ConsumerRecord<>(topic, 1, 0L, "test", "{\"foo\":\"bar\"}");
        graphDBExtension.consume(topic, record);
        assertFalse(graphDBExtension.recordProcessSuccessful());
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Collections.singletonList("fasten.cg.edges");
        assertEquals(topics, graphDBExtension.consumerTopics());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Collections.singletonList("fasten.cg.edges");
        assertEquals(topics1, graphDBExtension.consumerTopics());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Collections.singletonList(differentTopic);
        graphDBExtension.setTopic(differentTopic);
        assertEquals(topics2, graphDBExtension.consumerTopics());
    }

    @Test
    public void recordProcessSuccessfulTest() {
        assertFalse(graphDBExtension.recordProcessSuccessful());
    }

    @Test
    public void nameTest() {
        var name = "Graph plugin";
        assertEquals(name, graphDBExtension.name());
    }

    @Test
    public void descriptionTest() {
        var description = "Graph plugin. "
                + "Consumes list of edges (pair of global IDs produced by PostgreSQL from Kafka"
                + " topic and populates graph database (RocksDB) with consumed data";
        assertEquals(description, graphDBExtension.description());
    }
}
