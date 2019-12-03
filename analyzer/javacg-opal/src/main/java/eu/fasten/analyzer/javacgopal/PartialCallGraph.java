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
import org.opalj.ai.analyses.cg.CallGraphFactory;
import org.opalj.ai.analyses.cg.ComputedCallGraph;
import org.opalj.ai.analyses.cg.CHACallGraphAlgorithmConfiguration;
import org.opalj.br.Method;
import org.opalj.br.analyses.Project;
import org.opalj.collection.immutable.ConstArray;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

import scala.collection.Iterable;
import scala.collection.JavaConversions;
import scala.collection.Map;



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

    public PartialCallGraph(File file) {
        this.resolvedCalls = new ArrayList<>();
        this.unresolvedCalls = new ArrayList<>();
        this.generatePartialCallGraph(file);
    }

    /**
     * This constructor creates the list of UnresolvedCalls from a list of org.opalj.ai.analyses.cg.UnresolvedMethodCall.
     */
    private void setUnresolvedCalls(List<UnresolvedMethodCall> unresolvedMethodCalls) {
        for (UnresolvedMethodCall unresolvedMethodCall : unresolvedMethodCalls) {
            this.unresolvedCalls.add(new UnresolvedCall(unresolvedMethodCall.caller(),unresolvedMethodCall.pc(),unresolvedMethodCall.calleeClass(),unresolvedMethodCall.calleeName(),unresolvedMethodCall.calleeDescriptor()));
        }
    }

    public void setResolvedCalls(List<ResolvedCall> resolvedCalls) { this.resolvedCalls = resolvedCalls; }

    private void setClassHierarchy(ClassHierarchy classHierarchy) { this.classHierarchy = classHierarchy; }

    List<UnresolvedCall> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    List<ResolvedCall> getResolvedCalls() {
        return resolvedCalls;
    }

    public ClassHierarchy getClassHierarchy() {
        return classHierarchy;
    }

    /**
     * Loads a given file, generates call graph and change the format of calls to (source -> target).
     * @param artifactFile Java file that can be a jar or a folder containing jars.
     * @return A partial graph including ResolvedCalls, UnresolvedCalls and CHA.
     */
    PartialCallGraph generatePartialCallGraph(File artifactFile) {

        Project artifactInOpalFormat = Project.apply(artifactFile);

        ComputedCallGraph callGraphInOpalFormat = CallGraphFactory.create(artifactInOpalFormat,
            JavaToScalaConverter.asScalaFunction0(findEntryPoints(artifactInOpalFormat.allMethodsWithBody())),
            new CHACallGraphAlgorithmConfiguration(artifactInOpalFormat, true));

//        ComputedCallGraph callGraphInOpalFormat = (ComputedCallGraph) AnalysisModeConfigFactory.resetAnalysisMode(artifactInOpalFormat, AnalysisModes.OPA(),false).get(CHACallGraphKey$.MODULE$);

        return toPartialGraph(callGraphInOpalFormat);

    }

    /**
     * Given a call graph in OPAL format returns a call graph in eu.fasten.analyzer.javacgopal.PartialCallGraph format.
     * @param callGraphInOpalFormat Is an object of OPAL ComputedCallGraph.
     * @return eu.fasten.analyzer.javacgopal.PartialCallGraph includes all the calls(as java List) and ClassHierarchy.
     */
    private PartialCallGraph toPartialGraph(ComputedCallGraph callGraphInOpalFormat) {

        callGraphInOpalFormat.callGraph().foreachCallingMethod(JavaToScalaConverter.asScalaFunction2(setResolvedCalls()));

        this.setUnresolvedCalls(new ArrayList<>(JavaConversions.asJavaCollection(callGraphInOpalFormat.unresolvedMethodCalls().toList())));

        this.setClassHierarchy(callGraphInOpalFormat.callGraph().project().classHierarchy());

        return this;
    }

    /**
     * Adds resolved calls of OPAL call graph to resolvedCalls of this object.
     *
     * @return eu.fasten.analyzer.javacgopal.ScalaFunction2 As a fake scala function to be passed to the scala.
     */
    private ScalaFunction2 setResolvedCalls() {
        return (Method callerMethod, Map<Object, Iterable<Method>> calleeMethodsObject) -> {
            Collection<Iterable<Method>> calleeMethodsCollection =
                JavaConversions.asJavaCollection(calleeMethodsObject.valuesIterator().toList());

            List<Method> calleeMethodsList = new ArrayList<>();
            for (Iterable<Method> i : calleeMethodsCollection) {
                for (Method j : JavaConversions.asJavaIterable(i)) {
                    calleeMethodsList.add(j);
                }
            }
            return resolvedCalls.add(new ResolvedCall(callerMethod, calleeMethodsList));

        };
    }

    /**
     * Computes the entrypoints as a pre step of call graph generation.
     * @param allMethods Is all of the methods in an OPAL-loaded project.
     * @return An iterable of entrypoints to be consumed by scala-written OPAL.
     */
    static Iterable<Method> findEntryPoints(ConstArray allMethods) {

        return (Iterable<Method>) allMethods.filter(JavaToScalaConverter.asScalaFunction1((Object method) -> (!((Method) method).isAbstract()) && !((Method) method).isPrivate()));

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
    static RevisionCallGraph createRevisionCallGraph(String forge, MavenCoordinate coordinate, long timestamp, PartialCallGraph partialCallGraph) {

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
    private static ArrayList<FastenURI[]> toURIGraph(PartialCallGraph partialCallGraph) {

        var graph = new ArrayList<FastenURI[]>();

        for (ResolvedCall resolvedCall : partialCallGraph.getResolvedCalls()) {
            var URICalls = resolvedCall.toURICalls();
            if (URICalls.size() != 0) {
                graph.addAll(URICalls);
            }
        }

        for (UnresolvedCall unresolvedCall : partialCallGraph.getUnresolvedCalls()) {
            FastenURI[] URICall = unresolvedCall.toURICall();
            if (URICall[0]!= null && URICall[1] != null){
                graph.add(URICall);
            }
        }

        return graph;
    }

    /**
     * Converts all nodes (entities) of a partial graph to URIs.
     *
     * @return A graph of all nodes in URI format represented in a List of eu.fasten.core.data.FastenURI.
     */
    ArrayList<FastenURI[]> toURIGraph() {
        return toURIGraph(this);
    }


}
