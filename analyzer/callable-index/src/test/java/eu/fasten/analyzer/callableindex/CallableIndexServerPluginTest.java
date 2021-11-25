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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.callableindex.GidGraph;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;
import static org.junit.jupiter.api.Assertions.*;

public class CallableIndexServerPluginTest {

    private CallableIndexServerPlugin.CallableIndexFastenPlugin callableIndexFastenPlugin;

    @BeforeEach
    public void setUp() {
        callableIndexFastenPlugin = new CallableIndexServerPlugin.CallableIndexFastenPlugin();
        //callableIndexFastenPlugin.addTopic("fasten.MetadataDBExtension.out");
    }

    @Test
    public void saveToDatabaseTest() throws IOException, RocksDBException {
        var rocksDao = Mockito.mock(RocksDao.class);
        var json = new JSONObject("{\"payload\": {}}");

        var getTestResource = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("gid_graph_test.json"));
        json.getJSONObject("payload").put("dir", getTestResource.getPath());
        JSONTokener tokener = new JSONTokener(new FileReader(getTestResource.getFile()));
        var graph = GidGraph.getGraph(new JSONObject(tokener));
        callableIndexFastenPlugin.setRocksDao(rocksDao);
        callableIndexFastenPlugin.consume(json.toString());
        Mockito.verify(rocksDao).saveToRocksDb(graph);
    }

    @Test
    public void consumeTest() throws FileNotFoundException {
        var json = new JSONObject("{\"payload\": {}}");

        var getTestResource = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("gid_graph_test.json"));
        json.getJSONObject("payload").put("dir", getTestResource.getPath());
        callableIndexFastenPlugin.consume(json.toString());
        assertNull(callableIndexFastenPlugin.getPluginError());
    }

    @Test
    public void consumeJsonErrorTest() {
        var json = new JSONObject("{\"payload\": {}}");

        var getTestResource = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("gid_graph_test_err.json"));
        json.getJSONObject("payload").put("dir", getTestResource.getPath());
        //"{\"payload\":{\"foo\":\"bar\"}}"
        callableIndexFastenPlugin.consume(json.toString());
        assertNotNull(callableIndexFastenPlugin.getPluginError());
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
