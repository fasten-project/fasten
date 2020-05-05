package eu.fasten.core.data.metadatadb.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class GraphTest {

    @Test
    public void graphTest() {
        var json = new JSONObject("{" +
                "\"product\": \"test\"," +
                "\"version\": \"0.0.1\"," +
                "\"nodes\": [1, 2, 3]," +
                "\"numInternalNodes\": 2," +
                "\"edges\": [[1, 2], [2, 3]]" +
                "}");
        var graph = Graph.getGraph(json);
        assertEquals(json.toString(), graph.toJSONString());
    }

    @Test
    public void graphErrorTest() {
        var json = new JSONObject("{\"foo\": \"bar\"}");
        assertThrows(JSONException.class, () -> Graph.getGraph(json));
        assertThrows(JSONException.class, () -> Graph.getGraph(null));
    }
}
