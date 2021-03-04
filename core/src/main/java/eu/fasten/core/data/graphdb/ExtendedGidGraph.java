package eu.fasten.core.data.graphdb;

import eu.fasten.core.data.JavaCallSite;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord;
import org.apache.commons.math3.util.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.*;
import java.util.stream.Collectors;
import static eu.fasten.core.data.JavaCallSite.getReceiverType;

public class ExtendedGidGraph extends GidGraph {

    private final Map<Pair<Long, Long>, List<JavaCallSite>> edgesInfo = new HashMap<>();

    /**
     * Constructor for Graph.
     *
     * @param index            ID of the graph (index from postgres)
     * @param product          Product name
     * @param version          Product version
     * @param nodes            List of Global IDs of nodes of the graph
     *                         (first internal nodes, then external nodes)
     * @param numInternalNodes Number of internal nodes in nodes list
     * @param edges            List of edges of the graph with pairs for Global IDs
     */
    public ExtendedGidGraph(long index, String product, String version, List<Long> nodes, int numInternalNodes, List<EdgesRecord> edges) {
        super(index, product, version, nodes, numInternalNodes, edges);
        edges.forEach(e -> edgesInfo.put(new Pair<>(e.getSourceId(), e.getTargetId()),
                Arrays.stream(e.getReceivers()).map(r -> new JavaCallSite(r.getReceiverUri(), r.getType())).collect(Collectors.toList())));
    }

    public Map<Pair<Long, Long>, List<JavaCallSite>> getEdgesInfo() {
        return this.edgesInfo;
    }

    @Override
    public JSONObject toJSON() {
        var json = super.toJSON();
        var edgesInfoJson = new JSONObject();
        getEdgesInfo().forEach((edge, info) -> {
            var edgeStr = String.format("[%d, %d]", edge.getFirst(), edge.getSecond());
            var infoArray = new JSONArray();
            info.forEach(r -> {
                var callSiteJson = new JSONObject();
                callSiteJson.put("receiver_namespace", r.getReceiverNamespace());
                callSiteJson.put("call_type", r.getCallType().getLiteral());
                infoArray.put(callSiteJson);
            });
            edgesInfoJson.put(edgeStr, infoArray);
        });
        json.put("edges_info", edgesInfoJson);
        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ExtendedGidGraph that = (ExtendedGidGraph) o;
        return edgesInfo != null ? edgesInfo.equals(that.edgesInfo) : that.edgesInfo == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (edgesInfo != null ? edgesInfo.hashCode() : 0);
        return result;
    }

    public static ExtendedGidGraph getGraph(JSONObject jsonGraph) throws JSONException {
        var gidGraph = GidGraph.getGraph(jsonGraph);
        var edgesInfoJson = jsonGraph.getJSONObject("edges_info");
        var edgesList = new ArrayList<EdgesRecord>(edgesInfoJson.length());
        edgesInfoJson.keySet().forEach(k -> {
            var key = k.substring(1, k.length() - 1).split(",");
            var source = Long.parseLong(key[0].trim());
            var target = Long.parseLong(key[1].trim());
            var infoArray = edgesInfoJson.getJSONArray(k);
            var callSites = new ReceiverRecord[infoArray.length()];
            for (int i = 0; i < infoArray.length(); i++) {
                var callSiteJson = infoArray.getJSONObject(i);
                var callSite = new ReceiverRecord(
                        -1,
                        getReceiverType(callSiteJson.getString("call_type")),
                        callSiteJson.getString("receiver_namespace")
                );
                callSites[i] = callSite;
            }
            edgesList.add(new EdgesRecord(source, target, callSites, null));
        });
        return new ExtendedGidGraph(gidGraph.getIndex(), gidGraph.getProduct(), gidGraph.getVersion(), gidGraph.getNodes(), gidGraph.getNumInternalNodes(), edgesList);
    }
}
