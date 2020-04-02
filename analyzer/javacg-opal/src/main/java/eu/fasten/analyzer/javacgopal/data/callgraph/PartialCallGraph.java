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
import eu.fasten.analyzer.baseanalyzer.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.OPALMethod;
import eu.fasten.analyzer.javacgopal.data.OPALType;
import eu.fasten.analyzer.javacgopal.scalawrapper.JavaToScalaConverter;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.FastenURI;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opalj.ai.analyses.cg.CHACallGraphAlgorithmConfiguration;
import org.opalj.ai.analyses.cg.CallGraphFactory;
import org.opalj.ai.analyses.cg.ComputedCallGraph;
import org.opalj.ai.analyses.cg.UnresolvedMethodCall;
import org.opalj.br.ClassFile;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.ObjectType;
import org.opalj.br.analyses.Project;
import org.opalj.collection.immutable.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.Iterable;
import scala.collection.JavaConverters;

/**
 * Call graphs that are not still fully resolved. i.e. isolated call graphs which within-artifact
 * calls (edges) are known as internal calls and Cross-artifact calls are known as external. calls.
 */
public class PartialCallGraph {

    private static Logger logger = LoggerFactory.getLogger(PartialCallGraph.class);

    /**
     * Class hierarchy of this call graph, keys are {@link FastenURI} of classes of the artifact and
     * values are {@link ExtendedRevisionCallGraph.Type}.
     */
    private final Map<FastenURI, ExtendedRevisionCallGraph.Type> classHierarchy;

    /**
     * Internal calls of the call graph, each element is a list of Integer that the first element is
     * the id of source method and the second one is the id of the target method. Ids are available
     * in the class hierarchy.
     * @implNote Since this part of the graphs are most of the times bottleneck it is important
     *     to use a proper data structure. using int[] for each edge will make things a bit
     *     difficult in future because of the array equal and toString methods. Pair is also a good
     *     option since it's representative of the functionality but the equal function works too
     *     slow to be in such a critical place. So Lists are good trade of in every aspect.
     */
    private final List<List<Integer>> internalCalls;

    /**
     * External calls of the call graph. Each key of the map is an external call. Keys are {@link
     * Pair} of Integers that indicate the source methods's id (available in the CHA) and a {@link
     * FastenURI} of the target method. Values are a map between JVM call types and number of each
     * call type for the corresponding edge
     */
    private final Map<Pair<Integer, FastenURI>, Map<String, String>> externalCalls;

    /**
     * Given a file it creates a {@link PartialCallGraph} for it using OPAL.
     * @param file class files to be process, it can be jar file or a folder containing class
     *             files.
     */
    public PartialCallGraph(final File file) {
        logger.info("Generating call graph using OPAL ...");
        final var cg = generateCallGraph(file);
        logger.info("OPAL generated the call graph, creating the CHA ...");
        final var cha = createCHA(cg);
        logger.info("CHA is created, setting internal calls ...");
        this.internalCalls = getInternalCalls(cg, cha);
        logger.info("Internal calls are set, setting external calls ...");
        this.externalCalls = getExternalCalls(cg, cha);
        logger.info("External calls are set, converting CHA entities to URIs ...");
        this.classHierarchy = asURIHierarchy(cha);
        logger.info("CHA entities has been converted to URIs.");
    }

    /**
     * Given an external call it generates a FastenURI for the target method of the call.
     * @param externalCall {@link UnresolvedMethodCall}
     * @return {@link FastenURI} with empty product.
     */
    public static FastenURI getTargetURI(final UnresolvedMethodCall externalCall) {

        final var targetURI = OPALMethod.toCanonicalSchemelessURI(
            null,
            externalCall.calleeClass(),
            externalCall.calleeName(),
            externalCall.calleeDescriptor()
        );

        if (targetURI == null) {
            return null;
        }

        return FastenURI.create("//" + targetURI.toString());
    }

    /**
     * Finds non abstract and non private methods of the artifact as entrypoints for call graph
     * generation.
     * @param methods are all of the {@link org.opalj.br.Method} in an OPAL-loaded project.
     * @return An {@link Iterable} of entrypoints to be consumed by scala-written OPAL.
     */
    public static Iterable<org.opalj.br.Method> findEntryPoints(
        final java.lang.Iterable<org.opalj.br.Method> methods) {

        final List<org.opalj.br.Method> result = new ArrayList<>();

        for (final var method : methods) {
            if (!(method.isAbstract()) && !(method.isPrivate())) {
                result.add(method);
            }
        }
        return JavaConverters.collectionAsScalaIterable(result);

    }

    /**
     * Converts all of the members of the classHierarchy to {@link FastenURI}.
     * @param classHierarchy Map<{@link ObjectType},{@link OPALType}>
     * @return A {@link Map} of {@link FastenURI} as key and {@link ExtendedRevisionCallGraph.Type}
     *     as value.
     */
    public static Map<FastenURI, ExtendedRevisionCallGraph.Type> asURIHierarchy(
        final Map<ObjectType, OPALType> classHierarchy) {

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
                OPALMethod.getTypeURI(aClass),
                new ExtendedRevisionCallGraph.Type(type.getSourceFileName(),
                    toURIMethods(type.getMethods()),
                    superClassesURIs,
                    toURIInterfaces(type.getSuperInterfaces())));
        }
        return result;
    }

    /**
     * Converts a {@link List} of {@link ObjectType} to a list of {@link FastenURI}.
     */
    public static List<FastenURI> toURIInterfaces(final List<ObjectType> types) {
        final List<FastenURI> result = new ArrayList<>();
        for (final var aClass : types) {
            result.add(OPALMethod.getTypeURI(aClass));
        }
        return result;
    }

    /**
     * Converts a {@link Chain} of {@link ObjectType} to a {@link LinkedList} of {@link FastenURI}.
     */
    public static LinkedList<FastenURI> toURIClasses(final Chain<ObjectType> types) {

        final LinkedList<FastenURI> result = new LinkedList<>();

        types.foreach(JavaToScalaConverter.asScalaFunction1(
            clas -> result.add(OPALMethod.getTypeURI((ObjectType) clas))));

        return result;
    }

    /**
     * Converts a {@link Map} of {@link org.opalj.br.Method} to a Map of {@link FastenURI}. And also
     * shifts the keys and values.
     * @param methods {@link org.opalj.br.Method} are keys and their unique id in the artifact are
     *                values.
     * @return A Map in which the unique id of each method in the artifact is the key and the {@link
     *     FastenURI} of the method is the value.
     */
    public static Map<Integer, FastenURI> toURIMethods(
        final Map<org.opalj.br.Method, Integer> methods) {

        final Map<Integer, FastenURI> result = new HashMap<>();

        for (final var entry : methods.entrySet()) {
            final var method = entry.getKey();
            result.put(entry.getValue(),
                OPALMethod.toCanonicalSchemelessURI(
                    null,
                    method.declaringClassFile().thisType(),
                    method.name(),
                    method.descriptor()
                )
            );
        }
        return result;
    }

    /**
     * Checks whether the environment is test.
     * @return true if tests are running, otherwise false.
     */
    public static boolean isJUnitTest() {
        final StackTraceElement[] list = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : list) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * It creates a class hierarchy for the given call graph's artifact.
     * @param cg {@link ComputedCallGraph}
     * @return A Map of {@link ObjectType} and created {@link OPALType} for it.
     * @implNote Inside {@link OPALType} all of the methods are indexed, it means one can use
     *     the ids assigned to each method instead of the method itself.
     */
    public static Map<ObjectType, OPALType> createCHA(final ComputedCallGraph cg) {

        final var project = cg.callGraph().project();
        final AtomicInteger methodNum = new AtomicInteger();
        final Map<ObjectType, OPALType> cha = new HashMap<>();
        for (final var obj : sort(JavaConverters.asJavaIterable(project.allProjectClassFiles()))) {
            final var classFile = (ClassFile) obj;
            final var currentClass = classFile.thisType();
            final var methods =
                getMethodsMap(methodNum.get(), JavaConverters.asJavaIterable(classFile.methods()));
            final var type =
                new OPALType(methods, extractSuperClasses(project.classHierarchy(), currentClass),
                    extractSuperInterfaces(project.classHierarchy(), currentClass),
                    classFile.sourceFile()
                        .getOrElse(JavaToScalaConverter.asScalaFunction0OptionString("NotFound")));
            cha.put(currentClass, type);
            methodNum.addAndGet(methods.size());
        }

        return cha;
    }

    /**
     * Extract super classes of a given type from a given CHA.
     * @param classHierarchy {@link ClassHierarchy} of the artifact to be investigated for super
     *                       classes.
     * @param currentClass   {@link ObjectType} the type that we are looking for it's super
     *                       classes.
     * @return A {@link Chain} of {@link ObjectType} as super classes of the passed type.
     */
    public static Chain<ObjectType> extractSuperClasses(final ClassHierarchy classHierarchy,
                                                        final ObjectType currentClass) {
        try {
            if (classHierarchy.supertypes().contains(currentClass)) {
                final var superClasses =
                    classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
                if (superClasses != null) {
                    return superClasses.reverse();
                }
            }
        } catch (NoSuchElementException e) {
            logger.error("This type {} doesn't have allSuperclassTypesInInitializationOrder"
                + " method.", currentClass, e);
        } catch (OutOfMemoryError e) {
            logger.error("This type {} made an out of memory Exception in calculation of its"
                + "supper types!", currentClass, e);
        } catch (Exception e) {
            logger.error("This type made an Exception in calculation of its supper types!", e);
        }
        return null;
    }

    /**
     * Extract super Interfaces of a given type from a given CHA.
     * @param classHierarchy {@link ClassHierarchy} of the artifact to be investigated for super
     *                       Interfaces.
     * @param currentClass   {@link ObjectType} the type that we are looking for it's super
     *                       interfaces.
     * @return A list of {@link ObjectType} as super interfaces of the passed type.
     */
    public static List<ObjectType> extractSuperInterfaces(final ClassHierarchy classHierarchy,
                                                          final ObjectType currentClass) {
        return Lists.newArrayList(JavaConverters
            .asJavaIterable(classHierarchy.allSuperinterfacetypes(currentClass, false)));
    }

    /**
     * Sorts the given Iterable.
     * @param iterable Iterable to be sorted.
     * @return Sorted List.
     */
    private static List<?> sort(final java.lang.Iterable<?> iterable) {
        final var result = Lists.newArrayList(iterable);
        result.sort(Comparator.comparing(Object::toString));
        return result;
    }

    /**
     * Assign each method an id. Ids start from the the first parameter and increase by one number
     * for every method.
     * @param keyStartsFrom Starting point of the Methods's ids.
     * @param methods       Iterable of {@link org.opalj.br.Method} to get mapped to ids.
     * @return A map of passed methods and their ids.
     * @implNote Methods are keys of the result map and values are the generated Integer keys.
     */
    private static Map<org.opalj.br.Method, Integer> getMethodsMap(
        final int keyStartsFrom,
        final java.lang.Iterable<org.opalj.br.Method> methods) {

        final Map<org.opalj.br.Method, Integer> result = new HashMap<>();
        final AtomicInteger i = new AtomicInteger(keyStartsFrom);
        for (final var method : methods) {
            result.put(method, i.get());
            i.addAndGet(1);
        }
        return result;
    }

    /**
     * Generates a call graph for a given file using {@link CallGraphFactory}.
     * @param artifactFile {@link File} that can be jar or class files or a folder containing them.
     * @return {@link ComputedCallGraph}
     */
    public static ComputedCallGraph generateCallGraph(final File artifactFile) {

        final var artifactInOpalFormat = Project.apply(artifactFile);

        //OPALLogger.updateLogger(artifactInOpalFormat.logContext(),
        // new ConsoleOPALLogger(true, 0));
        //ComputedCallGraph callGraphInOpalFormat = (ComputedCallGraph) AnalysisModeConfigFactory
        //    .resetAnalysisMode(artifactInOpalFormat, AnalysisModes.OPA())
        //    .get(CHACallGraphKey$.MODULE$);

        return CallGraphFactory.create(artifactInOpalFormat,
            JavaToScalaConverter.asScalaFunction0EntryPionts(findEntryPoints(
                JavaConverters.asJavaIterable(artifactInOpalFormat.allMethodsWithBody()))),
            new CHACallGraphAlgorithmConfiguration(artifactInOpalFormat, true));

    }


    /**
     * Creates {@link ExtendedRevisionCallGraph} using OPAL call graph generator for a given maven
     * coordinate. It also sets the forge to "mvn".
     * @param coordinate maven coordinate of the revision to be processed.
     * @param timestamp  timestamp of the revision release.
     * @return {@link ExtendedRevisionCallGraph} of the given coordinate.
     * @throws FileNotFoundException in case there is no jar file for the given coordinate on the
     *                               Maven central it throws this exception.
     */
    public static ExtendedRevisionCallGraph createExtendedRevisionCallGraph(
        final MavenCoordinate coordinate, final long timestamp)
        throws FileNotFoundException {
        final var partialCallGraph = new PartialCallGraph(
            MavenCoordinate.MavenResolver.downloadJar(coordinate.getCoordinate())
                .orElseThrow(RuntimeException::new)
        );

        return new ExtendedRevisionCallGraph("mvn", coordinate.getProduct(),
            coordinate.getVersionConstraint(), timestamp, partialCallGraph.getGENERATOR(),
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            partialCallGraph.getClassHierarchy(),
            partialCallGraph.getGraph());
    }

    /**
     * Given a call graph and a CHA it creates a list of internal calls. This list indicates source
     * and target methods by their unique within artifact ids existing in the cha.
     * @param cg  {@link ComputedCallGraph}
     * @param cha A Map of {@link ObjectType} and {@link ExtendedRevisionCallGraph.Type}
     * @return a list of List of Integers that the first element of each int[] is the source method
     *     and the second one is the target method.
     */
    private List<List<Integer>> getInternalCalls(final ComputedCallGraph cg,
                                                 final Map<ObjectType, OPALType> cha) {
        final Set<List<Integer>> resultSet = new HashSet<>();
        for (final var source : JavaConverters
            .asJavaIterable(cg.callGraph().project().allMethods())) {
            final var targetsMap = cg.callGraph().calls((source));
            if (targetsMap != null && !targetsMap.isEmpty()) {
                for (final var keyValue : JavaConverters.asJavaIterable(targetsMap)) {
                    for (final var target : JavaConverters.asJavaIterable(keyValue._2())) {
                        final var call = Arrays.asList(
                            cha.get(source.declaringClassFile().thisType()).getMethods().get(
                                source),
                            cha.get(target.declaringClassFile().thisType()).getMethods().get(target)
                        );
                        resultSet.add(call);
                    }
                }
            }
        }
        return new ArrayList<>(resultSet);
    }

    public List<List<Integer>> getInternalCalls() {
        return this.internalCalls;
    }

    /**
     * Given a call graph and a CHA it creates a map of external calls and their call type. This map
     * indicates the source methods by their unique within artifact id existing in the cha, target
     * methods by their {@link FastenURI}, and a map that indicates the call type.
     * @param cg  {@link ComputedCallGraph}
     * @param cha A Map of {@link ObjectType} and {@link ExtendedRevisionCallGraph.Type}
     * @return A map that each each entry of it is a {@link Pair} of source method's id, and target
     *     method's {@link FastenURI} as key and a map that shows call types as value. call types
     *     map's key is the name of JVM call type and the value is number of invocation by this call
     *     type for this specific edge.
     */
    private Map<Pair<Integer, FastenURI>, Map<String, String>> getExternalCalls(
        final ComputedCallGraph cg,
        final Map<ObjectType, OPALType> cha) {
        List<UnresolvedMethodCall> v = new ArrayList<>();

        final var externlCalls = cg.unresolvedMethodCalls();
        final Map<Pair<Integer, FastenURI>, Map<String, String>> result = new HashMap<>();

        for (final var externalCall : JavaConverters.asJavaIterable(externlCalls)) {

            final var call = new MutablePair<>(
                cha.get(externalCall.caller().declaringClassFile().thisType()).getMethods()
                    .get(externalCall.caller()),
                getTargetURI(externalCall));
            final var typeOfCall =
                externalCall.caller().instructionsOption().get()[externalCall.pc()].mnemonic();
            putCall(result, call, typeOfCall);
        }

        return result;
    }

    public Map<Pair<Integer, FastenURI>, Map<String, String>> getExternalCalls() {
        return this.externalCalls;
    }

    /**
     * It puts the given call to the given map if it doesn't exist and if call already exists in the
     * map it will be updated with the passed call extra information.
     * @param result     The result map that should be updated with the given call.
     * @param call       Internal or external call.
     * @param typeOfCall OPALType of JVM call: invodestatic, invokedynamic, invokevirtual,
     *                   invokeinterface, invokespecial
     * @param <T>        int[] if it's internal call and {@link Pair} of Integer and {@link
     *                   FastenURI} if external.
     */
    private <T> void putCall(final Map<T, Map<String, String>> result,
                             final T call,
                             final String typeOfCall) {
        if (result.containsKey(call)) {
            if (result.get(call).containsKey(typeOfCall)) {
                result.get(call).put(typeOfCall,
                    String.valueOf((Integer.parseInt(result.get(call).get(typeOfCall)) + 1)));
            } else {
                result.get(call).put(typeOfCall, "1");
            }
        } else {
            result.put(call, new HashMap<>(Map.of(typeOfCall, "1")));
        }
    }

    public Map<FastenURI, ExtendedRevisionCallGraph.Type> getClassHierarchy() {
        return classHierarchy;
    }

    public String getGENERATOR() {
        return "OPAL";
    }

    public ExtendedRevisionCallGraph.Graph getGraph() {
        return new ExtendedRevisionCallGraph.Graph(this.internalCalls, this.externalCalls);
    }

    /**
     * Returns the map of all the methods in this partial call graph hierarchy.
     * @return a Map of method ids and their corresponding {@link FastenURI}
     */
    public Map<Integer, FastenURI> mapOfAllMethods() {
        Map<Integer, FastenURI> result = new HashMap<>();
        for (final var aClass : this.getClassHierarchy().entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        return result;
    }

    @Override public String toString() {

        return "PartialCallGraph{"
            + "classHierarchy=" + classHierarchy
            + ", internalCalls="
            + internalCalls.stream().map(call -> "[" + call.get(0) + "," + call.get(1)
            + "]").collect(Collectors.joining())
            + ", externalCalls=" + externalCalls
            + '}';
    }
}
