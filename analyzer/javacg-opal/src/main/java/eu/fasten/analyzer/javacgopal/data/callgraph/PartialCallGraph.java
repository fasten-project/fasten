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

package eu.fasten.analyzer.javacgopal.data.callgraph;

import eu.fasten.analyzer.javacgopal.data.Method;
import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.ResolvedCall;
import eu.fasten.analyzer.javacgopal.data.Type;
import eu.fasten.analyzer.javacgopal.data.UnresolvedCall;
import eu.fasten.analyzer.javacgopal.scalawrapper.JavaToScalaConverter;
import eu.fasten.analyzer.javacgopal.scalawrapper.ScalaFunction2;
import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.data.FastenURI;

import java.io.File;
import java.util.*;

import org.opalj.ai.analyses.cg.UnresolvedMethodCall;
import org.opalj.ai.analyses.cg.CallGraphFactory;
import org.opalj.ai.analyses.cg.ComputedCallGraph;
import org.opalj.ai.analyses.cg.CHACallGraphAlgorithmConfiguration;
import org.opalj.br.ObjectType;
import org.opalj.br.analyses.Project;
import org.opalj.collection.immutable.Chain;
import org.opalj.collection.immutable.ConstArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * For every class in the form of org.opalj.br.ObjectType it specify a single eu.fasten.analyzer.javacgopal.data.Type.
     */
    private Map<ObjectType, Type> classHierarchy;

    public PartialCallGraph(List<UnresolvedCall> unresolvedCalls, List<ResolvedCall> ResolvedCalls, Map<ObjectType, Type> classHierarchy) {
        this.unresolvedCalls = unresolvedCalls;
        this.resolvedCalls = ResolvedCalls;
        this.classHierarchy = classHierarchy;
    }

    /**
     * Using this constructor it is possible to directly retrieve calls in eu.fasten.analyzer.javacgopal.graph.PartialCallGraph.
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
    public void setUnresolvedCalls(List<UnresolvedMethodCall> unresolvedMethodCalls) {
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
    public void setClassHierarchy(Project artifactInOpalFormat) {

        Map<ObjectType, List<org.opalj.br.Method>> allMethods = new HashMap<>();
        artifactInOpalFormat.allMethodsWithBody().foreach((JavaToScalaConverter.asScalaFunction1(
            (Object method) -> putMethod(allMethods, (org.opalj.br.Method) method)))
        );

        Set<ObjectType> currentArtifactClasses = new HashSet<>(allMethods.keySet());
        Set<ObjectType> libraryClasses = new HashSet<>(JavaConversions.mapAsJavaMap(artifactInOpalFormat.classHierarchy().supertypes()).keySet());
        libraryClasses.removeAll(currentArtifactClasses);

        for (ObjectType currentClass : currentArtifactClasses) {
            Type type = new Type();
            type.setSupers(artifactInOpalFormat.classHierarchy(), currentClass);
            type.getMethods().addAll(allMethods.get(currentClass));
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
    public Boolean putMethod(Map<ObjectType, List<org.opalj.br.Method>> methods, org.opalj.br.Method method) {

        var currentClass = method.declaringClassFile().thisType();

        List<org.opalj.br.Method> currentClassMethods = methods.get(currentClass);

        try {
            if (currentClassMethods == null) {
                currentClassMethods = new ArrayList<>();
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

    public List<UnresolvedCall> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    public List<ResolvedCall> getResolvedCalls() {
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
    public PartialCallGraph generatePartialCallGraph(File artifactFile) {

        Project artifactInOpalFormat = Project.apply(artifactFile);

        ComputedCallGraph callGraphInOpalFormat = CallGraphFactory.create(artifactInOpalFormat,
            JavaToScalaConverter.asScalaFunction0(findEntryPoints(artifactInOpalFormat.allMethodsWithBody())),
            new CHACallGraphAlgorithmConfiguration(artifactInOpalFormat, true));

//        ComputedCallGraph callGraphInOpalFormat = (ComputedCallGraph) AnalysisModeConfigFactory.resetAnalysisMode(artifactInOpalFormat, AnalysisModes.OPA(),false).get(CHACallGraphKey$.MODULE$);

        return toPartialGraph(callGraphInOpalFormat);

    }

    /**
     * Given a call graph in OPAL format returns a call graph in eu.fasten.analyzer.javacgopal.graph.PartialCallGraph format.
     *
     * @param callGraphInOpalFormat Is an object of OPAL ComputedCallGraph.
     * @return eu.fasten.analyzer.javacgopal.graph.PartialCallGraph includes all the calls(as java List) and ClassHierarchy.
     */
    public PartialCallGraph toPartialGraph(ComputedCallGraph callGraphInOpalFormat) {

        callGraphInOpalFormat.callGraph().foreachCallingMethod(JavaToScalaConverter.asScalaFunction2(setResolvedCalls()));

        this.setUnresolvedCalls(new ArrayList<>(JavaConversions.asJavaCollection(callGraphInOpalFormat.unresolvedMethodCalls().toList())));

        this.setClassHierarchy(callGraphInOpalFormat.callGraph().project());

        return this;
    }

    /**
     * Adds resolved calls of OPAL call graph to resolvedCalls of this object.
     *
     * @return eu.fasten.analyzer.javacgopal.scalawrapper.ScalaFunction2 As a fake scala function to be passed to the scala.
     */
    public ScalaFunction2 setResolvedCalls() {
        return (org.opalj.br.Method callerMethod, scala.collection.Map<Object, Iterable<org.opalj.br.Method>> calleeMethodsObject) -> {
            Collection<Iterable<org.opalj.br.Method>> calleeMethodsCollection =
                JavaConversions.asJavaCollection(calleeMethodsObject.valuesIterator().toList());

            List<org.opalj.br.Method> calleeMethodsList = new ArrayList<>();
            for (Iterable<org.opalj.br.Method> i : calleeMethodsCollection) {
                for (org.opalj.br.Method j : JavaConversions.asJavaIterable(i)) {
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
    public static Iterable<org.opalj.br.Method> findEntryPoints(ConstArray allMethods) {

        return (Iterable<org.opalj.br.Method>) allMethods.filter(JavaToScalaConverter.asScalaFunction1((Object method) -> (!((org.opalj.br.Method) method).isAbstract()) && !((org.opalj.br.Method) method).isPrivate()));

    }

    /**
     * Creates the call graph in a FASTEN supported format from a eu.fasten.analyzer.javacgopal.graph.PartialCallGraph.
     *
     * @param forge            The forge of the under investigation artifact. e.g. assuming we are making revision call graph of
     *                         "com.google.guava:guava:jar:28.1-jre", this artifact exists on maven repository so the forge is mvn.
     * @param coordinate       Maven coordinate of the artifact in the format of groupId:ArtifactId:version.
     * @param timestamp        The timestamp (in seconds from UNIX epoch) in which the artifact is released on the forge.
     * @param partialCallGraph A partial call graph of artifact including resolved calls, unresolved calls (calls without specified product) and CHA.
     * @return The given revision's call graph in FASTEN format. All nodes are in URI format.
     */
    public static RevisionCallGraph createRevisionCallGraph(String forge, MavenCoordinate coordinate, long timestamp, PartialCallGraph partialCallGraph) {

        return new RevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.getVersionConstraint(),
            timestamp,
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            partialCallGraph.toURIGraph());
    }



    /**
     * Converts all of the members of the classHierarchy to FastenURIs.
     *
     * @param classHierarchy Map<org.obalj.br.ObjectType, eu.fasten.analyzer.javacgopal.data.Type>
     * @return Map<eu.fasten.core.data.FastenURI, eu.fasten.analyzer.javacgopal.graph.ExtendedRevisionCallGraph.Type>
     */
    public static Map<FastenURI, ExtendedRevisionCallGraph.Type> toURIHierarchy(Map<ObjectType, Type> classHierarchy) {

        Map<FastenURI, ExtendedRevisionCallGraph.Type> URIclassHierarchy = new HashMap<>();

        for (ObjectType aClass : classHierarchy.keySet()) {

            var type = classHierarchy.get(aClass);

            URIclassHierarchy.put(
                Method.getTypeURI(aClass),
                new ExtendedRevisionCallGraph.Type(
                    toURIMethods(type.getMethods()),
                    toURIClasses(type.getSuperClasses()),
                    toURIInterfaces(type.getSuperInterfaces())
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
    public static List<FastenURI> toURIInterfaces(List<ObjectType> interfaces) {
        List<FastenURI> classURIs = new ArrayList<>();
        for (ObjectType aClass : interfaces) {
            classURIs.add(Method.getTypeURI(aClass));
        }
        return classURIs;
    }

    /**
     * Converts a list of classes to a list of FastenURIs.
     *
     * @param classes A list of org.opalj.collection.immutable.Chain<org.obalj.br.ObjectType>
     * @return A list of eu.fasten.core.data.FastenURI.
     */
    public static LinkedList<FastenURI> toURIClasses(Chain<ObjectType> classes) {
        LinkedList<FastenURI> classURIs = new LinkedList<>();
        classes.foreach(JavaToScalaConverter.asScalaFunction1(aClass ->
            classURIs.add(Method.getTypeURI((ObjectType) aClass)))
        );
        return classURIs;
    }

    /**
     * Converts a list of methods to a list of FastenURIs.
     *
     * @param methods A list of org.obalj.br.Method
     * @return A list of eu.fasten.core.data.FastenURIs.
     */
    public static List<FastenURI> toURIMethods(List<org.opalj.br.Method> methods) {
        List<FastenURI> methodsURIs = new ArrayList<>();
        for (org.opalj.br.Method method : methods) {
            methodsURIs.add(
                Method.toCanonicalSchemelessURI(
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
    public static ArrayList<FastenURI[]> toURIGraph(PartialCallGraph partialCallGraph) {

        var graph = new ArrayList<FastenURI[]>();

        for (ResolvedCall resolvedCall : partialCallGraph.removeDuplicateResolvedCall()) {
            var URICalls = resolvedCall.toURICalls();

            if (URICalls.size() != 0) {
                graph.addAll(URICalls);
            }
        }

        for (UnresolvedCall unresolvedCall : partialCallGraph.removeDuplicateUnresolvedCall()) {
            FastenURI[] URICall = unresolvedCall.toURICall();
            if (URICall[0] != null && URICall[1] != null && !graph.contains(URICall)) {

                graph.add(URICall);
            }
        }

        return graph;
    }

    /**
     * Removes duplicate unresolved calls generated by OPAL from this object.
     *
     * @return unique unresolved calls of this object.
     */
    private List<UnresolvedCall> removeDuplicateUnresolvedCall() {

        List<UnresolvedCall> distincedArcs = new ArrayList<>();

        for (UnresolvedCall element : this.getUnresolvedCalls()) {

            if (!containsCall(distincedArcs, element)) {
                distincedArcs.add(element);
            }
        }

        this.unresolvedCalls.clear();
        this.unresolvedCalls = distincedArcs;

        return this.unresolvedCalls;

    }

    private boolean containsCall(List<UnresolvedCall> newList, UnresolvedCall element) {

        for (UnresolvedCall unresolvedCall : newList) {

            if (unresolvedCall.calleeClass() == element.calleeClass()
                && unresolvedCall.calleeName() == element.calleeName()
                && unresolvedCall.calleeDescriptor() == element.calleeDescriptor()
                && unresolvedCall.caller() == element.caller()) {

                return true;

            }
        }
        return false;
    }

    /**
     * Removes duplicate resolved calls generated by OPAL from this object.
     *
     * @return unique resolved calls of this object.
     */
    private List<ResolvedCall> removeDuplicateResolvedCall() {

        for (ResolvedCall currentMethod : this.resolvedCalls) {
            Set<org.opalj.br.Method> targets = new HashSet<>(currentMethod.getTargets());
            currentMethod.clearTargets();
            currentMethod.setTargets(new ArrayList<>(targets));
        }

        return this.resolvedCalls;

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
