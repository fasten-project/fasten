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
import org.opalj.ai.analyses.cg.CallGraphFactory;
import org.opalj.ai.analyses.cg.ComputedCallGraph;
import org.opalj.ai.analyses.cg.CHACallGraphAlgorithmConfiguration;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.br.analyses.Project;
import org.opalj.collection.immutable.Chain;
import org.opalj.collection.immutable.ConstArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import scala.collection.Iterable;
import scala.collection.JavaConversions;

/**
 * Call graphs that are not still fully resolved.
 * e.g. isolated call graphs which within-artifact calls (edges) are known as resolved calls and
 * Cross-artifact calls are known as unresolved calls.
 */
public class PartialCallGraph {

    private static Logger logger = LoggerFactory.getLogger(PartialCallGraph.class);

    /**
     * Calls that their target's packages are not still known and need to be resolved in later on, e.g. in a merge phase.
     */
    private List<UnresolvedCall> unresolvedCalls;
    /**
     * Calls that their sources and targets are fully resolved.
     */
    private List<ResolvedCall> resolvedCalls;
    /**
     * ClassHierarchy of the under investigation artifact.
     * For every class in the form of org.opalj.br.ObjectType it specify a single eu.fasten.analyzer.javacgopal.Type.
     */
    private Map<ObjectType, Type> classHierarchy;

    public PartialCallGraph(List<UnresolvedCall> unresolvedCalls, List<ResolvedCall> ResolvedCalls, Map<ObjectType, Type> classHierarchy) {
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
        this.classHierarchy = new HashMap<>();
        this.generatePartialCallGraph(file);
    }

    /**
     * This constructor creates the list of UnresolvedCalls from a list of org.opalj.ai.analyses.cg.UnresolvedMethodCall.
     */
    private void setUnresolvedCalls(List<UnresolvedMethodCall> unresolvedMethodCalls) {
        for (UnresolvedMethodCall unresolvedMethodCall : unresolvedMethodCalls) {
            this.unresolvedCalls.add(new UnresolvedCall(unresolvedMethodCall.caller(), unresolvedMethodCall.pc(), unresolvedMethodCall.calleeClass(), unresolvedMethodCall.calleeName(), unresolvedMethodCall.calleeDescriptor()));
        }
    }

    public void setResolvedCalls(List<ResolvedCall> resolvedCalls) {
        this.resolvedCalls = resolvedCalls;
    }

    /**
     * Sets classHierarchy of the current call graph.
     * Note: For the classes that are inside the under investigation artifact methods are also known,
     * so we set them in the classHierarchy.
     *
     * @param artifactInOpalFormat org.opalj.br.analyses.Project.
     */
    void setClassHierarchy(Project artifactInOpalFormat) {

        Map<ObjectType, List<Method>> allMethods = new HashMap<>();
        artifactInOpalFormat.allMethodsWithBody().foreach((JavaToScalaConverter.asScalaFunction1(
            (Object method) -> putMethod(allMethods, (Method) method)))
        );

        Set<ObjectType> currentArtifactClasses = new HashSet<>(allMethods.keySet());
        Set<ObjectType> libraryClasses = new HashSet<>(JavaConversions.mapAsJavaMap(artifactInOpalFormat.classHierarchy().supertypes()).keySet());
        libraryClasses.removeAll(currentArtifactClasses);

        for (ObjectType currentClass : currentArtifactClasses) {
            Type type = new Type();
            type.setSupers(artifactInOpalFormat.classHierarchy(), currentClass);
            type.methods.addAll(allMethods.get(currentClass));
            this.classHierarchy.put(currentClass, type);
        }

//        for (ObjectType libraryClass : libraryClasses) {
//            Type type = new Type();
//            type.setSupers(artifactInOpalFormat.classHierarchy(), libraryClass);
//            this.classHierarchy.put(libraryClass, type);
//        }

    }

    /**
     * Adds an org.opalj.br.Method to the passed Map.
     *
     * @param methods a map of methods inside each class.
     *                Keys are org.opalj.ObjectType and values are a list of org.opalj.br.Method declared inside of that class.
     * @param method  the org.opalj.br.Method to be added.
     * @return if the value is successfully added returns true otherwise false.
     */
    private Boolean putMethod(Map<ObjectType, List<Method>> methods, Method method) {

        var currentClass = method.declaringClassFile().thisType();

        List<Method> currentClassMethods = methods.get(currentClass);

        try {
            if (currentClassMethods == null) {
                currentClassMethods = new ArrayList<Method>();
                currentClassMethods.add(method);
            } else {
                if (!currentClassMethods.contains(method)) {
                    currentClassMethods.add(method);
                }
            }
            methods.put(currentClass, currentClassMethods);
            return true;

        } catch (Exception e) {
            logger.error("Couldn't add the method {} to the list of methods of the class:{} ", method, currentClass, e);
            return false;
        }
    }

    List<UnresolvedCall> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    List<ResolvedCall> getResolvedCalls() {
        return resolvedCalls;
    }

    public Map<ObjectType, Type> getClassHierarchy() {
        return classHierarchy;
    }

    /**
     * Loads a given file, generates call graph and change the format of calls to (source -> target).
     *
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
     *
     * @param callGraphInOpalFormat Is an object of OPAL ComputedCallGraph.
     * @return eu.fasten.analyzer.javacgopal.PartialCallGraph includes all the calls(as java List) and ClassHierarchy.
     */
    private PartialCallGraph toPartialGraph(ComputedCallGraph callGraphInOpalFormat) {

        callGraphInOpalFormat.callGraph().foreachCallingMethod(JavaToScalaConverter.asScalaFunction2(setResolvedCalls()));

        this.setUnresolvedCalls(new ArrayList<>(JavaConversions.asJavaCollection(callGraphInOpalFormat.unresolvedMethodCalls().toList())));

        this.setClassHierarchy(callGraphInOpalFormat.callGraph().project());

        return this;
    }

    /**
     * Adds resolved calls of OPAL call graph to resolvedCalls of this object.
     *
     * @return eu.fasten.analyzer.javacgopal.ScalaFunction2 As a fake scala function to be passed to the scala.
     */
    private ScalaFunction2 setResolvedCalls() {
        return (Method callerMethod, scala.collection.Map<Object, Iterable<Method>> calleeMethodsObject) -> {
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
     *
     * @param allMethods Is all of the methods in an OPAL-loaded project.
     * @return An iterable of entrypoints to be consumed by scala-written OPAL.
     */
    static Iterable<Method> findEntryPoints(ConstArray allMethods) {

        return (Iterable<Method>) allMethods.filter(JavaToScalaConverter.asScalaFunction1((Object method) -> (!((Method) method).isAbstract()) && !((Method) method).isPrivate()));

    }

    /**
     * Creates the call graph in a FASTEN supported format from a eu.fasten.analyzer.javacgopal.PartialCallGraph.
     *
     * @param forge            The forge of the under investigation artifact. e.g. assuming we are making revision call graph of
     *                         "com.google.guava:guava:jar:28.1-jre", this artifact exists on maven repository so the forge is mvn.
     * @param coordinate       Maven coordinate of the artifact in the format of groupId:ArtifactId:version.
     * @param timestamp        The timestamp (in seconds from UNIX epoch) in which the artifact is released on the forge.
     * @param partialCallGraph A partial call graph of artifact including resolved calls, unresolved calls (calls without specified product) and CHA.
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

    static ProposalRevisionCallGraph createProposalRevisionCallGraph(String forge, MavenCoordinate coordinate, long timestamp, PartialCallGraph partialCallGraph) {

        return new ProposalRevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.versionConstraint,
            timestamp,
            MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            partialCallGraph.toURIGraph(),
            toURIHierarchy(partialCallGraph.classHierarchy));
    }


    /**
     * Converts all of the members of the classHierarchy to FastenURIs.
     *
     * @param classHierarchy Map<org.obalj.br.ObjectType, eu.fasten.analyzer.javacgopal.Type>
     * @return Map<eu.fasten.core.data.FastenURI, eu.fasten.analyzer.javacgopal.ProposalRevisionCallGraph.Type>
     */
    static Map<FastenURI, ProposalRevisionCallGraph.Type> toURIHierarchy(Map<ObjectType, Type> classHierarchy) {

        Map<FastenURI, ProposalRevisionCallGraph.Type> URIclassHierarchy = new HashMap<>();

        for (ObjectType aClass : classHierarchy.keySet()) {

            var type = classHierarchy.get(aClass);

            URIclassHierarchy.put(
                OPALMethodAnalyzer.getTypeURI(aClass),
                new ProposalRevisionCallGraph.Type(
                    toURIMethods(type.methods),
                    toURIClasses(type.superClasses),
                    toURIInterfaces(type.superInterfaces)
                )
            );
        }
        return URIclassHierarchy;
    }

    /**
     * Converts a list of interfaces to a list of FastenURIs.
     *
     * @param interfaces A list of org.obalj.br.ObjectType
     * @return A list of eu.fasten.core.data.FastenURI.
     */
    static List<FastenURI> toURIInterfaces(List<ObjectType> interfaces) {
        List<FastenURI> classURIs = new ArrayList<>();
        for (ObjectType aClass : interfaces) {
            classURIs.add(OPALMethodAnalyzer.getTypeURI(aClass));
        }
        return classURIs;
    }

    /**
     * Converts a list of classes to a list of FastenURIs.
     *
     * @param classes A list of org.opalj.collection.immutable.Chain<org.obalj.br.ObjectType>
     * @return A list of eu.fasten.core.data.FastenURI.
     */
    static LinkedList<FastenURI> toURIClasses(Chain<ObjectType> classes) {
        LinkedList<FastenURI> classURIs = new LinkedList<>();
        classes.foreach( JavaToScalaConverter.asScalaFunction1(aClass ->
            classURIs.add(OPALMethodAnalyzer.getTypeURI((ObjectType) aClass)))
        );
        return classURIs;
    }

    /**
     * Converts a list of methods to a list of FastenURIs.
     *
     * @param methods A list of org.obalj.br.Method
     * @return A list of eu.fasten.core.data.FastenURIs.
     */
    static List<FastenURI> toURIMethods(List<Method> methods) {
        List<FastenURI> methodsURIs = new ArrayList<>();
        for (Method method : methods) {
            methodsURIs.add(
                OPALMethodAnalyzer.toCanonicalSchemelessURI(
                    null,
                    method.declaringClassFile().thisType(),
                    method.name(),
                    method.descriptor()
                )
            );
        }
        return methodsURIs;
    }

    /**
     * Converts all nodes (entities) of a partial graph to URIs.
     *
     * @param partialCallGraph Partial graph with org.opalj.br.Method nodes.
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
            if (URICall[0] != null && URICall[1] != null) {
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
