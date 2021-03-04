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
import java.util.Optional;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.graphdb.GidGraph;
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
        graphDBExtension.setTopic("fasten.MetadataDBExtension.out");
    }

    @Test
    public void saveToDatabaseTest() throws IOException, RocksDBException {
        var rocksDao = Mockito.mock(RocksDao.class);
        var json = new JSONObject("{\"payload\": {" +
                "\"index\": 1," +
                "\"product\": \"test\"," +
                "\"version\": \"0.0.1\"," +
                "\"nodes\": [1, 2, 3]," +
                "\"numInternalNodes\": 2," +
                "\"edges\": [[1, 2], [2, 3]]" +
                "}}");
        var graph = GidGraph.getGraph(json.getJSONObject("payload"));
        graphDBExtension.setRocksDao(rocksDao);
        graphDBExtension.consume(json.toString());
        Mockito.verify(rocksDao).saveToRocksDb(graph);
    }

    @Test
    public void consumeTest() {
        var json ="{\"payload\": {" +
                "\"index\": 1," +
                "\"product\": \"test\"," +
                "\"version\": \"0.0.1\"," +
                "\"nodes\": [0, 1, 2]," +
                "\"numInternalNodes\": 2," +
                "\"edges\": [[0, 1], [1, 2]]" +
                "}}";
        graphDBExtension.consume(json);
        assertNull(graphDBExtension.getPluginError());
    }

    @Test
    public void consumeJsonErrorTest() {
        graphDBExtension.consume("{\"payload\":{\"foo\":\"bar\"}}");
        assertNotNull(graphDBExtension.getPluginError());
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.MetadataDBExtension.out"));
        assertEquals(topics, graphDBExtension.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.MetadataDBExtension.out"));
        assertEquals(topics1, graphDBExtension.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        graphDBExtension.setTopic(differentTopic);
        assertEquals(topics2, graphDBExtension.consumeTopic());
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
