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

import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.core.Call;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartialCallGraph {

    /**
     * List of maven coordinates of dependencies.
     */
    private final MavenCoordinate coordinate;

    /**
     * Calls that their target's packages are not still known and need to be resolved in
     * later on, e.g. in a merge phase.
     */
    private final List<Call> unresolvedCalls;

    /**
     * Calls that their sources and targets are fully resolved.
     */
    private final List<Call> resolvedCalls;

    /**
     * Class hierarchy.
     */
    private final Map<FastenURI, ExtendedRevisionCallGraph.Type> classHierarchy;

    /**
     * Construct a partial call graph with empty lists of resolved / unresolved calls.
     *
     * @param coordinate List of {@link MavenCoordinate}
     */
    public PartialCallGraph(MavenCoordinate coordinate) {
        this.resolvedCalls = new ArrayList<>();
        this.unresolvedCalls = new ArrayList<>();
        this.classHierarchy = new HashMap<>();
        this.coordinate = coordinate;
    }

    public List<Call> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    public List<Call> getResolvedCalls() {
        return resolvedCalls;
    }

    public Map<FastenURI, ExtendedRevisionCallGraph.Type> getClassHierarchy() {
        return classHierarchy;
    }


    /**
     * Add a new call to the list of resolved calls.
     *
     * @param call New call
     */
    public void addResolvedCall(Call call) {
        if (!this.resolvedCalls.contains(call)) {
            this.resolvedCalls.add(call);
        }
    }

    /**
     * Add a new call to the list of unresolved calls.
     *
     * @param call New call
     */
    public void addUnresolvedCall(Call call) {
        if (!this.unresolvedCalls.contains(call)) {
            this.unresolvedCalls.add(call);
        }
    }

    /**
     * Convert a {@link PartialCallGraph} to FASTEN compatible format.
     *
     * @return FASTEN call graph
     */
    public RevisionCallGraph toRevisionCallGraph(long date) {

        List<List<RevisionCallGraph.Dependency>> depArray =
                MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate());


        var graph = toURIGraph();

        return new RevisionCallGraph(
                "mvn",
                coordinate.getProduct(),
                coordinate.getVersionConstraint(),
                date, depArray, graph
        );
    }

    /**
     * Convert a {@link PartialCallGraph} to FASTEN compatible format.
     *
     * @return FASTEN call graph
     */
    public ExtendedRevisionCallGraph toExtendedRevisionCallGraph(long date) {

        List<List<RevisionCallGraph.Dependency>> depArray =
                MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate());


        var graph = toNumericalGraph();

        return new ExtendedRevisionCallGraph(
                "mvn",
                coordinate.getProduct(),
                coordinate.getVersionConstraint(),
                date, depArray, graph, classHierarchy
        );
    }

    /**
     * Converts all nodes {@link Call} of a Wala call graph to URIs.
     *
     * @return A graph of all nodes in URI format represented in a List of {@link FastenURI}
     */
    private ArrayList<FastenURI[]> toURIGraph() {

        var graph = new ArrayList<FastenURI[]>();

        for (Call resolvedCall : resolvedCalls) {
            addCall(graph, resolvedCall);
        }

        for (Call unresolvedCall : unresolvedCalls) {
            addCall(graph, unresolvedCall);
        }

        return graph;
    }

    /**
     * Converts all nodes {@link Call} of a Wala call graph to numbers.
     *
     * @return A graph of all nodes in numerical format represented in a List of {@link FastenURI}
     */
    private ArrayList<FastenURI[]> toNumericalGraph() {

        var graph = new ArrayList<FastenURI[]>();

        for (Call resolvedCall : resolvedCalls) {
            addNumericalCall(graph, resolvedCall);
        }

        for (Call unresolvedCall : unresolvedCalls) {
            addNumericalCall(graph, unresolvedCall);
        }

        return graph;
    }

    /**
     * Add call to a call graph.
     *
     * @param graph Call graph to add a call to
     * @param call  Call to add
     */
    private static void addCall(ArrayList<FastenURI[]> graph, Call call) {

        var uriCall = call.toURICall();

        if (uriCall[0] != null && uriCall[1] != null && !graph.contains(uriCall)) {
            graph.add(uriCall);
        }
    }

    /**
     * Add call numbers to a call graph.
     *
     * @param graph Call graph to add a call to
     * @param call  Call to add
     */
    private void addNumericalCall(ArrayList<FastenURI[]> graph, Call call) {

        var source = call.getSource();
        var target = call.getTarget();
        var type = call.getCallType();

        FastenURI uriSourceType =
                FastenURI.create("/" + source.getPackageName() + "/" + source.getClassName());
        FastenURI uriTargetType =
                FastenURI.create("/" + target.getPackageName() + "/" + target.getClassName());

        var sourceData = this.classHierarchy.get(uriSourceType);
        var targetData = this.classHierarchy.get(uriTargetType);

        var sourceIndex = sourceData.getMethods().get(source.toCanonicalSchemalessURI());
        var targetIndex = targetData.getMethods().get(target.toCanonicalSchemalessURI());

        if (sourceIndex != null && targetIndex != null) {
            var result = new FastenURI[3];
            result[0] = FastenURI.create(String.valueOf(sourceIndex));
            result[1] = FastenURI.create(String.valueOf(targetIndex));
            result[2] = FastenURI.create(type.label);
            graph.add(result);
        }
    }
}
