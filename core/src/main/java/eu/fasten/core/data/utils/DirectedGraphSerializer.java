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

import eu.fasten.core.data.DirectedGraph;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.util.Map;
import java.util.Set;

public class DirectedGraphSerializer {

    public String graphToJson(DirectedGraph graph, Map<Long, String> idToUriMap) {
        var builder = new StringBuilder("{");
        builder.append("\"nodes\":{");
        appendNodes(builder, graph.nodes(), idToUriMap);
        builder.append("},\"edges\":[");
        appendEdges(builder, graph.edgeSet());
        builder.append("]}");
        return builder.toString();
    }

    private void appendEdges(StringBuilder builder, Set<LongLongPair> edges) {
        edges.forEach(e -> appendEdge(builder, e));
        removeLastIfNotEmpty(builder, edges.size());
    }

    private void appendEdge(StringBuilder builder, LongLongPair edge) {
        builder.append("[").append(edge.firstLong()).append(",").append(edge.secondLong()).append("],");
    }

    private void appendNodes(StringBuilder builder, Set<Long> nodes, Map<Long, String> idToUriMap) {
        nodes.forEach(n -> appendKeyValuePair(builder, String.valueOf(n), idToUriMap.get(n)));
        removeLastIfNotEmpty(builder, nodes.size());
    }

    private void appendKeyValuePair(StringBuilder builder, String key, String value) {
        builder.append(quote(key)).append(":").append(quote(value)).append(",");
    }

    private String quote(final String s) {
        return "\"" + s + "\"";
    }

    private void removeLastIfNotEmpty(StringBuilder builder, int size) {
        if (size != 0) {
            builder.setLength(builder.length() - 1);
        }
    }
}
