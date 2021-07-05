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

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaScope;
import java.io.File;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;
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
import org.opalj.br.LineNumber;
import org.opalj.br.LineNumberTable;
import org.opalj.br.Method;
import org.opalj.br.MethodDescriptor;
import org.opalj.br.ObjectType;
import org.opalj.br.instructions.Instruction;
import org.opalj.collection.QualifiedCollection;
import org.opalj.collection.immutable.$colon$amp$colon;
import org.opalj.collection.immutable.Chain;
import org.opalj.collection.immutable.RefArray;
import org.opalj.collection.immutable.UIDSet;
import org.opalj.collection.immutable.UIDSet1;
import scala.Option;

class OPALTypeTest {

    private static ObjectType type;

    @BeforeAll
    static void setUp() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        type = Mockito.mock(ObjectType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);
    }

    @Test
    void constructorTest() {
        var method = Mockito.mock(Method.class);
        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        var superClass = Mockito.mock(ObjectType.class);
        var superClasses = new LinkedList<ObjectType>();
        superClasses.add(superClass);

        var superInterface = Mockito.mock(ObjectType.class);
        var superInterfaces = new ArrayList<ObjectType>();
        superInterfaces.add(superInterface);

        var annotations = new HashMap<String, List<Pair<String, String>>>();
        annotations.put("org/springframework/boot/RestController", new ArrayList<>());

        var type = new OPALType(methods, superClasses, superInterfaces, "source.java", "", false, annotations);

        assertEquals("source.java", type.getSourceFileName());
        assertEquals(123, type.getMethods().get(method));
        assertEquals(superClass, type.getSuperClasses().peek());
        assertEquals(superInterface, type.getSuperInterfaces().get(0));
        assertEquals(annotations, type.getAnnotations());
    }

    @Test
    void getTypeWithSuperClassesWithSuperInterface() {
        var chain = new $colon$amp$colon<>(type, Chain.empty());
        var qualifiedCollection = Mockito.mock(QualifiedCollection.class);
        Mockito.when(qualifiedCollection.s()).thenReturn(chain);

        var uidSet = Mockito.mock(UIDSet.class);
        Mockito.when(uidSet.nonEmpty()).thenReturn(true);

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

        var methods = new HashMap<DeclaredMethod, Integer>();
        methods.put(declaredMethod, 123);

        var resultType = OPALType.getType(classHierarchy, methods, type);

        assertNotNull(resultType);
        assertNotNull(resultType.get("/some.package/typeName"));

        var ERCGType = resultType.get("/some.package/typeName");
        assertEquals(1, resultType.size());
        assertEquals(1, ERCGType.getMethods().size());
        assertEquals("", ERCGType.getSourceFileName());
        assertEquals(1, ERCGType.getSuperClasses().size());
        assertEquals(1, ERCGType.getSuperInterfaces().size());
        assertNotNull(ERCGType.getMethods().get(123));

        var node = ERCGType.getMethods().get(123);
        assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
                node.getUri());
        assertEquals(0, node.getMetadata().size());

        assertEquals(FastenURI.create("/some.package/typeName"),
                ERCGType.getSuperInterfaces().get(0));

        assertEquals(FastenURI.create("/some.package/typeName"),
                ERCGType.getSuperClasses().get(0));
    }


    @Test
    void getTypeNoSuperClassesWithSuperInterface() {
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

        var methods = new HashMap<DeclaredMethod, Integer>();
        methods.put(declaredMethod, 123);

        var resultType = OPALType.getType(classHierarchy, methods, type);

        assertNotNull(resultType);
        assertNotNull(resultType.get("/some.package/typeName"));

        var ERCGType = resultType.get("/some.package/typeName");
        assertEquals(1, resultType.size());
        assertEquals(1, ERCGType.getMethods().size());
        assertEquals("", ERCGType.getSourceFileName());
        assertEquals(0, ERCGType.getSuperClasses().size());
        assertEquals(1, ERCGType.getSuperInterfaces().size());
        assertNotNull(ERCGType.getMethods().get(123));

        var node = ERCGType.getMethods().get(123);
        assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
                node.getUri());
        assertEquals(0, node.getMetadata().size());

        assertEquals(FastenURI.create("/some.package/typeName"),
                ERCGType.getSuperInterfaces().get(0));
    }

    @Test
    void getTypeNoSuperClassesNoSuperInterfacesAlternative() {
        var method = createMethod();
        Mockito.when(method.isPrivate()).thenReturn(true);

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        var opalType = new OPALType(methods, null, new ArrayList<>(), "source.java", "", false, new HashMap<>());

        var resultType = OPALType.getType(opalType, type);

        assertNotNull(resultType);
//        assertNotNull(resultType.get(FastenURI.create("/some.package/typeName")));
//
//        var ERCGType = resultType.get(FastenURI.create("/some.package/typeName"));
//        assertEquals(1, resultType.size());
//        Assertions.assertEquals(1, ERCGType.getMethods().size());
//        Assertions.assertEquals("source.java", ERCGType.getSourceFileName());
//        Assertions.assertEquals(0, ERCGType.getSuperClasses().size());
//        Assertions.assertEquals(0, ERCGType.getSuperInterfaces().size());
//        assertNotNull(ERCGType.getMethods().get(123));
//
//        var node = ERCGType.getMethods().get(123);
//        Assertions.assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
//                node.getUri());
//        Assertions.assertEquals(4, node.getMetadata().size());
//        Assertions.assertEquals(10, node.getMetadata().get("first"));
//        Assertions.assertEquals(30, node.getMetadata().get("last"));
//        Assertions.assertEquals(true, node.getMetadata().get("defined"));
//        Assertions.assertEquals("private", node.getMetadata().get("access"));
    }

    @Test
    void getTypeWithSuperClassesWithSuperInterfacesAlternative() {
        var method = createMethod();
        Mockito.when(method.isPrivate()).thenReturn(true);

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        var chain = new LinkedList<ObjectType>();
        chain.add(type);
        var interfaces = new ArrayList<ObjectType>();
        interfaces.add(type);

        var opalType = new OPALType(methods, chain, interfaces, "source.java", "", false, new HashMap<>());

        var resultType = OPALType.getType(opalType, type);

        assertNotNull(resultType);
//        assertNotNull(resultType.get(FastenURI.create("/some.package/typeName")));
//
//        var ERCGType = resultType.get(FastenURI.create("/some.package/typeName"));
//        assertEquals(1, resultType.size());
//        Assertions.assertEquals(1, ERCGType.getMethods().size());
//        Assertions.assertEquals("source.java", ERCGType.getSourceFileName());
//        Assertions.assertEquals(1, ERCGType.getSuperClasses().size());
//        Assertions.assertEquals(1, ERCGType.getSuperInterfaces().size());
//        assertNotNull(ERCGType.getMethods().get(123));
//
//        var node = ERCGType.getMethods().get(123);
//        Assertions.assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
//                node.getUri());
//        Assertions.assertEquals(4, node.getMetadata().size());
//        Assertions.assertEquals(10, node.getMetadata().get("first"));
//        Assertions.assertEquals(30, node.getMetadata().get("last"));
//        Assertions.assertEquals(true, node.getMetadata().get("defined"));
//        Assertions.assertEquals("private", node.getMetadata().get("access"));
//
//        Assertions.assertEquals(FastenURI.create("/some.package/typeName"),
//                ERCGType.getSuperClasses().getFirst());
//        Assertions.assertEquals(FastenURI.create("/some.package/typeName"),
//                ERCGType.getSuperInterfaces().get(0));
    }

    @Test
    void toURIDeclaredMethods() {
        var arrayOfParameters = new RefArray<FieldType>(new FieldType[]{type});

        var descriptor = Mockito.mock(MethodDescriptor.class);
        Mockito.when(descriptor.parameterTypes()).thenReturn(arrayOfParameters);
        Mockito.when(descriptor.returnType()).thenReturn(type);

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.descriptor()).thenReturn(descriptor);
        Mockito.when(declaredMethod.name()).thenReturn("methodName");
        Mockito.when(declaredMethod.declaringClassType()).thenReturn(type);

        var methods = new HashMap<DeclaredMethod, Integer>();
        methods.put(declaredMethod, 123);

        assertNotNull(OPALType.toURIDeclaredMethods(methods).get(123));
        assertEquals(0,
                OPALType.toURIDeclaredMethods(methods).get(123).getMetadata().size());
        assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
                OPALType.toURIDeclaredMethods(methods).get(123).getUri());
    }

    @Test
    void toURIInterfaces() {
        var list = new ArrayList<ObjectType>();
        list.add(type);

        assertEquals(1, OPALType.toURIInterfaces(list).size());
        assertEquals(FastenURI.create("/some.package/typeName"),
                OPALType.toURIInterfaces(list).get(0));
    }

    @Test
    void toURIClasses() {
        var chain = new LinkedList<ObjectType>();
        chain.add(type);

        assertEquals(1, OPALType.toURIClasses(chain).size());
        assertEquals(FastenURI.create("/some.package/typeName"),
                OPALType.toURIClasses(chain).get(0));
    }

    @Test
    void toURIMethodsPrivateWithDefinedLines() {
        var method = createMethod();

        Mockito.when(method.isPrivate()).thenReturn(true);

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);
        assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
                node.getUri());
        assertEquals(4, node.getMetadata().size());
        assertEquals(10, node.getMetadata().get("first"));
        assertEquals(30, node.getMetadata().get("last"));
        assertEquals(true, node.getMetadata().get("defined"));
        assertEquals("private", node.getMetadata().get("access"));
    }

    @Test
    void toURIMethodsPublicWithEmptyLines() {
        var method = createMethod();

        Mockito.when(method.isPublic()).thenReturn(true);
        Mockito.when(method.body().get().firstLineNumber()).thenReturn(Option.empty());
        Mockito.when(method.body().get().lineNumber(20)).thenReturn(Option.empty());

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);

        Mockito.when(method.instructionsOption()).thenReturn(Option.empty());

        node = OPALType.toURIMethods(methods).get(123);
        assertEquals(10, node.getMetadata().get("first"));
        assertEquals(30, node.getMetadata().get("last"));
        assertEquals(false, node.getMetadata().get("defined"));
        assertEquals("public", node.getMetadata().get("access"));
    }

    @Test
    void toURIMethodsPackagePrivateWithNotFoundLines() {
        var method = createMethod();

        Mockito.when(method.isPackagePrivate()).thenReturn(true);
        Mockito.when(method.body()).thenReturn(Option.empty());

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);

        assertEquals("notFound", node.getMetadata().get("first"));
        assertEquals("notFound", node.getMetadata().get("last"));
        assertEquals("packagePrivate", node.getMetadata().get("access"));
    }

    @Test
    void toURIMethodsProtectedWithNotFoundLines() {
        var method = createMethod();

        Mockito.when(method.isProtected()).thenReturn(true);

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);

        assertEquals("protected", node.getMetadata().get("access"));
    }

    @Test
    void toURIMethodsNotFoundAccessWithNotFoundLines() {
        var method = createMethod();

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);

        assertEquals("notFound", node.getMetadata().get("access"));
    }

    private Method createMethod() {
        var arrayOfParameters = new RefArray<FieldType>(new FieldType[]{type});

        var descriptor = Mockito.mock(MethodDescriptor.class);
        Mockito.when(descriptor.parameterTypes()).thenReturn(arrayOfParameters);
        Mockito.when(descriptor.returnType()).thenReturn(type);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(type);

        var code = Mockito.mock(Code.class);
        var lineNumberTable = Mockito.mock(LineNumberTable.class);
        LineNumber[] lineNumber = new LineNumber[2];
        lineNumber[0] = new LineNumber(0,10);
        lineNumber[1] = new LineNumber(1,30);
        var lineNumbers = new RefArray<LineNumber>(lineNumber);
        Mockito.when(code.lineNumberTable()).thenReturn(Option.apply(lineNumberTable));
        Mockito.when(code.lineNumberTable().get().lineNumbers()).thenReturn(lineNumbers);

        var method = Mockito.mock(Method.class);
        Mockito.when(method.descriptor()).thenReturn(descriptor);
        Mockito.when(method.name()).thenReturn("methodName");
        Mockito.when(method.declaringClassFile()).thenReturn(classFile);
        Mockito.when(method.body()).thenReturn(Option.apply(code));
        Mockito.when(method.instructionsOption()).thenReturn(Option.apply(new Instruction[]{}));

        return method;
    }

    @Test
    void extractSuperClassesCorrect() {
        var superClass = Mockito.mock(ObjectType.class);
        var currentType = Mockito.mock(ObjectType.class);
        var chain = new $colon$amp$colon<>(superClass, Chain.empty());

        var qualifiedCollection = Mockito.mock(QualifiedCollection.class);
        Mockito.when(qualifiedCollection.s()).thenReturn(chain);

        var uidSet = Mockito.mock(UIDSet.class);
        Mockito.when(uidSet.nonEmpty()).thenReturn(true);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.supertypes(currentType)).thenReturn(uidSet);
        Mockito.when(classHierarchy.allSuperclassTypesInInitializationOrder(currentType))
                .thenReturn(qualifiedCollection);

        assertEquals(superClass, OPALType.extractSuperClasses(classHierarchy, currentType).peek());
    }

    @Test
    void extractSuperClassesEmptySuperClasses() {
        var currentType = Mockito.mock(ObjectType.class);

        var uidSet = Mockito.mock(UIDSet.class);
        Mockito.when(uidSet.nonEmpty()).thenReturn(false);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.supertypes(currentType)).thenReturn(uidSet);

        assertNull(OPALType.extractSuperClasses(classHierarchy, currentType));
    }

    @Test
    void extractSuperClassesNullSuperClasses() {
        var currentType = Mockito.mock(ObjectType.class);

        var qualifiedCollection = Mockito.mock(QualifiedCollection.class);
        Mockito.when(qualifiedCollection.s()).thenReturn(null);

        var uidSet = Mockito.mock(UIDSet.class);
        Mockito.when(uidSet.nonEmpty()).thenReturn(true);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.supertypes(currentType)).thenReturn(uidSet);
        Mockito.when(classHierarchy.allSuperclassTypesInInitializationOrder(currentType))
                .thenReturn(qualifiedCollection);

        assertNull(OPALType.extractSuperClasses(classHierarchy, currentType));
    }

    @Test
    void extractSuperInterfaces() {
        var superInterface = Mockito.mock(ObjectType.class);
        var currentType = Mockito.mock(ObjectType.class);

        var uidSet = new UIDSet1<>(superInterface);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.allSuperinterfacetypes(currentType, false))
                .thenReturn(uidSet);

        assertEquals(1,
                OPALType.extractSuperInterfaces(classHierarchy, currentType).size());
        assertEquals(superInterface,
                OPALType.extractSuperInterfaces(classHierarchy, currentType).get(0));
    }

    @Test
    void lineNumbersSouldBeAccurate() throws OPALException {

        var cg = getRCG("linenumbertests/APIConsumerImpl.class");

        assertLineNumber(cg,"/org.wso2.carbon.apimgt.impl/APIConsumerImpl.%3Cinit%3E()%2Fjava" +
            ".lang%2FVoidType", 193, 195);
        assertLineNumber(cg, "/org.wso2.carbon.apimgt.impl/APIConsumerImpl.%3Cinit%3E" +
            "(%2Fjava.lang%2FString,APIMRegistryService)%2Fjava.lang%2FVoidType", 198, 202);

        cg = getRCG("linenumbertests/ProcessIdUtil.class");

        assertLineNumber(cg, "/org.apache.logging.log4j.util/ProcessIdUtil.getProcessId()%2Fjava" +
            ".lang%2FString", 33, 49);

    }

    private void assertLineNumber(PartialCallGraph cg, final String uri, final int first,
                                  final int last) {
        for (final var type : cg.getClassHierarchy().get(JavaScope.internalTypes).values()) {
            for (final var node : type.getMethods().values()) {
                if (node.getUri().toString().equals(uri)) {
                    assertEquals(first, node.getMetadata().get("first"));
                    assertEquals(last, node.getMetadata().get("last"));
                }
            }
        }
    }

    private PartialCallGraph getRCG(String s) throws OPALException {
        return new PartialCallGraph(new CallGraphConstructor(
            new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource(s)).getFile()), "",
            "CHA"), false);
    }
}

