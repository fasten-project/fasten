package eu.fasten.core.data.utils;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DirectedGraphSerializerDeserializerTest {

    @Test
    public void serializeDeserializeTest() {
        var idToUriMap = Map.of(
                1L, "method1",
                2L, "method2",
                3L, "method3"
        );
        var edges = Set.of(
                LongLongPair.of(1, 2),
                LongLongPair.of(2, 3)
        );
        var builder = new ArrayImmutableDirectedGraph.Builder();
        idToUriMap.keySet().forEach(builder::addInternalNode);
        edges.forEach(e -> builder.addArc(e.firstLong(), e.secondLong()));
        var graph = builder.build();
        var serializer = new DirectedGraphSerializer();
        var str = serializer.graphToJson(graph, idToUriMap);
        var deserializer = new DirectedGraphDeserializer();
        var result = deserializer.jsonToGraph(str);
        assertEquals(graph.nodes(), result.first().nodes());
        assertEquals(graph.edgeSet(), result.first().edgeSet());
        assertEquals(idToUriMap, result.second());
    }
}
