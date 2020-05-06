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

import java.io.IOException;
import java.util.Collections;
import eu.fasten.analyzer.graphplugin.db.RocksDao;
import eu.fasten.core.data.metadatadb.graph.Graph;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;
import static org.junit.jupiter.api.Assertions.*;

public class GraphDatabasePluginTest {

    private GraphDatabasePlugin.GraphDBExtension graphDBExtension;

    @BeforeEach
    public void setUp() {
        graphDBExtension = new GraphDatabasePlugin.GraphDBExtension();
        graphDBExtension.setTopic("fasten.cg.gid_graphs");
    }

    @Test
    public void saveToDatabaseTest() throws IOException, RocksDBException {
        var rocksDao = Mockito.mock(RocksDao.class);
        var json = new JSONObject("{" +
                "\"index\": 1," +
                "\"product\": \"test\"," +
                "\"version\": \"0.0.1\"," +
                "\"nodes\": [1, 2, 3]," +
                "\"numInternalNodes\": 2," +
                "\"edges\": [[1, 2], [2, 3]]" +
                "}");
        var graph = Graph.getGraph(json);
        graphDBExtension.saveToDatabase(graph, rocksDao);
        Mockito.verify(rocksDao).saveToRocksDb(graph.getIndex(), graph.getNodes(), graph.getNumInternalNodes(), graph.getEdges());
    }

    @Test
    public void consumeTest() {
        var json = new JSONObject("{" +
                "\"index\": 1," +
                "\"product\": \"test\"," +
                "\"version\": \"0.0.1\"," +
                "\"nodes\": [0, 1, 2]," +
                "\"numInternalNodes\": 2," +
                "\"edges\": [[0, 1], [1, 2]]" +
                "}");
        var graph = Graph.getGraph(json);
        var record = new ConsumerRecord<>("fasten.cg.gid_graphs", 1, 0L, graph.getProduct(), graph.toJSONString());
        graphDBExtension.consume("fasten.cg.gid_graphs", record);
        assertTrue(graphDBExtension.recordProcessSuccessful());
        assertTrue(graphDBExtension.getPluginError().isEmpty());
    }

    @Test
    public void consumeJsonErrorTest() {
        var topic = "fasten.cg.gid_graphs";
        var record = new ConsumerRecord<>(topic, 1, 0L, "test", "{\"foo\":\"bar\"}");
        graphDBExtension.consume(topic, record);
        assertFalse(graphDBExtension.recordProcessSuccessful());
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Collections.singletonList("fasten.cg.gid_graphs");
        assertEquals(topics, graphDBExtension.consumerTopics());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Collections.singletonList("fasten.cg.gid_graphs");
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
