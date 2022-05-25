package eu.fasten.core.data.utils;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;

public class HybridDirectedGraphSerializerDeserializerTest {

    @Test
    public void serializeDeserializeTest() {
        var str = "{\"nodes\":{\"3\":\"method3\",\"2\":\"method2\",\"1\":\"method1\"},\"edges\":[[2,3,{\"static\":true,\"dynamic\":true}],[1,2,{\"static\":false,\"dynamic\":true}]]}";
        var serializer = new HybridDirectedGraphSerializer();
        var deserializer = new HybridDirectedGraphDeserializer();
        var graphMapPair = deserializer.jsonToGraph(str);
        var result = serializer.graphToJson(graphMapPair.first(), graphMapPair.second());
        assertJsonEquals(new JSONObject(str), new JSONObject(result));
    }
}
