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

package eu.fasten.analyzer.javacgopal;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgopal.data.callgraph.PartialCallGraph;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.FileNotFoundException;

import static org.junit.Assert.*;

public class OPALPluginTest {

    final String topic = "maven.packages";
    static OPALPlugin.OPAL opalPlugin;

    @BeforeClass
    public static void instantiatePlugin() {
        opalPlugin = new OPALPlugin.OPAL();
    }

    @Test
    public void testConsumerTopic() {
        assertEquals("maven.packages", opalPlugin.consumerTopics().get(0));
    }

    @Test
    public void testConsume() throws JSONException, FileNotFoundException {

        JSONObject coordinateJSON = new JSONObject("{\n" +
                "    \"groupId\": \"org.slf4j\",\n" +
                "    \"artifactId\": \"slf4j-api\",\n" +
                "    \"version\": \"1.7.29\",\n" +
                "    \"date\":\"1574072773\"\n" +
                "}");

        var cg = opalPlugin.consume(new ConsumerRecord<>(topic, 1, 0, "foo", coordinateJSON.toString()), false);

        var extendedRevisionCallGraph  = ExtendedRevisionCallGraph.create("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29"), 1574072773,
                new PartialCallGraph(MavenCoordinate.MavenResolver.downloadJar("org.slf4j:slf4j-api:1.7.29").orElseThrow(RuntimeException::new))
        );

        JSONAssert.assertEquals(extendedRevisionCallGraph.toJSON(), cg.toJSON(), false);
    }

    @Test
    public void testShouldNotFaceClassReadingError() throws JSONException, FileNotFoundException {

        JSONObject coordinateJSON1 = new JSONObject("{\n" +
                "    \"groupId\": \"com.zarbosoft\",\n" +
                "    \"artifactId\": \"coroutines-core\",\n" +
                "    \"version\": \"0.0.3\",\n" +
                "    \"date\":\"1574072773\"\n" +
                "}");

        var cg = opalPlugin.consume(new ConsumerRecord<>(topic, 1, 0, "foo", coordinateJSON1.toString()), false);

        var extendedRevisionCallGraph = ExtendedRevisionCallGraph.create("mvn",
                new MavenCoordinate("com.zarbosoft", "coroutines-core", "0.0.3"), 1574072773,
                new PartialCallGraph(MavenCoordinate.MavenResolver.downloadJar("com.zarbosoft:coroutines-core:0.0.3").orElseThrow(RuntimeException::new))
        );

        JSONAssert.assertEquals(extendedRevisionCallGraph.toJSON(), cg.toJSON(), false);
    }

    @Test
    public void testEmptyCallGraph() {
        JSONObject emptyCGCoordinate = new JSONObject("{\n" +
                "    \"groupId\": \"activemq\",\n" +
                "    \"artifactId\": \"activemq\",\n" +
                "    \"version\": \"release-1.5\",\n" +
                "    \"date\":\"1574072773\"\n" +
                "}");

        var cg = opalPlugin.consume(new ConsumerRecord<>(topic, 1, 0, "bar", emptyCGCoordinate.toString()), false);

        assertTrue(cg.isCallGraphEmpty());
    }

    @Test
    public void testFileNotFoundException() {
        JSONObject noJARFile = new JSONObject("{\n" +
                "    \"groupId\": \"com.visionarts\",\n" +
                "    \"artifactId\": \"power-jambda-pom\",\n" +
                "    \"version\": \"0.9.10\",\n" +
                "    \"date\":\"1521511260\"\n" +
                "}");

        opalPlugin.consume(new ConsumerRecord<>(topic, 1, 0, "bar", noJARFile.toString()), false);

        assertEquals(FileNotFoundException.class.getSimpleName(), opalPlugin.getPluginError());
        assertFalse(opalPlugin.recordProcessSuccessful());
    }

    @Test
    public void testProducerTopic() {
        assertEquals("opal_callgraphs", opalPlugin.producerTopic());
    }

    @Test
    public void testName() {
        assertEquals("eu.fasten.analyzer.javacgopal.OPALPlugin.OPAL", opalPlugin.name());
    }
}