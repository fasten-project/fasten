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

import com.google.common.collect.Lists;
import eu.fasten.analyzer.javacgopal.data.Method;
import eu.fasten.analyzer.javacgopal.data.Type;
import eu.fasten.analyzer.javacgopal.scalawrapper.JavaToScalaConverter;
import eu.fasten.core.data.FastenURI;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opalj.ai.analyses.cg.*;
import org.opalj.br.*;
import org.opalj.br.analyses.Project;
import org.opalj.collection.immutable.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.collection.Iterable;
import scala.collection.JavaConverters;


/** Call graphs that are not still fully resolved.
 * i.e. isolated call graphs which within-artifact calls (edges) are known as resolved calls and
 * Cross-artifact calls are known as unresolved calls. */
public class PartialCallGraph {

    private static Logger logger = LoggerFactory.getLogger(PartialCallGraph.class);

    private final String GENERATOR = "OPAL";

    /** Class hierarchy of this call graph, keys are {@link FastenURI} of classes
     *  of the artifact and values are {@link ExtendedRevisionCallGraph.Type}. */
    private final Map<FastenURI, ExtendedRevisionCallGraph.Type> classHierarchy;

    /** Resolved calls of the call graph, each element is an int[] that the first element
     *  is the id of source method and the second one is the id of the target method.
     *  Ids are available in the class hierarchy. */
    private final List<int[]> resolvedCalls;

    /** Unresolved calls of the call graph. Each key of the map is an unresolved call.
     *  Keys are {@link Pair} of Integers that indicate the source methods's id (available in the CHA)
     *  and a {@link FastenURI} of the target method. Values are a map between JVM call types and
     *  number of each call type for the corresponding edge */
    private final Map<Pair<Integer, FastenURI>, Map<String, Integer>> unresolvedCalls;

    public PartialCallGraph(final File file) {
        logger.info("Generating call graph using OPAL ...");
        final var cg = generatePartialCallGraph(file);
        logger.info("OPAL generated the call graph, creating the CHA ...");
        final var cha = createCHA(cg);
        logger.info("CHA is created, setting resolved calls ...");
        this.resolvedCalls = getResolvedCalls(cg, cha);
        logger.info("Resolved calls are set, setting unresolved calls ...");
        this.unresolvedCalls = getUnresolvedCalls(cg, cha);
        logger.info("Unresolved calls are set, converting CHA entities to URIs ...");
        this.classHierarchy = asURIHierarchy(cha);
        logger.info("CHA entities has been converted to URIs.");
    }

    /** It creates a class hierarchy for the given call graph's artifact.
     *
     * @param cg {@link ComputedCallGraph}
     * @return A Map of {@link ObjectType} and created {@link Type} for it.
     * @implNote Inside {@link Type} all of the methods are indexed, it means one can use the ids
     * assigned to each method instead of the method itself.
     */
    private Map<ObjectType, Type> createCHA(final ComputedCallGraph cg) {

        final var project = cg.callGraph().project();
        final AtomicInteger methodNum = new AtomicInteger();
        final Map<ObjectType, Type> cha = new HashMap<>();
        for (final var obj : sort(JavaConverters.asJavaIterable(project.allProjectClassFiles()))) {
            final var classFile = (ClassFile) obj;
            final var currentClass = classFile.thisType();
            final var methods = getMethodsMap(methodNum.get(), JavaConverters.asJavaIterable(classFile.methods()));
            final var type = new Type(methods,
                getSuperClasses(project.classHierarchy(), currentClass),
                getSuperInterfaces(project.classHierarchy(), currentClass),
                classFile.sourceFile().getOrElse(JavaToScalaConverter.asScalaFunction0OptionString("NotFound")));
            cha.put(currentClass, type);
            methodNum.addAndGet(methods.size());
        }

        return cha;
    }

    /** Given a call graph and a CHA it creates a list of resolved calls.
     * This list indicates source and target methods by their unique within artifact ids existing in the cha.
     *
     * @param cg {@link ComputedCallGraph}
     * @param cha A Map of {@link ObjectType} and {@link ExtendedRevisionCallGraph.Type}
     * @return a list of int[] that the first element of each int[] is the source method and
     * the second one is the target method.
     */
    private List<int[]> getResolvedCalls(final ComputedCallGraph cg,
                                         final Map<ObjectType, Type> cha)
    {
        final Set<int[]> resultSet = new HashSet<>();
        for (final var source : JavaConverters.asJavaIterable(cg.callGraph().project().allMethods())) {
            final var targetsMap = cg.callGraph().calls((source));
            if (targetsMap != null && !targetsMap.isEmpty()) {
                for (final var keyValue : JavaConverters.asJavaIterable(targetsMap)) {
                    for (final var target : JavaConverters.asJavaIterable(keyValue._2())) {
                        final var call = new int[]{
                            cha.get(source.declaringClassFile().thisType()).getMethods().get(source),
                            cha.get(target.declaringClassFile().thisType()).getMethods().get(target)
                        };
                        resultSet.add(call);
                    }
                }
            }
        }

        return new ArrayList<>(resultSet);
    }

    /** Given a call graph and a CHA it creates a map of unresolved calls and their call type.
     * This map indicates the source methods by their unique within artifact id existing in the cha,
     * target methods by their {@link FastenURI}, and a map that indicates the call type.
     *
     * @param cg {@link ComputedCallGraph}
     * @param cha A Map of {@link ObjectType} and {@link ExtendedRevisionCallGraph.Type}
     * @return A map that each each entry of it is a {@link Pair} of source method's id,
     * and target method's {@link FastenURI} as key and a map that shows call types as value.
     * call types map's key is the name of JVM call type and the value is number of invocation
     * by this call type for this specific edge.
     */
    private Map<Pair<Integer, FastenURI>, Map<String, Integer>> getUnresolvedCalls(final ComputedCallGraph cg,
                                                                                   final Map<ObjectType, Type> cha)
    {
        final var unresolvedCalls = cg.unresolvedMethodCalls();
        final Map<Pair<Integer, FastenURI>, Map<String, Integer>> result = new HashMap<>();

        for (final var unresolvedCall : JavaConverters.asJavaIterable(unresolvedCalls)) {
            final var call = new MutablePair<>(
                cha.get(unresolvedCall.caller().declaringClassFile().thisType()).getMethods().get(unresolvedCall.caller()),
                getTargetURI(unresolvedCall));

            final var typeOfCall = unresolvedCall.caller().instructionsOption().get()[unresolvedCall.pc()].mnemonic();
            putCall(result, call, typeOfCall);
        }

        return result;
    }

    /** It puts the given call to the given map if it doesn't exist and if call
     *  already exists in the map it will be updated with the passed call extra information.
     *
     * @param result The result map that should be updated with the given call.
     * @param call Resolved or unresolved call.
     * @param typeOfCall Type of JVM call: invodestatic, invokedynamic, invokevirtual, invokeinterface, invokespecial
     * @param <T> int[] if it's resolved call and {@link Pair} of Integer and {@link FastenURI} if unresolved.
     */
    private <T> void putCall(final Map<T, Map<String, Integer>> result,
                             final T call,
                             final String typeOfCall)
    {
        if (result.containsKey(call)) {
            if (result.get(call).containsKey(typeOfCall)) {
                result.get(call).put(typeOfCall, result.get(call).get(typeOfCall) + 1);
            } else {
                result.get(call).put(typeOfCall, 1);
            }
        } else {
            result.put(call, new HashMap<>(Map.of(typeOfCall, 1)));
        }
    }

    /** Given an unresolved call it generates a FastenURI for the target method of the call.
     *
     * @param unresolvedCall {@link UnresolvedMethodCall}
     * @return {@link FastenURI} with empty product.
     */
    public static FastenURI getTargetURI(final UnresolvedMethodCall unresolvedCall) {

        final var targetURI = Method.toCanonicalSchemelessURI(
            null,
            unresolvedCall.calleeClass(),
            unresolvedCall.calleeName(),
            unresolvedCall.calleeDescriptor()
        );

        if (targetURI == null) {
            return null;
        }

        return FastenURI.create("//" + targetURI.toString());
    }

    /** Extract super classes of a given type from a given CHA.
     *
     * @param classHierarchy {@link ClassHierarchy} of the artifact to be investigated for super classes.
     * @param currentClass {@link ObjectType} the type that we are looking for it's super classes.
     * @return A {@link Chain} of {@link ObjectType} as super classes of the passed type.
     */
    public Chain<ObjectType> getSuperClasses(final ClassHierarchy classHierarchy,
                                             final ObjectType currentClass)
    {
        try {
            if (classHierarchy.supertypes().contains(currentClass)) {
                final var superClasses = classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
                if (superClasses != null) {
                    return superClasses.reverse();
                }
            }
        } catch (NoSuchElementException e) {
            logger.error("This type {} doesn't have allSuperclassTypesInInitializationOrder" +
                " method.", currentClass, e);
        } catch (OutOfMemoryError e) {
            logger.error("This type {} made an out of memory Exception in calculation of its" +
                "supper types!", currentClass, e);
        } catch (Exception e) {
            logger.error("This type made an Exception in calculation of its supper types!", e);
        }
        return null;
    }

    /** Extract super Interfaces of a given type from a given CHA.
     *
     * @param classHierarchy {@link ClassHierarchy} of the artifact to be investigated for super Interfaces.
     * @param currentClass {@link ObjectType} the type that we are looking for it's super interfaces.
     * @return A list of {@link ObjectType} as super interfaces of the passed type.
     */
    public List<ObjectType> getSuperInterfaces(final ClassHierarchy classHierarchy,
                                               final ObjectType currentClass)
    {
        return Lists.newArrayList(JavaConverters.asJavaIterable(classHierarchy.allSuperinterfacetypes(currentClass, false)));
    }

    /** Sorts the given Iterable.
     *
     * @param iterable Iterable to be sorted.
     * @return Sorted List.
     */
    private List<?> sort(final java.lang.Iterable<?> iterable) {
        final var result = Lists.newArrayList(iterable);
        result.sort(Comparator.comparing(Object::toString));
        return result;
    }

    /** Assign each method an id. Ids start from the the first parameter and increase by one number for every method.
     *
     * @param keyStartsFrom Starting point of the Methods's ids.
     * @param methods Iterable of {@link org.opalj.br.Method} to get mapped to ids.
     * @return A map of passed methods and their ids.
     * @implNote Methods are keys of the result map and values are the generated Integer keys.
     */
    private Map<org.opalj.br.Method, Integer> getMethodsMap(final int keyStartsFrom,
                                                            final java.lang.Iterable<org.opalj.br.Method> methods)
    {

        final Map<org.opalj.br.Method, Integer> result = new HashMap<>();
        final AtomicInteger i = new AtomicInteger(keyStartsFrom);
        for (final var method : methods) {
            result.put(method, i.get());
            i.addAndGet(1);
        }
        return result;
    }

    /** Generates a call graph for a given file using {@link CallGraphFactory}.
     *
     * @param artifactFile {@link File} that can be jar or class files or a folder containing them.
     * @return {@link ComputedCallGraph}
     */
    public ComputedCallGraph generatePartialCallGraph(final File artifactFile) {

        final var artifactInOpalFormat = Project.apply(artifactFile);

//        OPALLogger.updateLogger(artifactInOpalFormat.logContext(),new ConsoleOPALLogger(true, 0));
//        ComputedCallGraph callGraphInOpalFormat = (ComputedCallGraph) AnalysisModeConfigFactory.resetAnalysisMode(artifactInOpalFormat, AnalysisModes.OPA()).get(CHACallGraphKey$.MODULE$);

        return CallGraphFactory.create(artifactInOpalFormat,
            JavaToScalaConverter.asScalaFunction0EntryPionts(findEntryPoints(JavaConverters.asJavaIterable(artifactInOpalFormat.allMethodsWithBody()))),
            new CHACallGraphAlgorithmConfiguration(artifactInOpalFormat, true));

    }


    /** Finds non abstract and non private methods of the artifact as entrypoints for call graph generation.
     *
     * @param methods are all of the {@link org.opalj.br.Method} in an OPAL-loaded project.
     * @return An {@link Iterable} of entrypoints to be consumed by scala-written OPAL.
     */
    public static Iterable<org.opalj.br.Method> findEntryPoints(final java.lang.Iterable<org.opalj.br.Method> methods) {

        final List<org.opalj.br.Method> result = new ArrayList<>();

        for (final var method : methods) {
            if (!(method.isAbstract()) && !(method.isPrivate())){
                result.add(method);
            }
        }
        return JavaConverters.collectionAsScalaIterable(result);

    }

    /** Converts all of the members of the classHierarchy to {@link FastenURI}.
     *
     * @param classHierarchy Map<{@link ObjectType},{@link Type}>
     * @return A {@link Map} of {@link FastenURI} as key and {@link ExtendedRevisionCallGraph.Type} as value.
     */
    public static Map<FastenURI, ExtendedRevisionCallGraph.Type> asURIHierarchy(final Map<ObjectType, Type> classHierarchy) {

        final Map<FastenURI, ExtendedRevisionCallGraph.Type> result = new HashMap<>();

        for (final var aClass : classHierarchy.keySet()) {

            final var type = classHierarchy.get(aClass);
            final LinkedList<FastenURI> superClassesURIs;
            if (type.getSuperClasses() != null) {
                superClassesURIs = toURIClasses(type.getSuperClasses());
            } else {
                logger.warn("There is no super type for {}", aClass);
                superClassesURIs = new LinkedList<>();
            }

            result.put(
                Method.getTypeURI(aClass),
                new ExtendedRevisionCallGraph.Type(type.getSourceFileName(),
                    toURIMethods(type.getMethods()),
                    superClassesURIs,
                    toURIInterfaces(type.getSuperInterfaces())));
        }
        return result;
    }

    /** Converts a {@link List} of {@link ObjectType} to a list of {@link FastenURI}.
     */
    public static List<FastenURI> toURIInterfaces(final List<ObjectType> types) {
        final List<FastenURI> result = new ArrayList<>();
        for (final var aClass : types) {
            result.add(Method.getTypeURI(aClass));
        }
        return result;
    }

    /** Converts a {@link Chain} of {@link ObjectType} to a {@link LinkedList} of {@link FastenURI}.
     */
    public static LinkedList<FastenURI> toURIClasses(final Chain<ObjectType> types) {

        final LinkedList<FastenURI> result = new LinkedList<>();

        types.foreach(JavaToScalaConverter.asScalaFunction1(
            aClass -> result.add(Method.getTypeURI((ObjectType) aClass))));

        return result;
    }

    /** Converts a {@link Map} of {@link org.opalj.br.Method} to a Map of {@link FastenURI}. And also shifts the keys and values.
     *
     * @param methods {@link org.opalj.br.Method} are keys and their unique id in the artifact are values.
     * @return A Map in which the unique id of each method in the artifact is the key and the {@link FastenURI} of the method is the value.
     */
    public static Map<Integer, FastenURI> toURIMethods(final Map<org.opalj.br.Method, Integer> methods) {

        final Map<Integer, FastenURI> result = new HashMap<>();

        for (final var entry : methods.entrySet()) {
            final var method = entry.getKey();
            result.put(entry.getValue(),
                Method.toCanonicalSchemelessURI(
                    null,
                    method.declaringClassFile().thisType(),
                    method.name(),
                    method.descriptor()
                )
            );
        }
        return result;
    }

    /** Checks whether the environment is test.
     *
     * @return true if tests are running, otherwise false.
     */
    public static boolean isJUnitTest() {
        final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        final List<StackTraceElement> list = Arrays.asList(stackTrace);
        for (StackTraceElement element : list) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }

    public Map<Pair<Integer, FastenURI>, Map<String, Integer>> getUnresolvedCalls() { return this.unresolvedCalls; }

    public List<int[]> getResolvedCalls() {
        return this.resolvedCalls;
    }

    public Map<FastenURI, ExtendedRevisionCallGraph.Type> getClassHierarchy() { return classHierarchy; }

    public String getGENERATOR() {
        return GENERATOR;
    }

}
