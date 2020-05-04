package eu.fasten.core.data.metadatadb.graph;

import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import org.jooq.JSONB;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Graph {

    private final String product;
    private final String version;
    private final List<Long> nodes;
    private final List<List<Long>> edges;

    /**
     * Constructor for Graph.
     *
     * @param product Product name
     * @param version Product version
     * @param nodes List of Global IDs of nodes of the graph
     * @param edges List of edges of the graph with pairs for Global IDs
     */
    public Graph(String product, String version, List<Long> nodes, List<EdgesRecord> edges) {
        this.product = product;
        this.version = version;
        this.nodes = nodes;
        this.edges = edges.parallelStream()
                .map((r) -> List.of(r.getSourceId(), r.getTargetId()))
                .collect(Collectors.toList());
    }

    public String getProduct() {
        return product;
    }

    public String getVersion() {
        return version;
    }

    public List<Long> getNodes() {
        return nodes;
    }

    public List<List<Long>> getEdges() {
        return edges;
    }

    /**
     * Converts the Graph object into JSON string.
     *
     * @return JSON representation of the graph as a String
     */
    public String toJSONString() {
        var json = new JSONObject();
        json.put("product", getProduct());
        json.put("version", getVersion());
        json.put("nodes", getNodes());
        json.put("edges", getEdges());
        return json.toString();
    }

    /**
     * Creates Graph object from JSON object.
     *
     * @param jsonGraph JSONObject representing a graph
     * @return Graph instance
     * @throws JSONException if JSON graph is null or is not in correct form
     */
    public static Graph getGraph(JSONObject jsonGraph) throws JSONException {
        if (jsonGraph == null) {
            throw new JSONException("JSON Graph cannot be null");
        }
        var product = jsonGraph.getString("product");
        var version = jsonGraph.getString("version");
        var jsonNodes = jsonGraph.getJSONArray("nodes");
        List<Long> nodes = new ArrayList<>(jsonNodes.length());
        for (int i = 0; i < jsonNodes.length(); i++) {
            nodes.add(jsonNodes.getLong(i));
        }
        var jsonEdges = jsonGraph.getJSONArray("edges");
        List<EdgesRecord> edges = new ArrayList<>(jsonEdges.length());
        for (int i = 0; i < jsonEdges.length(); i++) {
            var edgeArr = jsonEdges.getJSONArray(i);
            var edge = new EdgesRecord(edgeArr.getLong(0), edgeArr.getLong(1), JSONB.valueOf(""));
            edges.add(edge);
        }
        return new Graph(product, version, nodes, edges);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Graph graph = (Graph) o;
        if (!Objects.equals(product, graph.product)) {
            return false;
        }
        if (!Objects.equals(version, graph.version)) {
            return false;
        }
        if (!Objects.equals(nodes, graph.nodes)) {
            return false;
        }
        return Objects.equals(edges, graph.edges);
    }

    @Override
    public String toString() {
        return this.toJSONString();
    }
}
