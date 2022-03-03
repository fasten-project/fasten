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

import eu.fasten.analyzer.callableindex.CallableIndexServerPlugin.CallableIndexFastenPlugin;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.callableindex.ExtendedGidGraph;
import eu.fasten.core.data.callableindex.GraphMetadata;
import eu.fasten.core.data.callableindex.GraphMetadata.ReceiverRecord.CallType;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.merge.CallGraphUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static eu.fasten.core.data.callableindex.GraphMetadata.*;
import static eu.fasten.core.utils.TestUtils.getTestResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CallableIndexServerPluginTest {

    private CallableIndexFastenPlugin callableIndexFastenPlugin;
    private final RocksDao rocksDao = Mockito.mock(RocksDao.class);

    @BeforeEach
    public void setUp() {
        callableIndexFastenPlugin = new CallableIndexFastenPlugin();
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

    @Disabled("This test is only for local development and debugging purposes! " +
        "It needs docker compose up and running with app:0.0.1" +
        "synthetic jar ingested. Local variable callableIndexPath should also be provided. ")
    @Test
    void integrationTestForSavingAGIDJsonToCallableIndex() throws RocksDBException {
        final var callableIndexPath = "";
        final var rocksDao = new RocksDao(callableIndexPath, false);
        final var ci = new CallableIndexFastenPlugin();
        ci.setRocksDao(rocksDao);

        consumeResource(ci, "app_0.0.1.json");

        NodeMetadata doRoadTripCallSites = getNodeMetadata(rocksDao, 1, 2);
        assertLine(doRoadTripCallSites, 15, "/lib/MotorVehicle.on()%2Fjava.lang%2FBooleanType",
            CallType.INTERFACE, List.of("/lib/MotorVehicle"));
        assertLine(doRoadTripCallSites, 16, "/lib/VehicleWash.wash(MotorVehicle)%2Fjava.lang%2FVoidType",
            CallType.VIRTUAL,  List.of("/lib/VehicleWash"));
        assertLine(doRoadTripCallSites, 17,  "/lib/MotorVehicle.off()%2Fjava.lang%2FBooleanType",
            CallType.INTERFACE, List.of("/lib/MotorVehicle"));
    }

    private NodeMetadata getNodeMetadata(final RocksDao rocksDao, final int graphId,
                                         final int nodeId) throws RocksDBException {
        final var dg = rocksDao.getGraphData(graphId);
        final var metadata = rocksDao.getGraphMetadata(graphId, dg);
        return metadata.gid2NodeMetadata.get(nodeId);
    }

    public void consumeResource(final CallableIndexFastenPlugin ci,
                                final String resourceName) {
        var json = new JSONObject("{\"payload\": {}}");
        var jsonFile = getTestResource(resourceName);
        json.getJSONObject("payload").put("dir", jsonFile.getPath());
        ci.consume(json.toString());
    }

    private void assertLine(final NodeMetadata doRoadTripCallSite, final int line,
                            final String signature, final CallType callType,
                              final List<String> receiverTypes) {
        final var line15 = getReceiverRecordForLine(doRoadTripCallSite, line);
        assert line15 != null;
        assertEquals(signature, line15.receiverSignature);
        assertEquals(callType, line15.callType);
        assertEquals(receiverTypes, line15.receiverTypes);
    }

    private ReceiverRecord getReceiverRecordForLine(final NodeMetadata nodeMetadata,
                                                    final int line){
        for (final var rec : nodeMetadata.receiverRecords) {
            if (rec.line == line) {
                return rec;
            }
        }
        return null;
    }
}
