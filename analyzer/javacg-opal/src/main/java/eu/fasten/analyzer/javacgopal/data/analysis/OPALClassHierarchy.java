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

package eu.fasten.analyzer.javacgopal.data.analysis;

import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.DeclaredMethod;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.br.ReferenceType;
import org.opalj.collection.immutable.UIDSet;
import org.opalj.tac.DUVar;
import org.opalj.tac.Stmt;
import org.opalj.tac.UVar;
import org.opalj.value.ValueInformation;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConverters;

/**
 * Class hierarchy class containing two types of CHA - internal and external CHA
 * and also keeping track of node count.
 */
public class OPALClassHierarchy {

    public final Map<ObjectType, OPALType> internalCHA;
    public final Map<ObjectType, Map<DeclaredMethod, Integer>> externalCHA;
    public final AtomicInteger nodeCount;
    public final JavaGraph graph;

    /**
     * Class hierarchy constructor.
     *
     * @param internalCHA class hierarchy containing project specific entries
     * @param externalCHA class hierarchy containing entries from outside the project scope
     * @param nodeCount   number of nodes
     */
    public OPALClassHierarchy(Map<ObjectType, OPALType> internalCHA,
                              Map<ObjectType, Map<DeclaredMethod, Integer>> externalCHA,
                              int nodeCount, JavaGraph graph) {
        this.internalCHA = new ConcurrentHashMap<>(internalCHA);
        this.externalCHA = new ConcurrentHashMap<>(externalCHA);
        this.nodeCount = new AtomicInteger(nodeCount);
        this.graph = graph;
    }

    public OPALClassHierarchy(Map<ObjectType, OPALType> internalCHA,
                              Map<ObjectType, Map<DeclaredMethod, Integer>> externalCHA,
                              int nodeCount) {
        this.internalCHA = new ConcurrentHashMap<>(internalCHA);
        this.externalCHA = new ConcurrentHashMap<>(externalCHA);
        this.nodeCount = new AtomicInteger(nodeCount);
        this.graph = new JavaGraph();
    }

    public OPALClassHierarchy() {
        this.graph = new JavaGraph();
        this.internalCHA = new ConcurrentHashMap<>();
        this.externalCHA = new ConcurrentHashMap<>();
        this.nodeCount = new AtomicInteger();
    }

    public Map<ObjectType, OPALType> getInternalCHA() {
        return internalCHA;
    }

    public Map<ObjectType, Map<DeclaredMethod, Integer>> getExternalCHA() {
        return externalCHA;
    }

    public AtomicInteger getNodeCount() {
        return nodeCount;
    }

    /**
     * Converts all of the members of the classHierarchy to {@link FastenURI}.
     *
     * @param projectHierarchy OPAL class hierarchy
     * @return A {@link Map} of {@link FastenURI} and {@link JavaType}
     */
    public EnumMap<JavaScope, Map<String, JavaType>> asURIHierarchy(ClassHierarchy projectHierarchy) {

        final Map<String, JavaType> internalResult = new HashMap<>();
        final Map<String, JavaType> externalResult = new HashMap<>();
        final var internals = this.getInternalCHA();
        for (final var aClass : internals.keySet()) {
            final var klass = OPALType.getType(internals.get(aClass), aClass);
            internalResult.put(klass.getLeft(), klass.getRight());
        }
        final var externals = this.getExternalCHA();
        for (final var aClass : externals.keySet()) {
            externalResult
                    .putAll(OPALType.getType(projectHierarchy, externals.get(aClass), aClass));
        }

        return new EnumMap<>(Map.of(JavaScope.internalTypes, internalResult, JavaScope.externalTypes, externalResult,
                JavaScope.resolvedTypes, new HashMap<>()));
    }

    /**
     * An optimized version of `asURIHierarchy`.
     * Converts all of the members of the classHierarchy to {@link FastenURI}.
     *
     * @param projectHierarchy OPAL class hierarchy
     * @return A {@link Map} of {@link FastenURI} and {@link JavaType}
     */
    public EnumMap<JavaScope, Map<String, JavaType>> asURIHierarchyParallel(ClassHierarchy projectHierarchy) {

        final Map<String, JavaType> internalResult = new ConcurrentHashMap<>();
        final Map<String, JavaType> externalResult = new ConcurrentHashMap<>();
        final var internals = this.getInternalCHA();

        internals.keySet().stream().parallel().forEach(aClass -> {
            final var klass = OPALType.getType(internals.get(aClass), aClass);
            internalResult.put(klass.getLeft(), klass.getRight());
        });

        final var externals = this.getExternalCHA();
        externals.keySet().stream().parallel().forEach(aClass -> {
            externalResult
                    .putAll(OPALType.getType(projectHierarchy, externals.get(aClass), aClass));
        });

        return new EnumMap<>(Map.of(JavaScope.internalTypes, internalResult, JavaScope.externalTypes, externalResult,
                JavaScope.resolvedTypes, new HashMap<>()));
    }

    /**
     * Adds a method to the external CHA if the method doesn't already exist.
     * Otherwise returns and ID of the existing method.
     *
     * @param method method to add to external CHA
     * @return ID corresponding to the method
     */
    public int addMethodToExternals(DeclaredMethod method) {
        synchronized (nodeCount) {
            final var klass = method.declaringClassType();
            if (!this.externalCHA.containsKey(klass)) {
                this.externalCHA.put(klass, new Object2ObjectOpenHashMap<>());
            }
            if (this.externalCHA.get(klass).containsKey(method)) {
                return this.externalCHA.get(klass).get(method);
            } else {
                final var key = this.nodeCount.get();
                this.externalCHA.get(klass).put(method, key);
                this.nodeCount.incrementAndGet();
                return key;
            }
        }

    }

    /**
     * Get call keys from an internal class hierarchy.
     *
     * @param source source method
     * @param target target method
     * @return list of call ids
     */
    public IntIntPair getInternalCallKeys(final Method source, final Method target) {
        return IntIntPair.of(
            this.internalCHA.get(source.declaringClassFile().thisType().asObjectType())
                .getMethods().get(source),
            this.internalCHA.get(target.declaringClassFile().thisType().asObjectType())
                .getMethods().get(target));
    }

    /**
     * Get call keys from an external class hierarchy.
     *
     * @param source source method
     * @param target target method
     * @return list of call ids
     */
    public IntIntPair getExternalCallKeys(final Object source, final Object target) {
        if (source instanceof Method && target instanceof DeclaredMethod) {
            return IntIntPair.of(
                this.internalCHA
                    .get(((Method) source).declaringClassFile().thisType().asObjectType())
                    .getMethods().get(source),
                this.addMethodToExternals((DeclaredMethod) target));
        } else if (source instanceof DeclaredMethod && target instanceof Method) {
            return IntIntPair.of(
                this.addMethodToExternals((DeclaredMethod) source),
                this.internalCHA
                    .get(((Method) target).declaringClassFile().thisType().asObjectType())
                    .getMethods().get(target));
        } else if (source instanceof DeclaredMethod) {
            return IntIntPair.of(this.addMethodToExternals((DeclaredMethod) source),
                this.addMethodToExternals((DeclaredMethod) target));
        } else {
            return IntIntPair.of(-1, -1);
        }
    }

    /**
     * Put calls to either internal or external maps of calls.
     *
     * @param source   source method
     * @param target   target method
     * @param metadata metadata to put along the call
     */
    public <E, T> void putCall(final E source,
                               final T target,
                               final Map<Object, Object> metadata) {
        if (source instanceof Method && target instanceof Method) {
            final var call = this.getInternalCallKeys((Method) source, (Method) target);
            this.graph.put(call, getInternalMetadata(metadata, call));
        } else {
            putExternalCall(source, target, metadata);
        }

    }

    /**
     * Put external call to the list of calls.
     *
     * @param source   source method
     * @param target   target method declaration
     * @param metadata metadata to put along the call
     */
    public <T, E> void putExternalCall(final E source,
                                       final T target,
                                       final Map<Object, Object> metadata) {
        final var call = this.getExternalCallKeys(source, target);
        final var externalMetadata = this.graph.getOrDefault(call, new ConcurrentHashMap<>());
        externalMetadata.putAll(metadata);
        this.graph.put(call, externalMetadata);
    }

    /**
     * Get metadata of internal calls.
     *
     * @param metadata new metadata to add
     * @param call     call to add metadata to
     * @return internal metadata
     */
    public Map<Object, Object> getInternalMetadata(final Map<Object, Object> metadata,
                                                   final IntIntPair call) {
        final var internalMetadata = this.graph.getOrDefault(call, new ConcurrentHashMap<>());
        internalMetadata.putAll(metadata);
        return internalMetadata;
    }

    /**
     * Given a source method and a list of targets return a sub-graph of PartialJavaCallGraph.
     *
     * @param source  source method
     * @param targets list of targets
     */
    public void getSubGraph(final Object source,
                            final Iterator<Tuple2<Object, Iterator<DeclaredMethod>>> targets,
                            final Stmt<DUVar<ValueInformation>>[] stmts,
                            final List<Integer> incompeletes,
                            final Set<Integer> visitedPCs, CallPreservationStrategy callSiteOnly) {

        if (targets != null) {
            for (final var opalCallSite : JavaConverters.asJavaIterable(targets.toIterable())) {

                for (final var targetDeclaration : JavaConverters
                    .asJavaIterable(opalCallSite._2().toIterable())) {
                    final var pc = (Integer) opalCallSite._1();
                    incompeletes.remove(pc);
                    if (callSiteOnly == CallPreservationStrategy.ONLY_STATIC_CALLSITES) {
                        if (!visitedPCs.contains(pc)) {
                            processPC(source, stmts, visitedPCs,
                                opalCallSite, targetDeclaration, pc);
                        }
                    } else {
                        processPC(source, stmts, visitedPCs,
                            opalCallSite, targetDeclaration, pc);
                    }
                }
            }
        }
    }

    private void processPC(final Object source, final Stmt<DUVar<ValueInformation>>[] stmts,
                           final Set<Integer> visitedPCs,
                           final Tuple2<Object, Iterator<DeclaredMethod>> opalCallSite,
                           final DeclaredMethod targetDeclaration, final Integer pc) {
        visitedPCs.add(pc);
        Map<Object, Object> metadata = new HashMap<>();
        if (source instanceof Method) {
            metadata = getCallSite((Method) source, (Integer) opalCallSite._1(), stmts);
        }

        if (targetDeclaration.hasMultipleDefinedMethods()) {
            for (final var target : JavaConverters
                .asJavaIterable(targetDeclaration.definedMethods())) {
                this.putCall(source, target, metadata);

            }

        } else if (targetDeclaration.hasSingleDefinedMethod()) {
            this.putCall(source, targetDeclaration.definedMethod(), metadata);

        } else if (targetDeclaration.isVirtualOrHasSingleDefinedMethod()) {
            this.putExternalCall(source, targetDeclaration, metadata);

        }
    }

    /**
     * Get call site for a method.
     *
     * @param source source method
     * @param pc     pc
     * @return call site
     */
    public Map<Object, Object> getCallSite(final Method source, final Integer pc,
                                           Stmt<DUVar<ValueInformation>>[] stmts) {
        final var instructionsOption = source.instructionsOption();
        if (instructionsOption.isEmpty()) {
            return Collections.emptyMap();
        }

        final var instruction = instructionsOption.get()[pc].mnemonic();
        final var receiverType = new HashSet<FastenURI>();

        if (instruction.equals("invokevirtual") | instruction.equals("invokeinterface")) {
            if (stmts != null) {
                for (final var stmt : stmts) {
                    if (stmt.pc() == pc) {

                        try {
                            final UVar<?> value = getValue(stmt);
                            if (value == null) {
                                continue;
                            }
                            final var stmtValue = value.value();
                            final var upperBounds =
                                stmtValue.getClass().getMethod("upperTypeBound").invoke(stmtValue);
                            ((UIDSet<? extends ReferenceType>)upperBounds).foreach(v1 -> receiverType.add(OPALMethod.getTypeURI(v1)));

                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException("A problem occurred while finding receiver " +
                                "type", e);
                        }

                    }
                }
            }

        } else {
            receiverType.add(OPALMethod.getTypeURI(instructionsOption.get()[pc]
                .asMethodInvocationInstruction().declaringClass()));
        }

        var callSite = new HashMap<>();
        callSite.put(Constants.CALLSITE_LINE, source.body().get().lineNumber(pc).getOrElse(() -> 404));
        callSite.put(Constants.INVOCATION_TYPE, instruction);
        callSite.put(Constants.RECEIVER_TYPE, "[" + receiverType.stream().map(FastenURI::toString)
                .reduce((f1, f2) -> f1 + "," + f2).orElse("") + "]");

        return Map.of(pc.toString(), callSite);
    }

    private UVar<?> getValue(Stmt<DUVar<ValueInformation>> stmt) {
        UVar<?> uVar;
        if (stmt.isAssignment()) {
            uVar =
                (UVar) stmt.asAssignment().expr().asVirtualFunctionCall().receiverOption().value();
        } else if (stmt.isExprStmt()) {
            uVar = (UVar) stmt.asExprStmt().expr().asVirtualFunctionCall().receiverOption().value();
        } else if(stmt.isVirtualMethodCall()) {
            uVar = (UVar) stmt.asVirtualMethodCall().receiverOption().value();
        } else if (stmt.isStaticMethodCall()){
            uVar = (UVar) stmt.asStaticMethodCall().receiverOption().get();
        } else if (stmt.isNonVirtualMethodCall()){
            uVar = (UVar) stmt.asNonVirtualMethodCall().receiverOption().value();
        } else {
            uVar = null;
        }
        return uVar;
    }
}
