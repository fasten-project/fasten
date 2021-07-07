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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import eu.fasten.analyzer.javacgopal.data.analysis.OPALClassHierarchy;
import eu.fasten.analyzer.javacgopal.data.analysis.OPALType;
import eu.fasten.core.data.JavaGraph;
import eu.fasten.core.data.JavaScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opalj.br.BaseType;
import org.opalj.br.ClassFile;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.Code;
import org.opalj.br.DeclaredMethod;
import org.opalj.br.FieldType;
import org.opalj.br.Method;
import org.opalj.br.MethodDescriptor;
import org.opalj.br.ObjectType;
import org.opalj.br.instructions.Instruction;
import org.opalj.br.instructions.MethodInvocationInstruction;
import org.opalj.collection.QualifiedCollection;
import org.opalj.collection.immutable.ConstArray;
import org.opalj.collection.immutable.RefArray;
import org.opalj.collection.immutable.UIDSet;
import org.opalj.collection.immutable.UIDSet1;
import org.opalj.tac.Stmt;
import scala.Option;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.mutable.HashSet;

class OPALClassHierarchyTest {

    private static ObjectType type;


    @BeforeAll
    static void setUp() {
        ObjectType wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        BaseType baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        type = Mockito.mock(ObjectType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);
    }

    @Test
    void asURIHierarchy() {
        var qualifiedCollection = Mockito.mock(QualifiedCollection.class);
        Mockito.when(qualifiedCollection.s()).thenReturn(null);

        var uidSet = Mockito.mock(UIDSet.class);
        Mockito.when(uidSet.nonEmpty()).thenReturn(false);

        var uidSetInterfaces = new UIDSet1<>(type);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.supertypes(type)).thenReturn(uidSet);
        Mockito.when(classHierarchy.allSuperclassTypesInInitializationOrder(type))
            .thenReturn(qualifiedCollection);
        Mockito.when(classHierarchy.allSuperinterfacetypes(type, false))
            .thenReturn(uidSetInterfaces);

        var arrayOfParameters = new RefArray<FieldType>(new FieldType[]{type});

        var descriptor = Mockito.mock(MethodDescriptor.class);
        Mockito.when(descriptor.parameterTypes()).thenReturn(arrayOfParameters);
        Mockito.when(descriptor.returnType()).thenReturn(type);

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.descriptor()).thenReturn(descriptor);
        Mockito.when(declaredMethod.name()).thenReturn("methodName");
        Mockito.when(declaredMethod.declaringClassType()).thenReturn(type);

        var method = createMethod();
        Mockito.when(method.isPrivate()).thenReturn(true);

        var methodsInternal = new HashMap<Method, Integer>();
        methodsInternal.put(method, 123);

        var opalTypeInternal = new OPALType(methodsInternal, null, new ArrayList<>(), "source.java", "", false, new HashMap<>());

        var internals = Map.of(type, opalTypeInternal);
        var externals = Map.of(type, Map.of(declaredMethod, 4));

        var opalClassHierarchy = new OPALClassHierarchy(internals, externals, 5);

        var uriHierarchy = opalClassHierarchy.asURIHierarchy(classHierarchy);

        assertNotNull(uriHierarchy);
        assertEquals(1, uriHierarchy.get(JavaScope.internalTypes).size());
        assertEquals(1, uriHierarchy.get(JavaScope.externalTypes).size());

        var internalUri = uriHierarchy
            .get(JavaScope.internalTypes)
            .get("/some.package/typeName");
        var externalUri = uriHierarchy
            .get(JavaScope.externalTypes)
            .get("/some.package/typeName");

        assertEquals("source.java", internalUri.getSourceFileName());
        assertEquals("methodName(/some.package/typeName)/some.package/typeName",
            internalUri.getMethods().get(123).getSignature());
        assertEquals(0, internalUri.getSuperInterfaces().size());
        assertEquals(0, internalUri.getSuperClasses().size());

        assertEquals("", externalUri.getSourceFileName());
        assertEquals("methodName(/some.package/typeName)/some.package/typeName",
            externalUri.getMethods().get(4).getSignature());
        assertEquals(1, externalUri.getSuperInterfaces().size());
        assertEquals("/some.package/typeName",
            externalUri.getSuperInterfaces().get(0).toString());
        assertEquals(0, externalUri.getSuperClasses().size());
    }

    private Method createMethod() {
        var arrayOfParameters = new RefArray<FieldType>(new FieldType[]{type});

        var descriptor = Mockito.mock(MethodDescriptor.class);
        Mockito.when(descriptor.parameterTypes()).thenReturn(arrayOfParameters);
        Mockito.when(descriptor.returnType()).thenReturn(type);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(type);

        var code = Mockito.mock(Code.class);
        Mockito.when(code.firstLineNumber()).thenReturn(Option.apply(10));
        Mockito.when(code.lineNumber(20)).thenReturn(Option.apply(30));
        Mockito.when(code.codeSize()).thenReturn(20);

        var method = Mockito.mock(Method.class);
        Mockito.when(method.descriptor()).thenReturn(descriptor);
        Mockito.when(method.name()).thenReturn("methodName");
        Mockito.when(method.declaringClassFile()).thenReturn(classFile);
        Mockito.when(method.body()).thenReturn(Option.apply(code));
        Mockito.when(method.instructionsOption()).thenReturn(Option.apply(new Instruction[]{}));

        return method;
    }

    @Test
    void addMethodToExternalsMethodExists() {
        var objectType = Mockito.mock(ObjectType.class);

        var method = Mockito.mock(DeclaredMethod.class);
        Mockito.when(method.declaringClassType()).thenReturn(objectType);

        var externals = new HashMap<ObjectType, Map<DeclaredMethod, Integer>>();
        var externalCall = Map.of(method, 123);
        externals.put(objectType, externalCall);

        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), externals, 5);

        assertEquals(123, classHierarchy.addMethodToExternals(method));
    }

    @Test
    void addMethodToExternalsNoMethod() {
        var method = Mockito.mock(DeclaredMethod.class);
        Mockito.when(method.declaringClassType()).thenReturn(type);

        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5);

        assertEquals(5, classHierarchy.addMethodToExternals(method));
    }

    @Test
    void getInternalCallKeys() {
        var objectType = Mockito.mock(ObjectType.class);

        var thisType = Mockito.mock(ObjectType.class);
        Mockito.when(thisType.asObjectType()).thenReturn(objectType);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(thisType);

        var source = Mockito.mock(Method.class);
        Mockito.when(source.declaringClassFile()).thenReturn(classFile);
        var target = Mockito.mock(Method.class);
        Mockito.when(target.declaringClassFile()).thenReturn(classFile);

        var methods = new HashMap<Method, Integer>();
        methods.put(source, 123);
        methods.put(target, 234);

        var type = new OPALType(methods, new LinkedList<>(), new ArrayList<>(), "source.java", "", false, new HashMap<>());

        var internal = new HashMap<ObjectType, OPALType>();
        internal.put(objectType, type);

        var classHierarchy = new OPALClassHierarchy(internal, new HashMap<>(), 5);

        var internalKeys = classHierarchy.getInternalCallKeys(source, target);

        assertEquals(2, internalKeys.size());
        assertEquals(123, internalKeys.get(0).intValue());
        assertEquals(234, internalKeys.get(1).intValue());
    }

    @Test
    void getExternalCallKeysSourceMethodTargetDeclaredMethod() {
        var objectType = Mockito.mock(ObjectType.class);

        var thisType = Mockito.mock(ObjectType.class);
        Mockito.when(thisType.asObjectType()).thenReturn(objectType);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(thisType);

        var source = Mockito.mock(Method.class);
        Mockito.when(source.declaringClassFile()).thenReturn(classFile);
        var target = Mockito.mock(DeclaredMethod.class);

        var methods = new HashMap<Method, Integer>();
        methods.put(source, 123);

        var type = new OPALType(methods, new LinkedList<>(), new ArrayList<>(), "source.java", "", false, new HashMap<>());

        var internal = new HashMap<ObjectType, OPALType>();
        internal.put(objectType, type);

        var classHierarchy = new OPALClassHierarchy(internal, new HashMap<>(), 5);

        var externalKeys = classHierarchy.getExternalCallKeys(source, target);

        assertEquals(2, externalKeys.size());
        assertEquals(123, externalKeys.get(0).intValue());
        assertEquals(5, externalKeys.get(1).intValue());
    }

    @Test
    void getExternalCallKeysSourceDeclaredMethodTargetMethod() {
        var objectType = Mockito.mock(ObjectType.class);

        var thisType = Mockito.mock(ObjectType.class);
        Mockito.when(thisType.asObjectType()).thenReturn(objectType);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(thisType);

        var source = Mockito.mock(DeclaredMethod.class);
        var target = Mockito.mock(Method.class);
        Mockito.when(target.declaringClassFile()).thenReturn(classFile);

        var methods = new HashMap<Method, Integer>();
        methods.put(target, 123);

        var type = new OPALType(methods, new LinkedList<>(), new ArrayList<>(), "source.java", "", false, new HashMap<>());

        var internal = new HashMap<ObjectType, OPALType>();
        internal.put(objectType, type);

        var classHierarchy = new OPALClassHierarchy(internal, new HashMap<>(), 5);

        var externalKeys = classHierarchy.getExternalCallKeys(source, target);

        assertEquals(2, externalKeys.size());
        assertEquals(5, externalKeys.get(0).intValue());
        assertEquals(123, externalKeys.get(1).intValue());
    }

    @Test
    void getExternalCallKeysSourceDeclaredMethodTargetDeclaredMethod() {
        var source = Mockito.mock(DeclaredMethod.class);
        var target = Mockito.mock(DeclaredMethod.class);

        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5);

        var externalKeys = classHierarchy.getExternalCallKeys(source, target);

        assertEquals(2, externalKeys.size());
        assertEquals(5, externalKeys.get(0).intValue());
        assertEquals(6, externalKeys.get(1).intValue());
    }

    @Test
    void getExternalCallKeysWrongTypes() {
        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5);

        assertEquals(0, classHierarchy.getExternalCallKeys(new Object(), new Object()).size());
    }

    @Test
    void getExternalCallKeysSourceMethodTargetWrongType() {
        var source = Mockito.mock(Method.class);

        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5);

        assertEquals(0, classHierarchy.getExternalCallKeys(source, new Object()).size());
    }

    @Test
    void putCallsSourceMethod() {
        var objectType = Mockito.mock(ObjectType.class);

        var thisType = Mockito.mock(ObjectType.class);
        Mockito.when(thisType.asObjectType()).thenReturn(objectType);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(thisType);

        var source = Mockito.mock(Method.class);
        Mockito.when(source.declaringClassFile()).thenReturn(classFile);
        var target = Mockito.mock(Method.class);
        Mockito.when(target.declaringClassFile()).thenReturn(classFile);

        var methods = new HashMap<Method, Integer>();
        methods.put(source, 123);
        methods.put(target, 234);

        var type = new OPALType(methods, new LinkedList<>(), new ArrayList<>(), "source.java", "", false, new HashMap<>());

        var internal = new HashMap<ObjectType, OPALType>();
        internal.put(objectType, type);

        var classHierarchy = new OPALClassHierarchy(internal, new HashMap<>(), 5);

        var internalCalls = new HashMap<List<Integer>, Map<Object, Object>>();
        var internalCallKeys = classHierarchy.getInternalCallKeys(source, target);
        internalCalls.put(internalCallKeys, new HashMap<>());

        var externalCalls = new HashMap<List<Integer>, Map<Object, Object>>();

        var newMetadata = new HashMap<>();
        newMetadata.put(10, "newMetadata");

        assertEquals(0, internalCalls.get(internalCallKeys).size());

        classHierarchy.putCalls(source, internalCalls, externalCalls, null, newMetadata, target
        );

        assertEquals(1, internalCalls.get(internalCallKeys).size());
        assertEquals("newMetadata", internalCalls.get(internalCallKeys).get(10));
    }

    @Test
    void putCallsSourceDeclaredMethod() {
        var objectType = Mockito.mock(ObjectType.class);

        var thisType = Mockito.mock(ObjectType.class);
        Mockito.when(thisType.asObjectType()).thenReturn(objectType);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(thisType);

        var source = Mockito.mock(DeclaredMethod.class);
        var target = Mockito.mock(Method.class);
        Mockito.when(target.declaringClassFile()).thenReturn(classFile);

        var type = new OPALType(new HashMap<>(), new LinkedList<>(), new ArrayList<>(), "source.java", "", false, new HashMap<>());

        var internal = new HashMap<ObjectType, OPALType>();
        internal.put(objectType, type);

        var classHierarchy = new OPALClassHierarchy(internal, new HashMap<>(), 5);

        var externalCalls = new HashMap<List<Integer>, Map<Object, Object>>();
        externalCalls.put(List.of(5, 6), new HashMap<>());

        var internalCalls = new HashMap<List<Integer>, Map<Object, Object>>();

        var newMetadata = new HashMap<>();
        newMetadata.put(10, "newMetadata");

        assertEquals(0, externalCalls.get(List.of(5, 6)).size());

        classHierarchy.putCalls(source, internalCalls, externalCalls,
            Mockito.mock(DeclaredMethod.class), newMetadata, target);

        assertEquals(1, externalCalls.get(List.of(5, 6)).size());
        assertEquals("newMetadata", externalCalls.get(List.of(5, 6)).get(10));
    }

    @Test
    void putCallsTargetConstructor() {
        var objectType = Mockito.mock(ObjectType.class);

        var thisType = Mockito.mock(ObjectType.class);
        Mockito.when(thisType.asObjectType()).thenReturn(objectType);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(thisType);

        var source = Mockito.mock(DeclaredMethod.class);
        var target = Mockito.mock(Method.class);
        Mockito.when(target.declaringClassFile()).thenReturn(classFile);
        Mockito.when(target.isConstructor()).thenReturn(true);

        var methods = new HashMap<Method, Integer>();
        methods.put(target, 6);

        var type = new OPALType(methods, new LinkedList<>(), new ArrayList<>(), "source.java", "", false, new HashMap<>());

        var internal = new HashMap<ObjectType, OPALType>();
        internal.put(objectType, type);

        var classHierarchy = new OPALClassHierarchy(internal, new HashMap<>(), 5);

        var externalCalls = new HashMap<List<Integer>, Map<Object, Object>>();
        externalCalls.put(List.of(5, 6), new HashMap<>());

        var internalCalls = new HashMap<List<Integer>, Map<Object, Object>>();

        var newMetadata = new HashMap<>();
        newMetadata.put(10, "newMetadata");

        assertEquals(0, externalCalls.get(List.of(5, 6)).size());

        classHierarchy.putCalls(source, internalCalls, externalCalls,
            Mockito.mock(DeclaredMethod.class), newMetadata, target);

        assertEquals(1, externalCalls.size());
        assertEquals("newMetadata", externalCalls.get(List.of(5, 6)).get(10));
        assertNull(externalCalls.get(List.of(6, 6)));
    }

    @Test
    void putExternalCall() {
        var source = Mockito.mock(DeclaredMethod.class);
        var target = Mockito.mock(DeclaredMethod.class);

        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5);

        assertEquals(0, classHierarchy.getExternalCHA().size());

        var externalKeys = classHierarchy.getExternalCallKeys(source, target);
        var calls = new HashMap<List<Integer>, Map<Object, Object>>();
        classHierarchy.putExternalCall(source, calls, target, new HashMap<>());

        assertEquals(1, classHierarchy.getExternalCHA().size());
        assertNotNull(calls.get(externalKeys));
    }

    @Test
    void getInternalMetadata() {
        var callKeys = new ArrayList<Integer>();
        callKeys.add(1);
        callKeys.add(2);

        var internalMetadata = new HashMap<>();

        var internalCalls = new HashMap<List<Integer>, Map<Object, Object>>();
        internalCalls.put(callKeys, internalMetadata);

        var metadata = new HashMap<>();
        metadata.put(123, "testMetadata");

        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5);

        assertEquals(0, internalMetadata.size());

        var internalMetadataUpdated = classHierarchy.getInternalMetadata(internalCalls, metadata, callKeys);

        assertEquals(1, internalMetadata.size());
        assertEquals(internalMetadata, internalMetadataUpdated);

        assertEquals("testMetadata", internalMetadata.get(123));
    }

    @Test
    void appendGraph() {
        OPALClassHierarchy classHierarchy =
            Mockito.spy(new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5));
        var newGraph = Mockito.mock(JavaGraph.class);
        var existingGraph = Mockito.mock(JavaGraph.class);
        final var incompeletes = new ArrayList<Integer>();
        final Set<Integer> visitedPCs = new java.util.HashSet<>();

        Mockito.doReturn(newGraph).when(classHierarchy).getSubGraph(Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(false));

        var source = Mockito.mock(Method.class);
        var targets = new HashSet<Tuple2<Object, Iterator<DeclaredMethod>>>().iterator();
        var stmts = new Stmt[] { Mockito.mock(Stmt.class) };

        classHierarchy.appendGraph(source, targets, stmts, existingGraph, incompeletes, visitedPCs,
            false);

        Mockito.verify(classHierarchy, Mockito.times(1)).getSubGraph(source, targets, stmts,
            incompeletes, visitedPCs, false);
        Mockito.verify(existingGraph, Mockito.times(1)).append(newGraph);
    }

    @Test
    void getSubGraphTargetDeclarationNoDefinition() {
        OPALClassHierarchy classHierarchy =
            Mockito.spy(new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5));

        Mockito.doNothing().when(classHierarchy)
            .putCalls(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doNothing().when(classHierarchy)
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );

        var callSite = new HashMap<String, Object>();
        callSite.put("line", 20);
        callSite.put("type", "testType");
        callSite.put("receiver", "testReceiver");
        Mockito.doReturn(Map.of(1, callSite))
            .when(classHierarchy).getCallSite(Mockito.any(), Mockito.any(), Mockito.any());


        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        var targetDeclarations = new HashSet<DeclaredMethod>();
        targetDeclarations.add(declaredMethod);

        var tuple = new Tuple2<Object, Iterator<DeclaredMethod>>(1, targetDeclarations.iterator());
        var tupleSet = new HashSet<Tuple2<Object, Iterator<DeclaredMethod>>>();
        tupleSet.add(tuple);
        var stmts = new Stmt[] { Mockito.mock(Stmt.class) };

        var source = Mockito.mock(Method.class);
        final var incompeletes = new ArrayList<Integer>();
        final Set<Integer> visitedPCs = new java.util.HashSet<>();

        classHierarchy.getSubGraph(source, tupleSet.iterator(), stmts, incompeletes, visitedPCs,
            false);

        Mockito.verify(classHierarchy, Mockito.times(1)).getCallSite(source, 1, stmts);
        Mockito.verify(classHierarchy, Mockito.never()).putCalls(Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(classHierarchy, Mockito.never())
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
    }

    @Test
    void getSubGraphTargetDeclarationMultipleDefinitions() {
        OPALClassHierarchy classHierarchy =
            Mockito.spy(new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5));

        Mockito.doNothing().when(classHierarchy)
            .putCalls(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doNothing().when(classHierarchy)
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
        var callSite = new HashMap<String, Object>();
        var stmts = new Stmt[] { Mockito.mock(Stmt.class) };

        callSite.put("line", 20);
        callSite.put("type", "testType");
        callSite.put("receiver", "testReceiver");
        Mockito.doReturn(Map.of(1, callSite))
            .when(classHierarchy).getCallSite(Mockito.any(), Mockito.any(), Mockito.eq(stmts));

        var method = Mockito.mock(Method.class);
        var arr = ConstArray._UNSAFE_from(new Method[]{method, method});

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.hasMultipleDefinedMethods()).thenReturn(true);
        Mockito.when(declaredMethod.definedMethods()).thenReturn(arr);

        var targetDeclarations = new HashSet<DeclaredMethod>();
        targetDeclarations.add(declaredMethod);

        var tuple = new Tuple2<Object, Iterator<DeclaredMethod>>(1, targetDeclarations.iterator());
        var tupleSet = new HashSet<Tuple2<Object, Iterator<DeclaredMethod>>>();
        tupleSet.add(tuple);
        final var incompeletes = new ArrayList<Integer>();
        final Set<Integer> visitedPCs = new java.util.HashSet<>();

        var source = Mockito.mock(Method.class);
        classHierarchy.getSubGraph(source, tupleSet.iterator(), stmts, incompeletes, visitedPCs,
            false);

        Mockito.verify(classHierarchy, Mockito.times(1)).getCallSite(source, 1, stmts);
        Mockito.verify(classHierarchy, Mockito.times(2)).putCalls(Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(classHierarchy, Mockito.never())
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
    }

    @Test
    void getSubGraphTargetDeclarationSingleDefinition() {
        OPALClassHierarchy classHierarchy =
            Mockito.spy(new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5));

        Mockito.doNothing().when(classHierarchy)
            .putCalls(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doNothing().when(classHierarchy)
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
        var callSite = new HashMap<String, Object>();
        var stmts = new Stmt[] { Mockito.mock(Stmt.class) };

        callSite.put("line", 20);
        callSite.put("type", "testType");
        callSite.put("receiver", "testReceiver");
        Mockito.doReturn(Map.of(1, callSite))
            .when(classHierarchy).getCallSite(Mockito.any(), Mockito.any(), Mockito.eq(stmts));

        var method = Mockito.mock(Method.class);

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.hasSingleDefinedMethod()).thenReturn(true);
        Mockito.when(declaredMethod.definedMethod()).thenReturn(method);

        var targetDeclarations = new HashSet<DeclaredMethod>();
        targetDeclarations.add(declaredMethod);

        var tuple = new Tuple2<Object, Iterator<DeclaredMethod>>(1, targetDeclarations.iterator());
        var tupleSet = new HashSet<Tuple2<Object, Iterator<DeclaredMethod>>>();
        tupleSet.add(tuple);

        var source = Mockito.mock(Method.class);
        final var incompeletes = new ArrayList<Integer>();
        final Set<Integer> visitedPCs = new java.util.HashSet<>();

        classHierarchy.getSubGraph(source, tupleSet.iterator(), stmts, incompeletes, visitedPCs,
            false);

        Mockito.verify(classHierarchy, Mockito.times(1)).getCallSite(source, 1, stmts);
        Mockito.verify(classHierarchy, Mockito.times(1)).putCalls(Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(classHierarchy, Mockito.never())
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
    }

    @Test
    void getSubGraphTargetDeclarationVirtual() {
        OPALClassHierarchy classHierarchy =
            Mockito.spy(new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5));

        Mockito.doNothing().when(classHierarchy)
            .putCalls(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doNothing().when(classHierarchy)
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
        var callSite = new HashMap<String, Object>();
        var stmts = new Stmt[] { Mockito.mock(Stmt.class) };

        callSite.put("line", 20);
        callSite.put("type", "testType");
        callSite.put("receiver", "testReceiver");
        Mockito.doReturn(Map.of(1, callSite))
            .when(classHierarchy).getCallSite(Mockito.any(), Mockito.any(), Mockito.eq(stmts));

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.isVirtualOrHasSingleDefinedMethod()).thenReturn(true);

        var targetDeclarations = new HashSet<DeclaredMethod>();
        targetDeclarations.add(declaredMethod);

        var tuple = new Tuple2<Object, Iterator<DeclaredMethod>>(1, targetDeclarations.iterator());
        var tupleSet = new HashSet<Tuple2<Object, Iterator<DeclaredMethod>>>();
        tupleSet.add(tuple);

        var source = Mockito.mock(Method.class);
        final var incompeletes = new ArrayList<Integer>();
        final Set<Integer> visitedPCs = new java.util.HashSet<>();

        classHierarchy.getSubGraph(source, tupleSet.iterator(), stmts, incompeletes, visitedPCs,
            false);

        Mockito.verify(classHierarchy, Mockito.times(1)).getCallSite(source, 1, stmts);
        Mockito.verify(classHierarchy, Mockito.times(0)).putCalls(Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(classHierarchy, Mockito.times(1))
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
    }

    @Test
    void getSubGraphSourceWrongType() {
        OPALClassHierarchy classHierarchy =
            Mockito.spy(new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5));

        Mockito.doNothing().when(classHierarchy)
            .putCalls(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doNothing().when(classHierarchy)
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
        var stmts = new Stmt[] { Mockito.mock(Stmt.class) };

        var callSite = new HashMap<String, Object>();
        callSite.put("line", 20);
        callSite.put("type", "testType");
        callSite.put("receiver", "testReceiver");
        Mockito.doReturn(Map.of(1, callSite))
            .when(classHierarchy).getCallSite(Mockito.any(), Mockito.any(), Mockito.eq(stmts));


        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        var targetDeclarations = new HashSet<DeclaredMethod>();
        targetDeclarations.add(declaredMethod);

        var tuple = new Tuple2<Object, Iterator<DeclaredMethod>>(1, targetDeclarations.iterator());
        var tupleSet = new HashSet<Tuple2<Object, Iterator<DeclaredMethod>>>();
        tupleSet.add(tuple);

        var source = Mockito.mock(DeclaredMethod.class);
        final var incompeletes = new ArrayList<Integer>();
        final Set<Integer> visitedPCs = new java.util.HashSet<>();

        classHierarchy.getSubGraph(source, tupleSet.iterator(), stmts, incompeletes, visitedPCs,
            false);

        Mockito.verify(classHierarchy, Mockito.times(0)).getCallSite(Mockito.any(), Mockito.any(),
            Mockito.eq(stmts));
        Mockito.verify(classHierarchy, Mockito.never()).putCalls(Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(classHierarchy, Mockito.never())
            .putExternalCall(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
            );
    }

    @Test
    void getCallSite() {
        var methodInvocationInstruction = Mockito.mock(MethodInvocationInstruction.class);
        Mockito.when(methodInvocationInstruction.declaringClass()).thenReturn(type);

        var instruction = Mockito.mock(Instruction.class);
        Mockito.when(instruction.mnemonic()).thenReturn("testType");
        Mockito.when(instruction.asMethodInvocationInstruction()).thenReturn(methodInvocationInstruction);

        var code = Mockito.mock(Code.class);
        Mockito.when(code.lineNumber(0)).thenReturn(Option.apply(30));

        var source = Mockito.mock(Method.class);
        Mockito.when(source.instructionsOption())
            .thenReturn(Option.apply(new Instruction[]{instruction}));
        Mockito.when(source.body()).thenReturn(Option.apply(code));

        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5);
        var stmts = new Stmt[] { Mockito.mock(Stmt.class) };

        var callSite = classHierarchy.getCallSite(source, 0, stmts);

        assertNotNull(callSite);

        assertEquals(30, ((HashMap<String, Object>) callSite.get("0")).get("line"));
        assertEquals("[/some.package/typeName]",
            ((HashMap<String, Object>) callSite.get("0")).get("receiver"));
        assertEquals("testType", ((HashMap<String, Object>) callSite.get("0")).get("type"));
    }
}