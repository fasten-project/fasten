package eu.fasten.analyzer.javacgwala;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import java.io.FileNotFoundException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WALAPluginTest {

    final String topic = "maven.packages";
    static WALAPlugin.WALA walaPlugin;

    @BeforeAll
    public static void setUp() {
        walaPlugin = new WALAPlugin.WALA();
    }

    @Test
    public void testConsumerTopic() {
        assertEquals("maven.packages", walaPlugin.consumerTopics().get(0));
    }

    @Test
    public void testConsume() throws JSONException, FileNotFoundException {

        JSONObject coordinateJSON = new JSONObject("{\n" +
                "    \"groupId\": \"org.slf4j\",\n" +
                "    \"artifactId\": \"slf4j-api\",\n" +
                "    \"version\": \"1.7.29\",\n" +
                "    \"date\":\"1574072773\"\n" +
                "}");

        var cg = walaPlugin.consume(new ConsumerRecord<>(topic, 1, 0, "foo", coordinateJSON.toString()));

        var coordinate = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29");
        var revisionCallGraph =
                CallGraphConstructor.build(coordinate)
                        .toRevisionCallGraph(1574072773);

        assertEquals(revisionCallGraph.toJSON().toString(), cg.toJSON().toString());
    }

    @Test
    public void testEmptyCallGraph() {
        JSONObject emptyCGCoordinate = new JSONObject("{\n" +
                "    \"groupId\": \"activemq\",\n" +
                "    \"artifactId\": \"activemq\",\n" +
                "    \"version\": \"release-1.5\",\n" +
                "    \"date\":\"1574072773\"\n" +
                "}");

        var cg = walaPlugin.consume(new ConsumerRecord<>(topic, 1, 0, "bar", emptyCGCoordinate.toString()));

        assertEquals(0, cg.graph.size());
    }

    @Test
    public void testFileNotFoundException() {
        JSONObject noJARFile = new JSONObject("{\n" +
                "    \"groupId\": \"com.visionarts\",\n" +
                "    \"artifactId\": \"power-jambda-pom\",\n" +
                "    \"version\": \"0.9.10\",\n" +
                "    \"date\":\"1521511260\"\n" +
                "}");

        walaPlugin.consume(topic, new ConsumerRecord<>(topic, 1, 0, "bar", noJARFile.toString()));

        assertEquals(FileNotFoundException.class.getSimpleName(), walaPlugin.getPluginError());
        assertFalse(walaPlugin.recordProcessSuccessful());
    }

    @Test
    public void testShouldNotFaceClassReadingError() throws JSONException, FileNotFoundException {

        JSONObject coordinateJSON1 = new JSONObject("{\n" +
                "    \"groupId\": \"com.zarbosoft\",\n" +
                "    \"artifactId\": \"coroutines-core\",\n" +
                "    \"version\": \"0.0.3\",\n" +
                "    \"date\":\"1574072773\"\n" +
                "}");

        var cg = walaPlugin.consume(new ConsumerRecord<>(topic, 1, 0, "foo", coordinateJSON1.toString()));
        var coordinate = new MavenCoordinate("com.zarbosoft", "coroutines-core", "0.0.3");
        var extendedRevisionCallGraph =
                CallGraphConstructor.build(coordinate).toRevisionCallGraph(1574072773);

        assertEquals(extendedRevisionCallGraph.toJSON().toString(), cg.toJSON().toString());
    }

    @Test
    public void testProducerTopic() {
        assertEquals("wala_callgraphs", walaPlugin.producerTopic());
    }

    @Test
    public void testName() {
        assertEquals("eu.fasten.analyzer.javacgwala.WALAPlugin.WALA", walaPlugin.name());
    }
}