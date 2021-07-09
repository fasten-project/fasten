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

import eu.fasten.core.data.*;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

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

    private final Map<ObjectType, OPALType> internalCHA;
    private final Map<ObjectType, Map<DeclaredMethod, Integer>> externalCHA;
    private int nodeCount;

    /**
     * Class hierarchy constructor.
     *
     * @param internalCHA class hierarchy containing project specific entries
     * @param externalCHA class hierarchy containing entries from outside the project scope
     * @param nodeCount   number of nodes
     */
    public OPALClassHierarchy(Map<ObjectType, OPALType> internalCHA,
                              Map<ObjectType, Map<DeclaredMethod, Integer>> externalCHA,
                              int nodeCount) {
        this.internalCHA = internalCHA;
        this.externalCHA = externalCHA;
        this.nodeCount = nodeCount;
    }

    public Map<ObjectType, OPALType> getInternalCHA() {
        return internalCHA;
    }

    public Map<ObjectType, Map<DeclaredMethod, Integer>> getExternalCHA() {
        return externalCHA;
    }

    public int getNodeCount() {
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
     * Adds a method to the external CHA if the method doesn't already exist.
     * Otherwise returns and ID of the existing method.
     *
     * @param method method to add to external CHA
     * @return ID corresponding to the method
     */
    public int addMethodToExternals(DeclaredMethod method) {
        final var typeMethods = this.externalCHA
                .getOrDefault(method.declaringClassType(), new HashMap<>());

        if (typeMethods.containsKey(method)) {
            return typeMethods.get(method);
        } else {
            typeMethods.put(method, this.nodeCount);
            this.externalCHA.put(method.declaringClassType(), typeMethods);
            this.nodeCount++;
            return this.nodeCount - 1;
        }
    }

    /**
     * Get call keys from an internal class hierarchy.
     *
     * @param source source method
     * @param target target method
     * @return list of call ids
     */
    public List<Integer> getInternalCallKeys(final Method source, final Method target) {
        return Arrays.asList(
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
    public List<Integer> getExternalCallKeys(final Object source, final Object target) {
        if (source instanceof Method && target instanceof DeclaredMethod) {
            return Arrays.asList(
                    this.internalCHA
                            .get(((Method) source).declaringClassFile().thisType().asObjectType())
                            .getMethods().get(source),
                    this.addMethodToExternals((DeclaredMethod) target));
        } else if (source instanceof DeclaredMethod && target instanceof Method) {
            return Arrays.asList(
                    this.addMethodToExternals((DeclaredMethod) source),
                    this.internalCHA
                            .get(((Method) target).declaringClassFile().thisType().asObjectType())
                            .getMethods().get(target));
        } else if (source instanceof DeclaredMethod) {
            return Arrays.asList(this.addMethodToExternals((DeclaredMethod) source),
                    this.addMethodToExternals((DeclaredMethod) target));
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Put calls to either internal or external maps of calls.
     *
     * @param source            source method
     * @param internalCalls     map of internal calls
     * @param externalCalls     map of external calls
     * @param targetDeclaration target method declaration
     * @param metadata          metadata to put along the call
     * @param target            target method
     */
    public void putCalls(final Object source,
                         final HashMap<List<Integer>, Map<Object, Object>> internalCalls,
                         final HashMap<List<Integer>, Map<Object, Object>> externalCalls,
                         final DeclaredMethod targetDeclaration, Map<Object, Object> metadata,
                         final Method target) {
        if (source instanceof Method) {
            final var call = this.getInternalCallKeys((Method) source, target);
            internalCalls.put(call, getInternalMetadata(internalCalls, metadata, call));
        } else {
            putExternalCall(source, externalCalls, targetDeclaration, metadata);
        }

    }

    /**
     * Put external call to the list of calls.
     *
     * @param source            source method
     * @param externalCalls     map of external calls
     * @param targetDeclaration target method declaration
     * @param metadata          metadata to put along the call
     */
    public void putExternalCall(final Object source,
                                final HashMap<List<Integer>, Map<Object, Object>> externalCalls,
                                final DeclaredMethod targetDeclaration,
                                final Map<Object, Object> metadata) {
        final var call = this.getExternalCallKeys(source, targetDeclaration);
        final var externalMetadata = externalCalls.getOrDefault(call, new HashMap<>());
        externalMetadata.putAll(metadata);
        externalCalls.put(call, externalMetadata);
    }

    /**
     * Get metadata of internal calls.
     *
     * @param ic       map of internal calls
     * @param metadata new metadata to add
     * @param call     call to add metadata to
     * @return internal metadata
     */
    public Map<Object, Object> getInternalMetadata(final Map<List<Integer>, Map<Object, Object>> ic,
                                                   final Map<Object, Object> metadata,
                                                   final List<Integer> call) {
        final var internalMetadata = ic.getOrDefault(call, new HashMap<>());
        internalMetadata.putAll(metadata);
        return internalMetadata;
    }

    /**
     * Append a sub-graph to already existing ExtendedRevisionJavaCallGraph.
     *  @param source      source method
     * @param targets     list of targets
     * @param resultGraph already existing ExtendedRevisionJavaCallGraph
     * @param callSiteOnly
     */
    public void appendGraph(final Object source,
                            final Iterator<Tuple2<Object, Iterator<DeclaredMethod>>> targets,
                            final Stmt<DUVar<ValueInformation>>[] stmts,
                            final JavaGraph resultGraph, List<Integer> incompeletes,
                            final Set<Integer> visitedPCs, final boolean callSiteOnly) {
        final var edges = this.getSubGraph(source, targets, stmts, incompeletes, visitedPCs, callSiteOnly);
        resultGraph.append(edges);
    }

    /**
     * Given a source method and a list of targets return a sub-graph of ExtendedRevisionJavaCallGraph.
     *
     * @param source  source method
     * @param targets list of targets
     * @param callSiteOnly
     * @return ExtendedRevisionJavaCallGraph sub-graph
     */
    public JavaGraph getSubGraph(final Object source,
                             final Iterator<Tuple2<Object, Iterator<DeclaredMethod>>> targets,
                             final Stmt<DUVar<ValueInformation>>[] stmts,
                             final List<Integer> incompeletes,
                             final Set<Integer> visitedPCs, boolean callSiteOnly) {

        final var callSites = new HashMap<List<Integer>, Map<Object, Object>>();

        if (targets != null) {
            for (final var opalCallSite : JavaConverters.asJavaIterable(targets.toIterable())) {

                for (final var targetDeclaration : JavaConverters
                    .asJavaIterable(opalCallSite._2().toIterable())) {
                    final var pc = (Integer) opalCallSite._1();
                    incompeletes.remove(pc);
                    if (!callSiteOnly) {
                        processPC(source, stmts, visitedPCs, callSites, callSites,
                            opalCallSite, targetDeclaration, pc);
                    } else {
                        if (!visitedPCs.contains(pc)) {
                            processPC(source, stmts, visitedPCs, callSites, callSites,
                                opalCallSite, targetDeclaration, pc);
                        }
                    }
                }
            }
        }
        return new JavaGraph(convert(callSites));
    }

    private void processPC(final Object source, final Stmt<DUVar<ValueInformation>>[] stmts,
                           final Set<Integer> visitedPCs,
                           final HashMap<List<Integer>, Map<Object, Object>> internalCalls,
                           final HashMap<List<Integer>, Map<Object, Object>> externalCalls,
                           final Tuple2<Object, Iterator<DeclaredMethod>> opalCallSite,
                           final DeclaredMethod targetDeclaration, final Integer pc) {
        visitedPCs.add(pc);
        Map<Object, Object> metadata = new HashMap<>();
        if (source instanceof Method) {
            metadata = getCallSite((Method) source, (Integer) opalCallSite._1(),
                stmts);
        }

        if (targetDeclaration.hasMultipleDefinedMethods()) {
            for (final var target : JavaConverters
                .asJavaIterable(targetDeclaration.definedMethods())) {
                this.putCalls(source, internalCalls, externalCalls,
                    targetDeclaration,
                    metadata, target);
            }

        } else if (targetDeclaration.hasSingleDefinedMethod()) {
            this.putCalls(source, internalCalls, externalCalls, targetDeclaration,
                metadata, targetDeclaration.definedMethod());

        } else if (targetDeclaration.isVirtualOrHasSingleDefinedMethod()) {
            this.putExternalCall(source, externalCalls, targetDeclaration,
                metadata);
        }
    }

    // Conversion from List<Integer> to IntIntPair
	private HashMap<IntIntPair, Map<Object, Object>> convert(final HashMap<List<Integer>, Map<Object, Object>> externalCalls) {
		final HashMap<IntIntPair, Map<Object, Object>> result = new HashMap<>();
		for (final var e : externalCalls.entrySet()) {
			final List<Integer> key = e.getKey();
			result.put(IntIntPair.of(key.get(0), key.get(1)), e.getValue());
		}
		return result;
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
        final var instruction = source.instructionsOption().get()[pc].mnemonic();
        final var receiverType = new HashSet<FastenURI>();

        if (instruction.equals("invokevirtual") | instruction.equals("invokeinterface")) {
            if (stmts != null) {
                for (final var stmt : stmts) {
                    if (stmt.pc() == pc) {

                        try {
                            final var stmtValue = getValue(stmt).value();
                            final var upperBounds =
                                stmtValue.getClass().getMethod("upperTypeBound").invoke(stmtValue);
                            ((UIDSet<? extends ReferenceType>)upperBounds).foreach(v1 -> receiverType.add(OPALMethod.getTypeURI(v1)));

                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                            throw new RuntimeException("A problem occurred while finding receiver " +
                                "type");
                        }

                    }
                }
            }

        } else {
            receiverType.add(OPALMethod.getTypeURI(source.instructionsOption().get()[pc]
                .asMethodInvocationInstruction().declaringClass()));
        }

        var callSite = new HashMap<>();
        callSite.put("line", source.body().get().lineNumber(pc).getOrElse(() -> 404));
        callSite.put("type", instruction);
        callSite.put("receiver", "[" + receiverType.stream().map(FastenURI::toString)
                .reduce((f1, f2) -> f1 + "," + f2).orElse("") + "]");

        return Map.of(pc.toString(), callSite);
    }

    private UVar<?> getValue(Stmt<DUVar<ValueInformation>> stmt) {
        UVar<?> uVar;
        if (stmt.isAssignment()) {
            uVar =
                (UVar) stmt.asAssignment().expr().asVirtualFunctionCall().receiverOption()
                    .value();
        } else if (stmt.isExprStmt()) {
            uVar = (UVar) stmt.asExprStmt().expr().asVirtualFunctionCall().receiverOption()
                .value();
        } else {
            uVar = (UVar) stmt.asVirtualMethodCall().receiverOption()
                .value();
        }
        return uVar;
    }
}
