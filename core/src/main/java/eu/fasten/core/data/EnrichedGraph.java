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

package eu.fasten.core.data;

import org.apache.commons.math3.util.Pair;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EnrichedGraph {

    public Set<Long> nodes;
    public Set<Pair<Long, Long>> edges;
    public Map<Long, JSONObject> nodesMetadata;
    public Map<Pair<Long, Long>, JSONObject> edgesMetadata;

    public EnrichedGraph(Set<Long> nodes, Set<Pair<Long, Long>> edges,
                         Map<Long, JSONObject> nodesMetadata, Map<Pair<Long, Long>, JSONObject> edgesMetadata) {
        if (!new HashSet<>(nodes).equals(new HashSet<>(nodesMetadata.keySet()))) {
            throw new IllegalArgumentException("Not all nodes have have metadata" +
                    " or not all nodes are present in the nodes list");
        }
        this.nodes = nodes;
        this.edges = edges;
        this.nodesMetadata = nodesMetadata;
        this.edgesMetadata = edgesMetadata;
    }

    public JSONObject toJSON() {
        var json = new JSONObject();
        var nodesJson = new JSONObject();
        for (var node : nodes) {
            nodesJson.put(String.valueOf(node), nodesMetadata.get(node));
        }
        json.put("nodes", nodesJson);
        var edgesJson = new JSONObject();
        for (var edge : edges) {
            edgesJson.put(edgeToString(edge),
                    (edgesMetadata.get(edge) != null) ? edgesMetadata.get(edge) : new JSONObject());
        }
        json.put("edges", edgesJson);
        return json;
    }

    private String edgeToString(Pair<Long, Long> edge) {
        return "[" + edge.getFirst() + ", " + edge.getSecond() + "]";
    }

    public static EnrichedGraph fromJSON(JSONObject json) {
        var nodesJson = json.getJSONObject("nodes");
        var nodes = new HashSet<Long>(nodesJson.keySet().size());
        var nodesMetadata = new HashMap<Long, JSONObject>(nodesJson.keySet().size());
        for (var key : nodesJson.keySet()) {
            var node = Long.parseLong(key);
            nodes.add(node);
            var metadata = nodesJson.getJSONObject(key);
            nodesMetadata.put(node, metadata);
        }
        var edgesJson = json.getJSONObject("edges");
        var edges = new HashSet<Pair<Long, Long>>(edgesJson.keySet().size());
        var edgesMetadata = new HashMap<Pair<Long, Long>, JSONObject>(edgesJson.keySet().size());
        for (var key : edgesJson.keySet()) {
            var edge = stringToEdge(key);
            edges.add(edge);
            var metadata = edgesJson.getJSONObject(key);
            if (!metadata.isEmpty()) {
                edgesMetadata.put(edge, metadata);
            }
        }
        return new EnrichedGraph(nodes, edges, nodesMetadata, edgesMetadata);
    }

    private static Pair<Long, Long> stringToEdge(String edge) {
        if (edge.startsWith("[") && edge.endsWith("]")) {
            edge = edge.substring(1, edge.length() - 1);
        }
        var parts = edge.split(",");
        var source = Long.parseLong(parts[0].trim());
        var target = Long.parseLong(parts[1].trim());
        return new Pair<>(source, target);
    }
}
