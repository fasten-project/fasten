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

package eu.fasten.analyzer.callableindex;

import eu.fasten.core.data.callableindex.ExtendedGidGraph;
import eu.fasten.core.data.callableindex.RocksDao;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static eu.fasten.core.utils.TestUtils.getTestResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CallableIndexServerPluginTest {

    private CallableIndexServerPlugin.CallableIndexFastenPlugin callableIndexFastenPlugin;
    private final RocksDao rocksDao = Mockito.mock(RocksDao.class);

    @BeforeEach
    public void setUp() {
        callableIndexFastenPlugin = new CallableIndexServerPlugin.CallableIndexFastenPlugin();
        //callableIndexFastenPlugin.addTopic("fasten.MetadataDBExtension.out");
        callableIndexFastenPlugin.setRocksDao(rocksDao);
    }

    @Test
    public void saveToDatabaseTest() throws IOException, RocksDBException {
        var json = new JSONObject("{\"payload\": {}}");
        var jsonFile = getTestResource("gid_graph_test.json");
        json.getJSONObject("payload").put("dir", jsonFile.getPath());
        JSONTokener tokener = new JSONTokener(new FileReader(jsonFile));
        var graph = ExtendedGidGraph.getGraph(new JSONObject(tokener));
        callableIndexFastenPlugin.setRocksDao(rocksDao);
        callableIndexFastenPlugin.consume(json.toString());
        Mockito.verify(rocksDao).saveToRocksDb(graph);
    }

    @Test
    public void consumeTest() {
        var json = new JSONObject("{\"payload\": {}}");
        var jsonFile = getTestResource("gid_graph_test.json");
        json.getJSONObject("payload").put("dir", jsonFile.getPath());
        callableIndexFastenPlugin.consume(json.toString());
    }

    @Test
    public void consumeJsonErrorTest() {
        assertThrows(JSONException.class, () -> {
            var json = new JSONObject("{\"payload\": {}}");
            var jsonFile = getTestResource("gid_graph_test_err.json");
            json.getJSONObject("payload").put("dir", jsonFile.getPath());
            callableIndexFastenPlugin.consume(json.toString());
        });
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(List.of("fasten.MetadataDBJavaExtension.priority.out","fasten" +
            ".MetadataDBExtension.out"));
        assertEquals(topics, callableIndexFastenPlugin.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(List.of("fasten.MetadataDBJavaExtension.priority.out","fasten.MetadataDBExtension.out"));
        assertEquals(topics1, callableIndexFastenPlugin.consumeTopic());
        var differentTopic = Collections.singletonList("DifferentKafkaTopic");
        callableIndexFastenPlugin.setTopics(differentTopic);
        assertEquals(Optional.of(differentTopic), callableIndexFastenPlugin.consumeTopic());
    }

    @Test
    public void nameTest() {
        var name = "Graph plugin";
        assertEquals(name, callableIndexFastenPlugin.name());
    }

    @Test
    public void descriptionTest() {
        var description = "Callable index plugin. "
            + "Consumes list of edges (pair of global IDs produced by PostgreSQL from Kafka"
            + " topic and populates graph database (RocksDB) with consumed data";
        assertEquals(description, callableIndexFastenPlugin.description());
    }
}
