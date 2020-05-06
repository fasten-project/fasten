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

package eu.fasten.analyzer.graphplugin.db;

import eu.fasten.core.data.graphdb.GidGraph;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDBException;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class RocksDaoTest {

    private RocksDao rocksDao;

    @BeforeEach
    public void setUp() throws RocksDBException {
        rocksDao = new RocksDao("graphDB");
    }

    @AfterEach
    public void teardown() {
        rocksDao.close();
    }

    @Test
    public void databaseTest() throws IOException, RocksDBException {
        var json = new JSONObject("{" +
                "\"index\": 1," +
                "\"product\": \"test\"," +
                "\"version\": \"0.0.1\"," +
                "\"nodes\": [0, 1, 2]," +
                "\"numInternalNodes\": 2," +
                "\"edges\": [[0, 1], [1, 2]]" +
                "}");
        var graph = GidGraph.getGraph(json);
        rocksDao.saveToRocksDb(graph.getIndex(), graph.getNodes(), graph.getNumInternalNodes(), graph.getEdges());
        var graphData = rocksDao.getGraphData(graph.getIndex(), graph.getNumInternalNodes());
        assertEquals(graph.getNodes().size(), graphData.nodes().size());
        assertEquals(new LongArrayList(graph.getNodes()), graphData.nodes());
        assertEquals(new LongArrayList(List.of(1L)), graphData.successors(0L));
        assertEquals(new LongArrayList(List.of(2L)), graphData.successors(1L));
        assertEquals(graph.getEdges().size(), graphData.numArcs());
    }
}
