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

package eu.fasten.core.data.utils;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.HybridDirectedGraph;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.Set;

public class HybridDirectedGraphDeserializer {

    public Pair<HybridDirectedGraph, Map<Long, String>> jsonToGraph(String strJson) {
        var json = new JSONObject(strJson);
        var jsonNodes = json.getJSONObject("nodes");
        var idToUriMap = getIdToUriMap(jsonNodes);
        var jsonEdges = json.getJSONArray("edges");
        var mapEdgesPair = getEdges(jsonEdges);
        var callOriginMap = mapEdgesPair.first();
        var edges = mapEdgesPair.second();
        var builder = new ArrayImmutableDirectedGraph.Builder();
        idToUriMap.keySet().forEach(builder::addInternalNode);
        edges.forEach(e -> builder.addArc(e.firstLong(), e.secondLong()));
        var graph = new HybridDirectedGraph(callOriginMap);
        idToUriMap.keySet().forEach(graph::addVertex);
        edges.forEach(call -> graph.addEdge(call.firstLong(), call.secondLong()));
        return Pair.of(graph, idToUriMap);
    }

    private Map<Long, String> getIdToUriMap(JSONObject jsonNodes) {
        var idToUriMap = new Long2ObjectOpenHashMap<String>(jsonNodes.length());
        for (var node : jsonNodes.keySet()) {
            var id = Long.parseLong(node);
            var uri = jsonNodes.getString(node);
            idToUriMap.put(id, uri);
        }
        return idToUriMap;
    }

    private Pair<Map<LongLongPair, HybridDirectedGraph.CallOrigin>, Set<LongLongPair>> getEdges(JSONArray jsonEdges) {
        var edges = new ObjectOpenHashSet<LongLongPair>(jsonEdges.length());
        var callOriginMap = new Object2ObjectOpenHashMap<LongLongPair, HybridDirectedGraph.CallOrigin>(jsonEdges.length());
        for (int i = 0; i < jsonEdges.length(); i++) {
            var jsonEdge = jsonEdges.getJSONArray(i);
            var edge = new LongLongImmutablePair(jsonEdge.getLong(0), jsonEdge.getLong(1));
            edges.add(edge);
            var metadata = jsonEdge.getJSONObject(2);
            var isStatic = metadata.getBoolean("static");
            var isDynamic = metadata.getBoolean("dynamic");
            callOriginMap.put(edge, getCallOrigin(isStatic, isDynamic));
        }
        return Pair.of(callOriginMap, edges);
    }

    private HybridDirectedGraph.CallOrigin getCallOrigin(boolean isStatic, boolean isDynamic) {
        if (isStatic && isDynamic) {
            return HybridDirectedGraph.CallOrigin.staticAndDynamicCgs;
        } else if (isStatic) {
            return HybridDirectedGraph.CallOrigin.staticCg;
        } else if (isDynamic) {
            return HybridDirectedGraph.CallOrigin.dynamicCg;
        }
        return null;
    }
}
