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

package eu.fasten.core.dynamic;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.dynamic.data.DynamicJavaCG;
import eu.fasten.core.dynamic.data.HybridDirectedGraph;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Map;
import java.util.Set;

public class StaticDynamicCGCombiner {

    private final Long2ObjectMap<String> staticCgUriMap;
    private final Set<LongLongPair> staticCalls;
    private final Long2ObjectMap<String> dynamicCgUriMap;
    private final Set<LongLongPair> dynamicCalls;
    private Long2ObjectMap<String> allUrisMap;

    public StaticDynamicCGCombiner(final DirectedGraph staticCg,
                                   final Map<Long, String> staticCgUriMap,
                                   final DynamicJavaCG dynamicCg) {
        this.staticCgUriMap = new Long2ObjectOpenHashMap<>(staticCgUriMap);
        this.staticCalls = staticCg.edgeSet();
        this.dynamicCgUriMap = dynamicCg.getMethods();
        this.dynamicCalls = dynamicCg.getCalls();
        this.allUrisMap = null;
    }

    public Long2ObjectMap<String> getAllUrisMap() {
        return this.allUrisMap;
    }

    public HybridDirectedGraph combineCGs() {
        var staticUriCalls = new ObjectOpenHashSet<ObjectObjectImmutablePair<String, String>>(this.staticCalls.size());
        this.staticCalls.forEach(call -> staticUriCalls.add(ObjectObjectImmutablePair.of(
                this.staticCgUriMap.get(call.firstLong()),
                this.staticCgUriMap.get(call.secondLong())
        )));
        var dynamicUriCalls = new ObjectOpenHashSet<ObjectObjectImmutablePair<String, String>>(this.dynamicCalls.size());
        this.dynamicCalls.forEach(call -> dynamicUriCalls.add(ObjectObjectImmutablePair.of(
                this.dynamicCgUriMap.get(call.firstLong()),
                this.dynamicCgUriMap.get(call.secondLong())
        )));
        var allUriCalls = new ObjectOpenHashSet<ObjectObjectImmutablePair<String, String>>(this.staticCalls.size() + this.dynamicCalls.size());
        allUriCalls.addAll(staticUriCalls);
        allUriCalls.addAll(dynamicUriCalls);
        var allUrisMap = new Long2ObjectOpenHashMap<String>(this.staticCgUriMap.size() + this.dynamicCgUriMap.size());
        var c = 0L;
        for (var call : allUriCalls) {
            var source = call.left();
            var target = call.right();
            if (!allUrisMap.containsValue(source)) {
                allUrisMap.put(c++, source);
            }
            if (!allUrisMap.containsValue(target)) {
                allUrisMap.put(c++, target);
            }
        }
        this.allUrisMap = allUrisMap;
        var inverseUrisMap = new Object2LongOpenHashMap<String>(allUrisMap.size());
        allUrisMap.forEach((k, v) -> inverseUrisMap.put(v, k.longValue()));
        var allCalls = new ObjectOpenHashSet<LongLongPair>(allUriCalls.size());
        var isStaticCallMap = new Object2BooleanOpenHashMap<LongLongPair>(allUriCalls.size());
        allUriCalls.forEach(call -> {
            var callIds = LongLongPair.of(
                    inverseUrisMap.getLong(call.left()),
                    inverseUrisMap.getLong(call.right())
            );
            isStaticCallMap.put(callIds, staticUriCalls.contains(call));
            allCalls.add(callIds);
        });
        var graph = new HybridDirectedGraph(isStaticCallMap);
        allUrisMap.keySet().forEach(graph::addVertex);
        allCalls.forEach(call -> graph.addEdge(call.firstLong(), call.secondLong()));
        return graph;
    }
}
