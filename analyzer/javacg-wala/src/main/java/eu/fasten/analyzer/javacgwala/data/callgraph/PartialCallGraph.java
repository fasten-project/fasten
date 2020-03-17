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

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.core.CallType;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.FastenURI;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class PartialCallGraph {

    private final List<List<Integer>> internalCalls;

    private final Map<Pair<Integer, FastenURI>, Map<String, String>> externalCalls;

    private final Map<FastenURI, ExtendedRevisionCallGraph.Type> classHierarchy;

    /**
     * Construct a partial call graph with empty lists of resolved / unresolved calls.
     */
    public PartialCallGraph() {
        this.internalCalls = new ArrayList<>();
        this.externalCalls = new HashMap<>();
        this.classHierarchy = new HashMap<>();
    }

    public Map<FastenURI, ExtendedRevisionCallGraph.Type> getClassHierarchy() {
        return classHierarchy;
    }

    public ExtendedRevisionCallGraph.Graph getGraph() {
        return new ExtendedRevisionCallGraph.Graph(internalCalls, externalCalls);
    }

    public List<List<Integer>> getInternalCalls() {
        return internalCalls;
    }

    public Map<Pair<Integer, FastenURI>, Map<String, String>> getExternalCalls() {
        return externalCalls;
    }

    /**
     * Add a new call to the list of resolved calls.
     *
     * @param caller Source method
     * @param callee Target method
     */
    public void addInternalCall(final int caller, final int callee) {
        List<Integer> call = new ArrayList<>();
        call.add(caller);
        call.add(callee);
        if (internalCalls.contains(call)) {
            return;
        }
        this.internalCalls.add(call);
    }

    /**
     * Add a new call to the list of unresolved calls.
     *
     * @param caller   Source method
     * @param callee   Target method
     * @param callType Call type
     */
    public void addExternalCall(final int caller, final FastenURI callee,
                                final CallType callType) {
        final var call = new ImmutablePair<>(caller, callee);
        final var previousCallMetadata = this.getExternalCalls().get(call);
        int count = 1;

        if (previousCallMetadata != null) {
            count += Integer.parseInt(previousCallMetadata.get(callType.label));
            previousCallMetadata.put(callType.label, String.valueOf(count));
        } else {
            final var metadata = new HashMap<String, String>();
            metadata.put(callType.label, String.valueOf(count));
            this.externalCalls.put(call, metadata);
        }
    }

    /**
     * Creates {@link ExtendedRevisionCallGraph} using WALA call graph generator for a given maven
     * coordinate. It also sets the forge to "mvn".
     *
     * @param coordinate maven coordinate of the revision to be processed.
     * @param timestamp  timestamp of the revision release.
     * @return {@link ExtendedRevisionCallGraph} of the given coordinate.
     * @throws FileNotFoundException in case there is no jar file for the given coordinate on the
     *                               Maven central it throws this exception.
     */
    public static ExtendedRevisionCallGraph createExtendedRevisionCallGraph(
            final MavenCoordinate coordinate,
            final long timestamp)
            throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {

        final var partialCallGraph = CallGraphConstructor.build(coordinate);

        return new ExtendedRevisionCallGraph("mvn", coordinate.getProduct(),
                coordinate.getVersionConstraint(), timestamp, "WALA",
                MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
                partialCallGraph.getClassHierarchy(),
                partialCallGraph.getGraph());
    }
}
