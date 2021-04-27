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

package eu.fasten.core.data.graphdb;

import eu.fasten.core.data.metadatadb.codegen.enums.CallType;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallSitesRecord;
import org.apache.commons.math3.util.Pair;
import org.jooq.tools.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExtendedGidGraph extends GidGraph {

    private final Map<Pair<Long, Long>, CallSitesRecord> callInfo = new HashMap<>();
    private final Map<Long, String> gidToUriMap;
    private final Map<Long, String> typeMap;

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
    public ExtendedGidGraph(long index, String product, String version, List<Long> nodes, int numInternalNodes, List<CallSitesRecord> edges, Map<Long, String> gid2UriMap, Map<Long, String> typeMap) {
        super(index, product, version, nodes, numInternalNodes, edges);
        this.gidToUriMap = gid2UriMap;
        edges.forEach(e -> callInfo.put(new Pair<>(e.getSourceId(), e.getTargetId()), e));
        this.typeMap = typeMap;
    }

    public Map<Pair<Long, Long>, CallSitesRecord> getCallsInfo() {
        return this.callInfo;
    }

    public List<List<Long>> getEdges() {
        return this.callInfo.keySet().stream().map(p -> List.of(p.getFirst(), p.getSecond())).collect(Collectors.toList());
    }

    public Map<Long, String> getGidToUriMap() {
        return gidToUriMap;
    }

    public Map<Long, String> getTypeMap() {
        return typeMap;
    }

    @Override
    public JSONObject toJSON() {
        var json = super.toJSON();
        var callSitesInfo = new JSONObject();
        getCallsInfo().forEach((edge, info) -> {
            var edgeStr = String.format("[%d, %d]", edge.getFirst(), edge.getSecond());
            var infoJson = new JSONObject();
            infoJson.put("line", info.getLine());
            infoJson.put("receiver_type_ids", new JSONArray(Arrays.asList(info.getReceiverTypeIds())));
            infoJson.put("call_type", info.getCallType().getLiteral());
            callSitesInfo.put(edgeStr, infoJson);
        });
        json.put("callsites_info", callSitesInfo);
        var gidToUriJson = new JSONObject();
        this.gidToUriMap.forEach((k, v) -> gidToUriJson.put(String.valueOf(k), v));
        json.put("gid_to_uri", gidToUriJson);
        var typesJson = new JSONObject();
        this.typeMap.forEach((k, v) -> typesJson.put(String.valueOf(k), v));
        json.put("types_map", typesJson);
        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ExtendedGidGraph that = (ExtendedGidGraph) o;
        return gidToUriMap.equals(that.gidToUriMap)
                && callInfo != null ? callInfo.equals(that.callInfo) : that.callInfo == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (callInfo != null ? callInfo.hashCode() : 0);
        result = 31 * result + (gidToUriMap != null ? gidToUriMap.hashCode() : 0);
        return result;
    }

    public static ExtendedGidGraph getGraph(JSONObject jsonGraph) throws JSONException {
        if (jsonGraph == null) {
            throw new JSONException("JSON Graph cannot be null");
        }
        var index = jsonGraph.getLong("index");
        var product = jsonGraph.getString("product");
        var version = jsonGraph.getString("version");
        var jsonNodes = jsonGraph.getJSONArray("nodes");
        List<Long> nodes = new ArrayList<>(jsonNodes.length());
        for (int i = 0; i < jsonNodes.length(); i++) {
            nodes.add(jsonNodes.getLong(i));
        }
        var numInternalNodes = jsonGraph.getInt("numInternalNodes");
        var callSitesInfo = jsonGraph.getJSONObject("callsites_info");
        var callSitesList = new ArrayList<CallSitesRecord>(callSitesInfo.length());
        callSitesInfo.keySet().forEach(k -> {
            var key = k.substring(1, k.length() - 1).split(",");
            var source = Long.parseLong(key[0].trim());
            var target = Long.parseLong(key[1].trim());
            var infoJson = callSitesInfo.getJSONObject(k);
            var line = infoJson.getInt("line");
            var callType = getCallType(infoJson.getString("call_type"));
            var receiverTypeIdsJson = infoJson.getJSONArray("receiver_type_ids");
            var receiverTypeIds = new Long[receiverTypeIdsJson.length()];
            for (int i = 0; i < receiverTypeIdsJson.length(); i++) {
                receiverTypeIds[i] = receiverTypeIdsJson.getLong(i);
            }
            callSitesList.add(new CallSitesRecord(source, target, line, callType, receiverTypeIds, null));
        });
        var gid2uriMap = new HashMap<Long, String>(nodes.size());
        var gidToUriJson = jsonGraph.getJSONObject("gid_to_uri");
        gidToUriJson.keySet().forEach(k -> gid2uriMap.put(Long.parseLong(k), gidToUriJson.getString(k)));
        var typesMap = new HashMap<Long, String>();
        var typesJson = jsonGraph.getJSONObject("types_map");
        typesJson.keySet().forEach(k -> typesMap.put(Long.parseLong(k), typesJson.getString(k)));
        return new ExtendedGidGraph(index, product, version, nodes, numInternalNodes, callSitesList, gid2uriMap, typesMap);
    }

    private static CallType getCallType(String type) {
        switch (type.toLowerCase()) {
            case "static":
                return CallType.static_;
            case "dynamic":
                return CallType.dynamic;
            case "virtual":
                return CallType.virtual;
            case "interface":
                return CallType.interface_;
            case "special":
                return CallType.special;
            default:
                throw new IllegalArgumentException("Unknown call type: " + type);
        }
    }
}
