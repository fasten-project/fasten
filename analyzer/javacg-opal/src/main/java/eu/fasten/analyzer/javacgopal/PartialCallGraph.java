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

package eu.fasten.analyzer.javacgopal;

import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.data.FastenURI;

import org.opalj.ai.analyses.cg.UnresolvedMethodCall;
import org.opalj.br.ClassHierarchy;

import java.util.ArrayList;
import java.util.List;

/**
 * Call graphs that are not still fully resolved.
 * e.g. isolated call graphs which within-artifact calls (edges) are known as resolved calls and
 * Cross-artifact calls are known as unresolved calls.
 */
public class PartialCallGraph {
    /**
     * Calls that their target's packages are not still known and need to be resolved in later on, e.g. in a merge phase.
     */
    private List<UnresolvedCall> unresolvedCalls;
    /**
     * Calls that their sources and targets are fully resolved.
     */
    private List<ResolvedCall> resolvedCalls;
    /**
     * ClassHierarchy in OPAL format.
     */
    private ClassHierarchy classHierarchy;

    public PartialCallGraph(List<UnresolvedCall> unresolvedCalls, List<ResolvedCall> ResolvedCalls, ClassHierarchy classHierarchy) {
        this.unresolvedCalls = unresolvedCalls;
        this.resolvedCalls = ResolvedCalls;
        this.classHierarchy = classHierarchy;
    }

    /**
     * Using this constructor it is possible to directly retrieve calls in eu.fasten.analyzer.javacgopal.PartialCallGraph.
     * e.g. add edges to resolved calls one by one when scala is being used.
     */
    public PartialCallGraph() {
        this.resolvedCalls = new ArrayList<>();
        this.unresolvedCalls = new ArrayList<>();
    }

    /**
     * This constructor creates the list of UnresolvedCalls from a list of org.opalj.ai.analyses.cg.UnresolvedMethodCall.
     */
    public void setUnresolvedCalls(List<UnresolvedMethodCall> unresolvedMethodCalls) {
        for (UnresolvedMethodCall unresolvedMethodCall : unresolvedMethodCalls) {
            this.unresolvedCalls.add(new UnresolvedCall(unresolvedMethodCall.caller(),unresolvedMethodCall.pc(),unresolvedMethodCall.calleeClass(),unresolvedMethodCall.calleeName(),unresolvedMethodCall.calleeDescriptor()));
        }
    }

    public void setResolvedCalls(List<ResolvedCall> resolvedCalls) { this.resolvedCalls = resolvedCalls; }

    public void setClassHierarchy(ClassHierarchy classHierarchy) { this.classHierarchy = classHierarchy; }

    public List<UnresolvedCall> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    public List<ResolvedCall> getResolvedCalls() {
        return resolvedCalls;
    }

    public ClassHierarchy getClassHierarchy() {
        return classHierarchy;
    }

    /**
     *  Creates the call graph in a FASTEN supported format from a eu.fasten.analyzer.javacgopal.PartialCallGraph.
     *
     * @param forge The forge of the under investigation artifact. e.g. assuming we are making revision call graph of
     *              "com.google.guava:guava:jar:28.1-jre", this artifact exists on maven repository so the forge is mvn.
     * @param coordinate Maven coordinate of the artifact in the format of groupId:ArtifactId:version.
     * @param timestamp The timestamp (in seconds from UNIX epoch) in which the artifact is released on the forge.
     * @param partialCallGraph A partial call graph of artifact including resolved calls, unresolved calls (calls without specified product) and CHA.
     *
     * @return The given revision's call graph in FASTEN format. All nodes are in URI format.
     */
    public static RevisionCallGraph createRevisionCallGraph(String forge, MavenCoordinate coordinate, long timestamp, PartialCallGraph partialCallGraph) {

        return new RevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.versionConstraint,
            timestamp,
            MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            partialCallGraph.toURIGraph());
    }

    /**
     * Converts all nodes (entities) of a partial graph to URIs.
     *
     * @param partialCallGraph Partial graph with org.opalj.br.Method nodes.
     *
     * @return A graph of all nodes in URI format represented in a List of eu.fasten.core.data.FastenURIs.
     */
    public static ArrayList<FastenURI[]> toURIGraph(PartialCallGraph partialCallGraph) {

        var graph = new ArrayList<FastenURI[]>();

        for (ResolvedCall resolvedCall : partialCallGraph.getResolvedCalls()) {
            graph.addAll(resolvedCall.toUIRCalls());
        }

        for (UnresolvedCall unresolvedCall : partialCallGraph.getUnresolvedCalls()) {
            graph.add(unresolvedCall.toURICalls());
        }

        return graph;
    }

    /**
     * Converts all nodes (entities) of a partial graph to URIs.
     *
     * @return A graph of all nodes in URI format represented in a List of eu.fasten.core.data.FastenURI.
     */
    public ArrayList<FastenURI[]> toURIGraph() {
        return toURIGraph(this);
    }

}
