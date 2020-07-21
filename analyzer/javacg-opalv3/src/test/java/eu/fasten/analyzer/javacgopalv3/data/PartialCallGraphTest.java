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

package eu.fasten.analyzer.javacgopalv3.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgopalv3.data.analysis.OPALCallSite;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
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
import org.opalj.br.analyses.Project;
import org.opalj.br.instructions.Instruction;
import org.opalj.collection.QualifiedCollection;
import org.opalj.collection.immutable.ConstArray;
import org.opalj.collection.immutable.RefArray;
import org.opalj.collection.immutable.UIDSet;
import org.opalj.collection.immutable.UIDSet1;
import org.opalj.tac.cg.CallGraph;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.mutable.HashSet;

class PartialCallGraphTest {

    private static PartialCallGraph singleCallCG;

    @BeforeAll
    static void setUp() {
        singleCallCG = new PartialCallGraph(new CallGraphConstructor(
                new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                        .getResource("SingleSourceToTarget.class")).getFile()), "", "CHA"));
    }

    @Test
    void getClassHierarchy() {
        var cha = singleCallCG.getClassHierarchy();

        assertNotNull(cha);
        assertNotNull(cha.get(ExtendedRevisionCallGraph.Scope.internalTypes));
        assertEquals(1, cha.get(ExtendedRevisionCallGraph.Scope.internalTypes).size());
        assertEquals(1, cha.get(ExtendedRevisionCallGraph.Scope.externalTypes).size());
        assertEquals(0, cha.get(ExtendedRevisionCallGraph.Scope.resolvedTypes).size());

        // -------
        // Check internal types
        // -------
        var SSTTInternalType = cha.get(ExtendedRevisionCallGraph.Scope.internalTypes)
                .get(FastenURI.create("/name.space/SingleSourceToTarget"));

        // Check filename
        Assertions.assertEquals("SingleSourceToTarget.java", SSTTInternalType.getSourceFileName());

        // Check super interfaces and classes
        Assertions.assertEquals(0, SSTTInternalType.getSuperInterfaces().size());
        Assertions.assertEquals(1, SSTTInternalType.getSuperClasses().size());
        Assertions.assertEquals(FastenURI.create("/java.lang/Object"), SSTTInternalType.getSuperClasses().get(0));

        // Check methods
        Assertions.assertEquals(3, SSTTInternalType.getMethods().size());

        Assertions.assertEquals(FastenURI.create("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType"),
                SSTTInternalType.getMethods().get(0).getUri());
        Assertions.assertEquals("public", SSTTInternalType.getMethods().get(0).getMetadata().get("access"));
        Assertions.assertEquals(true, SSTTInternalType.getMethods().get(0).getMetadata().get("defined"));
        Assertions.assertEquals(3, SSTTInternalType.getMethods().get(0).getMetadata().get("first"));
        Assertions.assertEquals(3, SSTTInternalType.getMethods().get(0).getMetadata().get("last"));

        // -------
        // Check external types
        // -------
        var SSTTExternalType = cha.get(ExtendedRevisionCallGraph.Scope.externalTypes)
                .get(FastenURI.create("/java.lang/Object"));

        // Check super interfaces and classes
        Assertions.assertEquals(0, SSTTExternalType.getSuperInterfaces().size());
        Assertions.assertEquals(0, SSTTExternalType.getSuperClasses().size());

        // Check methods
        Assertions.assertEquals(1, SSTTExternalType.getMethods().size());

        Assertions.assertEquals(FastenURI.create("/java.lang/Object.Object()VoidType"),
                SSTTExternalType.getMethods().get(3).getUri());
        Assertions.assertEquals(0, SSTTExternalType.getMethods().get(3).getMetadata().size());
    }

    @Test
    void getGraph() {
        var graph = singleCallCG.getGraph();

        assertNotNull(graph);
        Assertions.assertEquals(1, graph.getInternalCalls().size());
        Assertions.assertEquals(1, graph.getExternalCalls().size());
        Assertions.assertEquals(0, graph.getResolvedCalls().size());

        // Check internal calls
        var internalCalls = graph.getInternalCalls();

        var call = new ArrayList<Integer>();
        call.add(1);
        call.add(2);

        assertNotNull(internalCalls.get(call).get("0"));
        assertTrue(internalCalls.get(call).get("0") instanceof HashMap);
        assertEquals(6, ((HashMap<String, Object>) internalCalls.get(call).get("0")).get("line"));
        assertEquals("invokestatic", ((HashMap<String, Object>) internalCalls.get(call).get("0")).get("type"));
        assertEquals("/name.space/SingleSourceToTarget",
                ((HashMap<String, Object>) internalCalls.get(call).get("0")).get("receiver"));
    }

    @Test
    void getNodeCount() {
        assertEquals(4, singleCallCG.getNodeCount());
    }

    @Test
    void createExtendedRevisionCallGraph() throws FileNotFoundException {
        var coordinate = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29");
        var cg = PartialCallGraph.createExtendedRevisionCallGraph(coordinate,
                "", "CHA", 1574072773);
        assertNotNull(cg);
        Assertions.assertEquals("mvn", cg.forge);
        Assertions.assertEquals("1.7.29", cg.version);
        Assertions.assertEquals(1574072773, cg.timestamp);
        Assertions.assertEquals(new FastenJavaURI("fasten://mvn!org.slf4j:slf4j-api$1.7.29"), cg.uri);
        Assertions.assertEquals(new FastenJavaURI("fasten://org.slf4j:slf4j-api$1.7.29"), cg.forgelessUri);
        Assertions.assertEquals("org.slf4j:slf4j-api", cg.product);
    }

    @Test
    void internalExternalCHAMultipleDeclarations() {
        var classFile = Mockito.mock(ClassFile.class);
        var type = Mockito.mock(ObjectType.class);

        var method = createMethod(classFile, type);
        var methods = new RefArray<Method>(new Method[]{method});

        var arr = ConstArray._UNSAFE_from(new Method[]{method, method});

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.definedMethods()).thenReturn(arr);
        Mockito.when(declaredMethod.hasMultipleDefinedMethods()).thenReturn(true);

        var classHierarchy = createClassHierarchy(type);

        Mockito.when(classFile.methods()).thenReturn(methods);
        Mockito.when(classFile.thisType()).thenReturn(type);
        Mockito.when(classFile.sourceFile()).thenReturn(Option.apply("filename.java"));

        var classFiles = new HashSet<ClassFile>();
        classFiles.add(classFile);

        var project = Mockito.mock(Project.class);
        Mockito.when(project.classHierarchy()).thenReturn(classHierarchy);
        Mockito.when(project.allClassFiles()).thenReturn(classFiles);

        var callGraph = createCallGraph(declaredMethod);

        var constructor = Mockito.mock(CallGraphConstructor.class);
        Mockito.when(constructor.getProject()).thenReturn(project);
        Mockito.when(constructor.getCallGraph()).thenReturn(callGraph);

        var pcg = new PartialCallGraph(constructor);
        assertNotNull(pcg);

        Mockito.verify(callGraph, Mockito.times(2)).calleesOf(declaredMethod);
    }

    @Test
    void internalExternalCHASingleDeclaration() {
        var classFile = Mockito.mock(ClassFile.class);
        var type = Mockito.mock(ObjectType.class);

        var method = createMethod(classFile, type);
        var methods = new RefArray<Method>(new Method[]{method});

        var arr = ConstArray._UNSAFE_from(new Method[]{method});

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.definedMethods()).thenReturn(arr);
        Mockito.when(declaredMethod.hasSingleDefinedMethod()).thenReturn(true);

        var classHierarchy = createClassHierarchy(type);

        Mockito.when(classFile.methods()).thenReturn(methods);
        Mockito.when(classFile.thisType()).thenReturn(type);
        Mockito.when(classFile.sourceFile()).thenReturn(Option.apply("filename.java"));

        var classFiles = new HashSet<ClassFile>();
        classFiles.add(classFile);

        var project = Mockito.mock(Project.class);
        Mockito.when(project.classHierarchy()).thenReturn(classHierarchy);
        Mockito.when(project.allClassFiles()).thenReturn(classFiles);

        var callGraph = createCallGraph(declaredMethod);

        var constructor = Mockito.mock(CallGraphConstructor.class);
        Mockito.when(constructor.getProject()).thenReturn(project);
        Mockito.when(constructor.getCallGraph()).thenReturn(callGraph);

        var pcg = new PartialCallGraph(constructor);
        assertNotNull(pcg);

        Mockito.verify(declaredMethod, Mockito.times(1)).definedMethod();
        Mockito.verify(callGraph, Mockito.times(1)).calleesOf(declaredMethod);
    }

    @Test
    void internalExternalCHAVirtual() {
        var classFile = Mockito.mock(ClassFile.class);
        var type = Mockito.mock(ObjectType.class);

        var method = createMethod(classFile, type);
        var methods = new RefArray<Method>(new Method[]{method});

        var arr = ConstArray._UNSAFE_from(new Method[]{method, method});

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.definedMethods()).thenReturn(arr);
        Mockito.when(declaredMethod.isVirtualOrHasSingleDefinedMethod()).thenReturn(true);

        var classHierarchy = createClassHierarchy(type);

        Mockito.when(classFile.methods()).thenReturn(methods);
        Mockito.when(classFile.thisType()).thenReturn(type);
        Mockito.when(classFile.sourceFile()).thenReturn(Option.apply("filename.java"));

        var classFiles = new HashSet<ClassFile>();
        classFiles.add(classFile);

        var project = Mockito.mock(Project.class);
        Mockito.when(project.classHierarchy()).thenReturn(classHierarchy);
        Mockito.when(project.allClassFiles()).thenReturn(classFiles);

        var callGraph = createCallGraph(declaredMethod);

        var constructor = Mockito.mock(CallGraphConstructor.class);
        Mockito.when(constructor.getProject()).thenReturn(project);
        Mockito.when(constructor.getCallGraph()).thenReturn(callGraph);

        var pcg = new PartialCallGraph(constructor);
        assertNotNull(pcg);

        Mockito.verify(callGraph, Mockito.times(1)).calleesOf(declaredMethod);
    }

    @Test
    void internalExternalCHANoDefinition() {
        var classFile = Mockito.mock(ClassFile.class);
        var type = Mockito.mock(ObjectType.class);

        var method = createMethod(classFile, type);
        var methods = new RefArray<Method>(new Method[]{method});

        var arr = ConstArray._UNSAFE_from(new Method[]{method, method});

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.definedMethods()).thenReturn(arr);

        var classHierarchy = createClassHierarchy(type);

        Mockito.when(classFile.methods()).thenReturn(methods);
        Mockito.when(classFile.thisType()).thenReturn(type);
        Mockito.when(classFile.sourceFile()).thenReturn(Option.apply("filename.java"));

        var classFiles = new HashSet<ClassFile>();
        classFiles.add(classFile);

        var project = Mockito.mock(Project.class);
        Mockito.when(project.classHierarchy()).thenReturn(classHierarchy);
        Mockito.when(project.allClassFiles()).thenReturn(classFiles);

        var callGraph = createCallGraph(declaredMethod);

        var constructor = Mockito.mock(CallGraphConstructor.class);
        Mockito.when(constructor.getProject()).thenReturn(project);
        Mockito.when(constructor.getCallGraph()).thenReturn(callGraph);

        var pcg = new PartialCallGraph(constructor);
        assertNotNull(pcg);

        Mockito.verify(callGraph, Mockito.never()).calleesOf(Mockito.any());
    }

    private Method createMethod(ClassFile classFile, ObjectType type) {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        var arrayOfParameters = new RefArray<FieldType>(new FieldType[]{type});

        var descriptor = Mockito.mock(MethodDescriptor.class);
        Mockito.when(descriptor.parameterTypes()).thenReturn(arrayOfParameters);
        Mockito.when(descriptor.returnType()).thenReturn(type);

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

    private ClassHierarchy createClassHierarchy(ObjectType type) {
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

        return classHierarchy;
    }

    private CallGraph createCallGraph(DeclaredMethod declaredMethod) {
        var sourceDeclarations = new HashSet<DeclaredMethod>();
        sourceDeclarations.add(declaredMethod);

        var sourceDeclarationIterator = Mockito.mock(Iterator.class);
        Mockito.when(sourceDeclarationIterator.toIterable()).thenReturn(sourceDeclarations);

        var callGraph = Mockito.mock(CallGraph.class);
        Mockito.when(callGraph.reachableMethods()).thenReturn(sourceDeclarationIterator);

        return callGraph;
    }
}