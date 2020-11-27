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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.json.JSONObject;
import org.json.JSONArray;

public class Graph {

    /**
     * Keeps all the internal calls of the graph. The metadata per call is stored as a map.
     */
    private final Map<List<Integer>, Map<Object, Object>> internalCalls;

    /**
     * Keeps all the external calls of the graph. The metadata per call is stored as a map.
     */
    private final Map<List<Integer>, Map<Object, Object>> externalCalls;

    /**
     * Keeps all the resolved calls of the graph. The metadata per call is stored as a map.
     */
    private final Map<List<Integer>, Map<Object, Object>> resolvedCalls;

    /**
     * Creates {@link Graph} from given internal, external, and resolved calls.
     *
     * @param internalCalls internal calls map
     * @param externalCalls external calls map
     * @param resolvedCalls resolved calls map
     */
    public Graph(final Map<List<Integer>, Map<Object, Object>> internalCalls,
                 final Map<List<Integer>, Map<Object, Object>> externalCalls,
                 final Map<List<Integer>, Map<Object, Object>> resolvedCalls) {
        this.internalCalls = internalCalls;
        this.externalCalls = externalCalls;
        this.resolvedCalls = resolvedCalls;
    }

    /**
     * Creates {@link Graph} for the given JSONObject.
     *
     * @param graph JSONObject of a graph including its internal calls and external calls.
     */
    public Graph(final JSONObject graph) {
        this.internalCalls = extractCalls(graph, "internalCalls");
        this.externalCalls = extractCalls(graph, "externalCalls");
        this.resolvedCalls = extractCalls(graph, "resolvedCalls");
    }

    /**
     * Creates {@link Graph} from given internal and external calls. Resolved calls are empty.
     *
     * @param internalCalls internal calls map
     * @param externalCalls external calls map
     */
    public Graph(final HashMap<List<Integer>, Map<Object, Object>> internalCalls,
                 final HashMap<List<Integer>, Map<Object, Object>> externalCalls) {
        this.internalCalls = internalCalls;
        this.externalCalls = externalCalls;
        this.resolvedCalls = new HashMap<>();
    }

    /**
     * Creates {@link Graph} with all fields empty.
     */
    public Graph() {
        this.internalCalls = new HashMap<>();
        this.externalCalls = new HashMap<>();
        this.resolvedCalls = new HashMap<>();
    }

    public Map<List<Integer>, Map<Object, Object>> getInternalCalls() {
        return internalCalls;
    }

    public Map<List<Integer>, Map<Object, Object>> getExternalCalls() {
        return externalCalls;
    }

    public Map<List<Integer>, Map<Object, Object>> getResolvedCalls() {
        return resolvedCalls;
    }

    /**
     * Get the total number of internal and external calls.
     *
     * @return total number of calls
     */
    public int size() {
        return internalCalls.size() + externalCalls.size();
    }

    /**
     * Get a call map from a given JSON array.
     *
     * @param call JSON array
     * @return call map
     */
    public Map<List<Integer>, Map<Object, Object>> getCall(final JSONArray call) {
        final Map<Object, Object> callSite = new HashMap<>();
        if (call.length() == 3) {
            final var callTypeJson = call.getJSONObject(2);
            for (String key : callTypeJson.keySet()) {
                final var pc = Integer.valueOf(key);
                callSite.put(pc, callTypeJson.getJSONObject(key).toMap());
            }
        }
        return Map.of(new ArrayList<>(Arrays.asList(Integer.valueOf(call.getString(0)),
                Integer.valueOf(call.getString(1)))), callSite);
    }

    /**
     * Extract calls from a provided JSON representation of a graph for a given key.
     *
     * @param graph graph of calls
     * @param key   key for calls extraction
     * @return extracted calls
     */
    private Map<List<Integer>, Map<Object, Object>> extractCalls(JSONObject graph, String key) {
        final var internalCalls = graph.getJSONArray(key);
        final Map<List<Integer>, Map<Object, Object>> result = new HashMap<>();
        final int numberOfArcs = internalCalls.length();
        for (int i = 0; i < numberOfArcs; i++) {
            result.putAll(getCall(internalCalls.getJSONArray(i)));
        }
        return result;
    }

    /**
     * Add calls from a given graph to this graph.
     *
     * @param graph a {@link Graph} to take new calls from
     */
    public void append(Graph graph) {
        this.internalCalls.putAll(graph.getInternalCalls());
        this.externalCalls.putAll(graph.getExternalCalls());
    }

    /**
     * Converts this {@link Graph} object to its JSON representation.
     *
     * @return the corresponding JSON representation.
     */
    public JSONObject toJSON() {
        final var result = new JSONObject();
        final var internalCallsJSON = new JSONArray();
        for (final var entry : this.internalCalls.entrySet()) {
            final var call = new JSONArray();
            call.put(entry.getKey().get(0).toString());
            call.put(entry.getKey().get(1).toString());
            call.put(new JSONObject(entry.getValue()));
            internalCallsJSON.put(call);
        }
        final var externalCallsJSON = new JSONArray();
        for (final var entry : this.externalCalls.entrySet()) {
            final var call = new JSONArray();
            call.put(entry.getKey().get(0).toString());
            call.put(entry.getKey().get(1).toString());
            call.put(new JSONObject(entry.getValue()));
            externalCallsJSON.put(call);
        }

        final var resolvedCallsJSON = new JSONArray();
        for (final var entry : this.resolvedCalls.entrySet()) {
            final var call = new JSONArray();
            call.put(entry.getKey().get(0).toString());
            call.put(entry.getKey().get(1).toString());
            call.put(new JSONObject(entry.getValue()));
            resolvedCallsJSON.put(call);
        }
        result.put("internalCalls", internalCallsJSON);
        result.put("externalCalls", externalCallsJSON);
        result.put("resolvedCalls", resolvedCallsJSON);
        return result;
    }
}
