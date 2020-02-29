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

package eu.fasten.analyzer.javacgwala.data.callgraph;

import eu.fasten.analyzer.javacgwala.data.core.CallType;
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class PartialCallGraph {

    /**
     * Calls that their sources and targets are fully resolved.
     */
    private final List<int[]> resolvedCalls;

    /**
     * Calls that their target's packages are not still known and need to be resolved in
     * later on, e.g. in a merge phase.
     */
    private final Map<Pair<Integer, FastenURI>, Map<String, String>> unresolvedCalls;

    /**
     * Class hierarchy.
     */
    private final Map<FastenURI, ExtendedRevisionCallGraph.Type> classHierarchy;

    /**
     * Construct a partial call graph with empty lists of resolved / unresolved calls.
     */
    public PartialCallGraph() {
        this.resolvedCalls = new ArrayList<>();
        this.unresolvedCalls = new HashMap<>();
        this.classHierarchy = new HashMap<>();
    }

    public Map<FastenURI, ExtendedRevisionCallGraph.Type> getClassHierarchy() {
        return classHierarchy;
    }

    public ExtendedRevisionCallGraph.Graph getGraph() {
        return new ExtendedRevisionCallGraph.Graph(resolvedCalls, unresolvedCalls);
    }

    public List<int[]> getResolvedCalls() {
        return resolvedCalls;
    }

    public Map<Pair<Integer, FastenURI>, Map<String, String>> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    /**
     * Add a new call to the list of resolved calls.
     *
     * @param caller Source method
     * @param callee Target method
     */
    public void addResolvedCall(final int caller, final int callee) {
        for (final int[] item : resolvedCalls) {
            if (Arrays.equals(item, new int[]{caller, callee})) {
                return;
            }
        }
        this.resolvedCalls.add(new int[]{caller, callee});
    }

    /**
     * Add a new call to the list of unresolved calls.
     *
     * @param caller   Source method
     * @param callee   Target method
     * @param callType Call type
     */
    public void addUnresolvedCall(final int caller, final FastenURI callee,
                                  final CallType callType) {
        final var call = new ImmutablePair<>(caller, callee);
        final var previousCallMetadata = this.getUnresolvedCalls().get(call);
        int count = 1;

        if (previousCallMetadata != null) {
            count += Integer.parseInt(previousCallMetadata.get(callType.label));
            previousCallMetadata.put(callType.label, String.valueOf(count));
        } else {
            final var metadata = new HashMap<String, String>();
            metadata.put(callType.label, String.valueOf(count));
            this.unresolvedCalls.put(call, metadata);
        }
    }
}
