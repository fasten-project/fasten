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

package eu.fasten.core.dynamic.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.json.JSONObject;
import java.util.Objects;
import java.util.Set;

public class DynamicJavaCG {

    private final Long2ObjectMap<String> methods;
    private final Set<LongLongPair> calls;

    public DynamicJavaCG(Long2ObjectMap<String> methods, Set<LongLongPair> calls) {
        this.methods = methods;
        this.calls = calls;
    }

    public DynamicJavaCG(JSONObject json) {
        var nodes = json.getJSONObject("nodes");
        var methodIdsMap = new Long2ObjectOpenHashMap<String>();
        for (var type : nodes.keySet()) {
            var methods = nodes.getJSONObject(type).getJSONObject("methods");
            for (var id : methods.keySet()) {
                methodIdsMap.put(Long.parseLong(id), methods.getString(id));
            }
        }
        this.methods = methodIdsMap;
        var graph = json.getJSONArray("graph");
        var calls = new ObjectOpenHashSet<LongLongPair>(graph.length());
        for (int i = 0; i < graph.length(); i++) {
            var call = graph.getJSONArray(i);
            var sourceId = call.getLong(0);
            var targetId = call.getLong(1);
            calls.add(LongLongImmutablePair.of(sourceId, targetId));
        }
        this.calls = calls;
    }

    public Long2ObjectMap<String> getMethods() {
        return methods;
    }

    public Set<LongLongPair> getCalls() {
        return calls;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DynamicJavaCG that = (DynamicJavaCG) o;
        if (!Objects.equals(methods, that.methods)) {
            return false;
        }
        return Objects.equals(calls, that.calls);
    }

    @Override
    public int hashCode() {
        int result = methods != null ? methods.hashCode() : 0;
        result = 31 * result + (calls != null ? calls.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DynamicJavaCG{" +
                "methods=" + methods +
                ", calls=" + calls +
                '}';
    }
}
