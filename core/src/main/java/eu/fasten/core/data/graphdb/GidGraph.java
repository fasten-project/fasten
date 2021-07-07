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

import eu.fasten.core.data.metadatadb.codegen.tables.records.CallSitesRecord;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GidGraph {

    private final long index;
    private final String product;
    private final String version;
    private final List<Long> nodes;
    private final int numInternalNodes;
    private final List<List<Long>> edges;

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
    public GidGraph(long index, String product, String version, List<Long> nodes, int numInternalNodes,
                    List<CallSitesRecord> edges) {
        this.index = index;
        this.product = product;
        this.version = version;
        this.nodes = nodes;
        this.numInternalNodes = numInternalNodes;
        this.edges = edges.parallelStream()
                .map((r) -> List.of(r.getSourceId(), r.getTargetId()))
                .collect(Collectors.toList());
    }

    public long getIndex() {
        return index;
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

    public int getNumInternalNodes() {
        return numInternalNodes;
    }

    public List<List<Long>> getEdges() {
        return edges;
    }

    /**
     * Converts the Graph object into JSON string.
     *
     * @return JSON representation of the graph
     */
    public JSONObject toJSON() {
        var json = new JSONObject();
        json.put("index", getIndex());
        json.put("product", getProduct());
        json.put("version", getVersion());
        json.put("nodes", getNodes());
        json.put("numInternalNodes", getNumInternalNodes());
        json.put("edges", getEdges());
        return json;
    }

    /**
     * Creates Graph object from JSON object.
     *
     * @param jsonGraph JSONObject representing a graph
     * @return Graph instance
     * @throws JSONException if JSON graph is null or is not in correct form
     */
    public static GidGraph getGraph(JSONObject jsonGraph) throws JSONException {
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
        var jsonEdges = jsonGraph.getJSONArray("edges");
        List<CallSitesRecord> edges = new ArrayList<>(jsonEdges.length());
        for (int i = 0; i < jsonEdges.length(); i++) {
            var edgeArr = jsonEdges.getJSONArray(i);
            var edge = new CallSitesRecord(edgeArr.getLong(0), edgeArr.getLong(1), null, null, null, null);
            edges.add(edge);
        }
        return new GidGraph(index, product, version, nodes, numInternalNodes, edges);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GidGraph gidGraph = (GidGraph) o;
        if (!Objects.equals(index, gidGraph.index)) {
            return false;
        }
        if (!Objects.equals(product, gidGraph.product)) {
            return false;
        }
        if (!Objects.equals(version, gidGraph.version)) {
            return false;
        }
        if (!Objects.equals(nodes, gidGraph.nodes)) {
            return false;
        }
        if (!Objects.equals(numInternalNodes, gidGraph.numInternalNodes)) {
            return false;
        }
        return Objects.equals(edges, gidGraph.edges);
    }

    @Override
    public int hashCode() {
        int result = (int) (index ^ (index >>> 32));
        result = 31 * result + (product != null ? product.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (nodes != null ? nodes.hashCode() : 0);
        result = 31 * result + numInternalNodes;
        result = 31 * result + (edges != null ? edges.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
}
