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

import java.util.Map;
import java.util.HashMap;
import org.json.JSONObject;

import it.unimi.dsi.fastutil.ints.IntIntPair;

import org.json.JSONArray;

public class Graph {

    /**
     * Keeps all the internal calls of the graph. The metadata per call is stored as a map.
     */
    private final Map<IntIntPair, Map<Object, Object>> callSites;

    /**
     * Creates {@link Graph} from given call-sites.
     *
     * @param callSites internal calls map
     */
    public Graph(final Map<IntIntPair, Map<Object, Object>> callSites) {
        this.callSites = callSites;
    }

    /**
     * Creates {@link Graph} for the given JSONObject.
     *
     * @param graph JSONObject of a graph including its internal calls and external calls.
     */
    public Graph(final JSONObject graph) {
        this.callSites = extractCalls(graph, "call-sites");
    }

    /**
     * Creates {@link Graph} from given call-sites
     *
     * @param callSites call-sites map
     */
    public Graph(final HashMap<IntIntPair, Map<Object, Object>> callSites) {
        this.callSites = callSites;
    }

    /**
     * Creates {@link Graph} with all fields empty.
     */
    public Graph() {
        this.callSites = new HashMap<>();
    }

    public Map<IntIntPair, Map<Object, Object>> getCallSites() {
        return callSites;
    }

    /**
     * Get the total number of internal and external calls.
     *
     * @return total number of calls
     */
    public int size() {
        return callSites.size();
    }

    /**
     * Get a call map from a given JSON array.
     *
     * @param call JSON array
     * @return call map
     */
    public Map<IntIntPair, Map<Object, Object>> getCall(final JSONArray call) {
        final Map<Object, Object> callSite = new HashMap<>();
        if (call.length() == 3) {
            final var callTypeJson = call.getJSONObject(2);
            for (String key : callTypeJson.keySet()) {
                final var pc = Integer.valueOf(key);
                callSite.put(pc, callTypeJson.getJSONObject(key).toMap());
            }
        }
        return Map.of(IntIntPair.of(Integer.parseInt(call.getString(0)),
                Integer.parseInt(call.getString(1))), callSite);
    }

    /**
     * Extract calls from a provided JSON representation of a graph for a given key.
     *
     * @param graph graph of calls
     * @param key   key for calls extraction
     * @return extracted calls
     */
    private Map<IntIntPair, Map<Object, Object>> extractCalls(JSONObject graph, String key) {
        final var calls = graph.getJSONArray(key);
        final Map<IntIntPair, Map<Object, Object>> result = new HashMap<>();
        final int numberOfArcs = calls.length();
        for (int i = 0; i < numberOfArcs; i++) {
            result.putAll(getCall(calls.getJSONArray(i)));
        }
        return result;
    }

    /**
     * Add calls from a given graph to this graph.
     *
     * @param graph a {@link Graph} to take new calls from
     */
    public void append(Graph graph) {
        this.callSites.putAll(graph.getCallSites());
    }

    /**
     * Converts this {@link Graph} object to its JSON representation.
     *
     * @return the corresponding JSON representation.
     */
    public JSONObject toJSON() {
        final var result = new JSONObject();
        final var internalCallsJSON = new JSONArray();
        for (final var entry : this.callSites.entrySet()) {
            final var call = new JSONArray();
            call.put(entry.getKey().first().toString());
            call.put(entry.getKey().second().toString());
            call.put(new JSONObject(entry.getValue()));
            internalCallsJSON.put(call);
        }
        result.put("call-sites", internalCallsJSON);
        return result;
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

        if (callSites != null) {
            return callSites.equals(graph.callSites);
        } else return graph.callSites == null;
    }

    @Override
    public int hashCode() {
        return callSites != null ? callSites.hashCode() : 0;
    }
}
