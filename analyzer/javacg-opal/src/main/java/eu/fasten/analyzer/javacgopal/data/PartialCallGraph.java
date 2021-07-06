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

package eu.fasten.analyzer.javacgopal.data;

import com.google.common.collect.Lists;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.analysis.OPALClassHierarchy;
import eu.fasten.analyzer.javacgopal.data.analysis.OPALMethod;
import eu.fasten.analyzer.javacgopal.data.analysis.OPALType;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.data.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.opalj.br.*;
import org.opalj.br.analyses.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.opalj.collection.immutable.RefArray;
import org.opalj.tac.AITACode;
import org.opalj.tac.ComputeTACAIKey$;
import org.opalj.tac.DUVar;
import org.opalj.tac.Stmt;
import org.opalj.tac.TACMethodParameter;
import org.opalj.value.ValueInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Function1;
import scala.collection.JavaConverters;

/**
 * Call graphs that are not still fully resolved. i.e. isolated call graphs which within-artifact
 * calls (edges) are known as internal calls and Cross-artifact calls are known as external calls.
 */
public class PartialCallGraph {

    private static final Logger logger = LoggerFactory.getLogger(PartialCallGraph.class);

    private final EnumMap<JavaScope, Map<String, JavaType>> classHierarchy;
    private final JavaGraph graph;
    private final int nodeCount;

    public PartialCallGraph(CallGraphConstructor constructor) throws OPALException{
        this(constructor, true);
    }

    /**
     * Given a file, algorithm and main class (in case of application package)
     * it creates a {@link PartialCallGraph} for it using OPAL.
     *
     * @param constructor call graph constructor
     */
    public PartialCallGraph(CallGraphConstructor constructor, boolean callSiteOnly) throws OPALException {
        this.graph = new JavaGraph();

        try {
            final var cha = createInternalCHA(constructor.getProject());

            createGraphWithExternalCHA(constructor, cha, callSiteOnly);

            this.nodeCount = cha.getNodeCount();
            this.classHierarchy = cha.asURIHierarchy(constructor.getProject().classHierarchy());
        } catch (Exception e) {
            if (e.getStackTrace().length > 0) {
                var stackTrace = e.getStackTrace()[0];
                if (stackTrace.toString().startsWith("org.opalj")) {
                    var opalException = new OPALException(
                            "Original error type: " + e.getClass().getSimpleName()
                                    + "; Original message: " + e.getMessage());
                    opalException.setStackTrace(e.getStackTrace());
                    throw opalException;
                }
            }
            throw e;
        }
    }

    public EnumMap<JavaScope, Map<String, JavaType>> getClassHierarchy() {
        return classHierarchy;
    }

    public JavaGraph getGraph() {
        return graph;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    /**
     * Creates RevisionCallGraph using OPAL call graph generator for a given maven
     * coordinate. It also sets the forge to "mvn".
     *
     * @param coordinate maven coordinate of the revision to be processed
     * @param timestamp  timestamp of the revision release
     * @return RevisionCallGraph of the given coordinate.
     */
    public static ExtendedRevisionJavaCallGraph createExtendedRevisionJavaCallGraph(
            final MavenCoordinate coordinate, final String mainClass,
            final String algorithm, final long timestamp, final String artifactRepo, final boolean callSiteOnly)
            throws MissingArtifactException, OPALException {

        File file = null;
        try {
            file = new MavenCoordinate.MavenResolver().downloadArtifact(coordinate, artifactRepo);
            final var opalCG = new CallGraphConstructor(file, mainClass, algorithm);

            final var partialCallGraph = new PartialCallGraph(opalCG, callSiteOnly);

            return new ExtendedRevisionJavaCallGraph(Constants.mvnForge, coordinate.getProduct(),
                    coordinate.getVersionConstraint(), timestamp,
                    partialCallGraph.getNodeCount(), Constants.opalGenerator,
                    partialCallGraph.getClassHierarchy(),
                    partialCallGraph.getGraph());
        } finally {
            if (file != null) {
                file.delete();
            }
        }
    }

    /**
     * Creates a class hierarchy for the given call graph's artifact with entries
     * only in internalCHA. ExternalCHA to be added at a later stage.
     *
     * @param project OPAL {@link Project}
     * @return class hierarchy for a given package
     * @implNote Inside {@link OPALType} all of the methods are indexed.
     */
    private OPALClassHierarchy createInternalCHA(final Project<?> project) {
        final Map<ObjectType, OPALType> result = new HashMap<>();
        final AtomicInteger methodNum = new AtomicInteger();

        final var objs = Lists.newArrayList(JavaConverters.asJavaIterable(project.allClassFiles()));
        objs.sort(Comparator.comparing(Object::toString));

        var opalAnnotations = new HashMap<String, List<Pair<String, String>>>();
        for (final var classFile : objs) {
            var annotations = JavaConverters.asJavaIterable(classFile.annotations());
            if (annotations != null) {
                for (Annotation annotation : annotations) {
                    final var annotationPackage =
                        OPALMethod.getPackageName(annotation.annotationType());
                    final var annotationClass = OPALMethod.getClassName(annotation.annotationType());

                    var valueList = new ArrayList<Pair<String, String>>();
                    final var values = JavaConverters.asJavaIterable(annotation.elementValuePairs());
                    if (values != null) {
                        for (ElementValuePair value : values) {
                            try {
                                final var valuePackage = OPALMethod.getPackageName(value.value().valueType());
                                final var valueClass = OPALMethod.getClassName(value.value().valueType());
                                final var valueContent = StringEscapeUtils.escapeJava(value.value().toJava());
                                valueList.add(Pair.of(valuePackage + "/" + valueClass, valueContent));
                            } catch (NullPointerException ignored) {
                            }
                        }
                    }
                    opalAnnotations.put(annotationPackage + "/" + annotationClass, valueList);
                }
            }
            final var currentClass = classFile.thisType();
            final var methods = getMethodsMap(methodNum.get(),
                    JavaConverters.asJavaIterable(classFile.methods()));
            var namespace = OPALMethod.getPackageName(classFile.thisType());
            var filepath = namespace != null ? namespace.replace(".", "/") : "";
            final var type = new OPALType(methods,
                    OPALType.extractSuperClasses(project.classHierarchy(), currentClass),
                    OPALType.extractSuperInterfaces(project.classHierarchy(), currentClass),
                    classFile.sourceFile().isDefined()
                            ? filepath + "/" + classFile.sourceFile().get()
                            : "NotFound",
                    classFile.isPublic() ? "public" : "packagePrivate", classFile.isFinal(),
                    opalAnnotations);

            result.put(currentClass, type);
            methodNum.addAndGet(methods.size());
        }
        return new OPALClassHierarchy(result, new HashMap<>(), methodNum.get());
    }

    /**
     * Assign each method an id. Ids start from the the first parameter and increase by one number
     * for every method.
     *
     * @param methods Iterable of {@link Method} to get mapped to ids.
     * @return A map of passed methods and their ids.
     * @implNote Methods are keys of the result map and values are the generated Integer keys.
     */
    private Map<Method, Integer> getMethodsMap(final int keyStartsFrom,
                                               final Iterable<Method> methods) {
        final Map<Method, Integer> result = new HashMap<>();
        final AtomicInteger i = new AtomicInteger(keyStartsFrom);
        for (final var method : methods) {
            result.put(method, i.get());
            i.addAndGet(1);
        }
        return result;
    }

    /**
     * Given a call graph generated by OPAL and class hierarchy iterates over methods
     * declared in the package that call external methods and add them to externalCHA of
     * a call hierarchy. Build a graph for both internal and external calls in parallel.
     *  @param cgConstructor  call graph from OPAL generator
     * @param cha class hierarchy
     * @param callSiteOnly
     */
    private void createGraphWithExternalCHA(final CallGraphConstructor cgConstructor,
                                            final OPALClassHierarchy cha, boolean callSiteOnly) {
        final var cg = cgConstructor.getCallGraph();
        final var tac = cgConstructor.getProject().get(ComputeTACAIKey$.MODULE$);
        for (final var sourceDeclaration : JavaConverters
            .asJavaIterable(cg.reachableMethods().toIterable())) {
            final List<Integer> incompeletes = new ArrayList<>();
            if (cg.incompleteCallSitesOf(sourceDeclaration) != null) {
                JavaConverters.asJavaIterator(cg.incompleteCallSitesOf(sourceDeclaration))
                    .forEachRemaining(pc -> incompeletes.add((int) pc));
            }
            final Set<Integer> visitedPCs = new HashSet<>();

            if (sourceDeclaration.hasMultipleDefinedMethods()) {
                for (final var source : JavaConverters
                    .asJavaIterable(sourceDeclaration.definedMethods())) {
                    cha.appendGraph(source, cg.calleesOf(sourceDeclaration),
                        getStmts(tac, sourceDeclaration.definedMethod()), graph, incompeletes,
                        visitedPCs, callSiteOnly);
                }
            } else if (sourceDeclaration.hasSingleDefinedMethod()) {
                final var definedMethod = sourceDeclaration.definedMethod();
                cha.appendGraph(definedMethod, cg.calleesOf(sourceDeclaration),
                    getStmts(tac, definedMethod), graph, incompeletes, visitedPCs, callSiteOnly);

            } else if (sourceDeclaration.isVirtualOrHasSingleDefinedMethod()) {

                cha.appendGraph(sourceDeclaration, cg.calleesOf(sourceDeclaration), getStmts(tac,
                    null), graph, incompeletes, visitedPCs, callSiteOnly);
            }
            if (!incompeletes.isEmpty()) {
                logger.warn("There is an incomplete call site " +
                    "that OPAL did not take care of. source: " + sourceDeclaration + "PCs: "+ incompeletes);

            }
        }
    }

    private Stmt<DUVar<ValueInformation>>[] getStmts(Function1<Method, AITACode<TACMethodParameter, ValueInformation>> tac,
                                                     Method definedSource) {
        Stmt<DUVar<ValueInformation>>[] stmts = null;
        if (definedSource != null) {
            AITACode<TACMethodParameter, ValueInformation> sourceTac = null;
            try {
                sourceTac = tac.apply(definedSource);

            }catch (NoSuchElementException e){
                logger.warn("couldn't find the stmt");
            }
            if (sourceTac != null) {
                stmts = sourceTac.stmts();
            }
        }
        return stmts;
    }
}
