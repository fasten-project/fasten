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
import org.opalj.collection.immutable.ConstArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;
import scala.collection.IndexedSeq;
import scala.collection.Iterable;
import scala.collection.JavaConversions;


/**
 * Call graphs that are not still fully resolved.
 * e.g. isolated call graphs which within-artifact calls (edges) are known as resolved calls and
 * Cross-artifact calls are known as unresolved calls.
 */
public class PartialCallGraph {

    private static Logger logger = LoggerFactory.getLogger(PartialCallGraph.class);

    private final String GENERATOR = "OPAL";
    /**
     * Calls that their target's packages are not still known and need to be resolved in later on, e.g. in a merge phase.
     */
    private final Map<Pair<Integer, FastenURI>, Map<String, Integer>> unresolvedCalls;
    /**
     * Calls that their sources and targets are fully resolved.
     */
    private final List<int[]> resolvedCalls;
    /**
     * ClassHierarchy of the under investigation artifact.
     */
    private final Map<FastenURI, ExtendedRevisionCallGraph.Type> classHierarchy;

    public Map<Pair<Integer, FastenURI>, Map<String, Integer>> getUnresolvedCalls() {
        return this.unresolvedCalls;
    }

    public List<int[]> getResolvedCalls() {
        return this.resolvedCalls;
    }

    public Map<FastenURI, ExtendedRevisionCallGraph.Type> getClassHierarchy() {
        return classHierarchy;
    }

    public String getGENERATOR() {
        return GENERATOR;
    }

    public PartialCallGraph(final File file) {
        final var cg = generatePartialCallGraph(file);
        logger.info("opal is done.");
        final var cha = createCHA(cg);
        logger.info("cha is done");
        this.resolvedCalls = getResolvedCalls(cg, cha);
        logger.info("resolved calls are done.");
        this.unresolvedCalls = getUnresolvedCalls(cg, cha);
        logger.info("unresolved calls are done.");
        this.classHierarchy = getURIHierarchy(cha);
        logger.info("cha hierarchy is done.");
    }

    private List<int[]> getResolvedCalls(final ComputedCallGraph cg, final Map<ObjectType, Type> cha) {

        final Set<int[]> resultSet = new HashSet<>();
        for (final var source : JavaConversions.asJavaIterable(cg.callGraph().project().allMethods())) {
            final var targetsMap = cg.callGraph().calls(((org.opalj.br.Method) source));
            if (targetsMap != null && !targetsMap.isEmpty()) {
                for (Tuple2<Object, Iterable<org.opalj.br.Method>> keyValue : JavaConversions.asJavaIterable(targetsMap)) {
                    for (org.opalj.br.Method target : JavaConversions.asJavaIterable(keyValue._2())) {
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

    private <T> void putCall(final Map<T, Map<String, Integer>> result, final T call, final String typeOfCall) {
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

    private Map<Pair<Integer, FastenURI>, Map<String, Integer>> getUnresolvedCalls(
        final ComputedCallGraph cg,
        final Map<ObjectType, Type> cha) {

        final var unresolvedCalls = cg.unresolvedMethodCalls();
        final Map<Pair<Integer, FastenURI>, Map<String, Integer>> result = new HashMap<>();

        for (final var unresolvedCall : JavaConversions.asJavaIterable(unresolvedCalls)) {

            final var call = new MutablePair<>(
                cha.get(unresolvedCall.caller().declaringClassFile().thisType()).getMethods().get(unresolvedCall.caller()),
                getTargetURI(unresolvedCall)
            );

            final var typeOfCall = unresolvedCall.caller().instructionsOption().get()[unresolvedCall.pc()].mnemonic();

            putCall(result, call, typeOfCall);
        }

        return result;
    }

    /**
     * Converts unresolved calls to URIs.
     * @param unresolvedCall Calls without specified product name in their target. Source or callers of
     *                        such calls are org.opalj.br.Method presented in the under investigation artifact,
     *                        But targets or callees don't have a product.
     * @return List of two dimensional eu.fasten.core.data.FastenURIs[] which always the first dimension of the array is a
     * fully resolved method and the second one has an unknown product.
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

        return FastenURI.create( "//" + targetURI.toString());
    }

    private Map<ObjectType, Type> createCHA(final ComputedCallGraph cg) {

        final var project = cg.callGraph().project();
        logger.info("Start creating hierarchy ...");
        final AtomicInteger methodNum = new AtomicInteger();
        final Map<ObjectType, Type> cha = new HashMap<>();
        for (final Object obj : sort(JavaConversions.asJavaIterable(project.allProjectClassFiles()))) {
            final var classFile = (ClassFile) obj;
            final var currentClass = classFile.thisType();
            final var methods = getMethodsMap(methodNum.get(), classFile.methods());
            final var type = new Type(methods,
                getSuperClasses(project.classHierarchy(), currentClass),
                getSuperInterfaces(project.classHierarchy(), currentClass),
                classFile.sourceFile().getOrElse(JavaToScalaConverter.asScalaFunction0OptionString("NotFound")));
            cha.put(currentClass, type);
            methodNum.addAndGet(methods.size());
        }

        return cha;
    }

    public Chain<ObjectType> getSuperClasses(final ClassHierarchy classHierarchy,
                                             final ObjectType currentClass) {

        try {
            if (classHierarchy.supertypes().contains(currentClass)) {
                final Chain<ObjectType> superClasses = classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
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

    public List<ObjectType> getSuperInterfaces(final ClassHierarchy classHierarchy,
                                               final ObjectType currentClass) {

        return Lists.newArrayList(JavaConversions.asJavaIterable(classHierarchy.allSuperinterfacetypes(currentClass, false)));
    }

    private List<?> sort(final java.lang.Iterable<?> iterable) {
        final var list = Lists.newArrayList(iterable);
        list.sort(Comparator.comparing(Object::toString));
        return list;
    }

    private Map<org.opalj.br.Method, Integer> getMethodsMap(final int keyStartsFrom,
                                                            final IndexedSeq<org.opalj.br.Method> methods) {

        final Map<org.opalj.br.Method, Integer> map = new HashMap<>();
        final AtomicInteger i = new AtomicInteger(keyStartsFrom);
        methods.foreach(JavaToScalaConverter.asScalaFunction1(method -> {
            map.put((org.opalj.br.Method) method, i.get());
            i.addAndGet(1);
            return null;
        }));
        return map;
    }


    /**
     * Loads a given file, generates call graph and change the format of calls to (source -> target).
     *
     * @param artifactFile Java file that can be a jar or a folder containing jars.
     * @return A partial graph including ResolvedCalls, UnresolvedCalls and CHA.
     */
    public ComputedCallGraph generatePartialCallGraph(final File artifactFile) {

        final var artifactInOpalFormat = Project.apply(artifactFile);

        final var callGraphInOpalFormat = CallGraphFactory.create(artifactInOpalFormat,
            JavaToScalaConverter.asScalaFunction0EntryPionts(findEntryPoints(artifactInOpalFormat.allMethodsWithBody())),
            new CHACallGraphAlgorithmConfiguration(artifactInOpalFormat, true));

//        OPALLogger.updateLogger(artifactInOpalFormat.logContext(),new ConsoleOPALLogger(true, 0));
//        ComputedCallGraph callGraphInOpalFormat = (ComputedCallGraph) AnalysisModeConfigFactory.resetAnalysisMode(artifactInOpalFormat, AnalysisModes.OPA()).get(CHACallGraphKey$.MODULE$);

        return callGraphInOpalFormat;

    }


    /**
     * Computes the entrypoints as a pre step of call graph generation.
     *
     * @param allMethods Is all of the methods in an OPAL-loaded project.
     * @return An iterable of entrypoints to be consumed by scala-written OPAL.
     */
    public static Iterable<org.opalj.br.Method> findEntryPoints(final ConstArray allMethods) {

        return (Iterable<org.opalj.br.Method>) allMethods.filter(JavaToScalaConverter.asScalaFunction1((Object method) -> (!((org.opalj.br.Method) method).isAbstract()) && !((org.opalj.br.Method) method).isPrivate()));

    }

    /**
     * Converts all of the members of the classHierarchy to FastenURIs.
     *
     * @param classHierarchy Map<org.obalj.br.ObjectType, eu.fasten.analyzer.javacgopal.data.Type>
     * @return Map<eu.fasten.core.data.FastenURI, eu.fasten.analyzer.javacgopal.graph.ExtendedRevisionCallGraph.Type>
     */
    public static Map<FastenURI, ExtendedRevisionCallGraph.Type> getURIHierarchy(
        final Map<ObjectType, Type> classHierarchy) {

        final Map<FastenURI, ExtendedRevisionCallGraph.Type> uriClassHierarchy = new HashMap<>();

        for (final var aClass : classHierarchy.keySet()) {

            final var type = classHierarchy.get(aClass);
            final LinkedList<FastenURI> superClassesURIs;
            if (type.getSuperClasses() != null) {
                superClassesURIs = toURIClasses(type.getSuperClasses());
            } else {
                logger.warn("There is no super type for {}", aClass);
                superClassesURIs = new LinkedList<>();
            }

            uriClassHierarchy.put(
                Method.getTypeURI(aClass),
                new ExtendedRevisionCallGraph.Type(
                    type.getSourceFileName(),
                    toURIMethods(type.getMethods()),
                    superClassesURIs,
                    toURIInterfaces(type.getSuperInterfaces())
                )
            );
        }
        return uriClassHierarchy;
    }

    /**
     * Converts a list of interfaces to a list of FastenURIs.
     *
     * @param interfaces A list of org.obalj.br.ObjectType
     * @return A list of eu.fasten.core.data.FastenURI.
     */
    public static List<FastenURI> toURIInterfaces(final List<ObjectType> interfaces) {
        final List<FastenURI> classURIs = new ArrayList<>();
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
    public static LinkedList<FastenURI> toURIClasses(final Chain<ObjectType> classes) {
        final LinkedList<FastenURI> classURIs = new LinkedList<>();

        classes.foreach(JavaToScalaConverter.asScalaFunction1(
            aClass -> classURIs.add(Method.getTypeURI((ObjectType) aClass))));

        return classURIs;
    }

    /**
     * Converts a list of methods to a list of FastenURIs.
     *
     * @param methods A list of org.obalj.br.Method
     * @return A Map of eu.fasten.core.data.FastenURIs
     */
    public static Map<Integer, FastenURI> toURIMethods(
        final Map<org.opalj.br.Method, Integer> methods) {
        final Map<Integer, FastenURI> methodsURIs = new HashMap<>();

        methods.forEach((method, key) -> {
            methodsURIs.put(key,
                Method.toCanonicalSchemelessURI(
                    null,
                    method.declaringClassFile().thisType(),
                    method.name(),
                    method.descriptor()
                )
            );
        });
        return methodsURIs;
    }

    /**
     * Checks whether the environment is test.
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


}
