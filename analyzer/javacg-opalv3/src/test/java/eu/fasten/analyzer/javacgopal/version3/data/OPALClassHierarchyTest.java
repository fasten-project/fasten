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

package eu.fasten.analyzer.javacgopal.version3.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opalj.br.BaseType;
import org.opalj.br.ClassFile;
import org.opalj.br.Code;
import org.opalj.br.DeclaredMethod;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.br.ReferenceType;
import org.opalj.br.instructions.Instruction;
import org.opalj.br.instructions.MethodInvocationInstruction;
import org.opalj.collection.immutable.Chain;
import scala.Option;

class OPALClassHierarchyTest {

    @Test
    void getInternalCHA() {
    }

    @Test
    void getExternalCHA() {
    }

    @Test
    void getNodeCount() {
    }

    @Test
    void asURIHierarchy() {
    }

    @Test
    void addMethodToExternals() {
    }

    @Test
    void getInternalCallKeys() {
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

        var type = new OPALType(methods, Chain.empty(), new ArrayList<>(), "source.java");

        var internal = new HashMap<ObjectType, OPALType>();
        internal.put(objectType, type);

        var classHierarchy = new OPALClassHierarchy(internal, new HashMap<>(), 5);

        var externalKeys = classHierarchy.getExternalCallKeys(source, target);

        assertEquals(2, externalKeys.size());
        assertEquals(123, externalKeys.get(0));
        assertEquals(5, externalKeys.get(1));
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

        var type = new OPALType(methods, Chain.empty(), new ArrayList<>(), "source.java");

        var internal = new HashMap<ObjectType, OPALType>();
        internal.put(objectType, type);

        var classHierarchy = new OPALClassHierarchy(internal, new HashMap<>(), 5);

        var externalKeys = classHierarchy.getExternalCallKeys(source, target);

        assertEquals(2, externalKeys.size());
        assertEquals(5, externalKeys.get(0));
        assertEquals(123, externalKeys.get(1));
    }

    @Test
    void getExternalCallKeysSourceDeclaredMethodTargetDeclaredMethod() {
        var source = Mockito.mock(DeclaredMethod.class);
        var target = Mockito.mock(DeclaredMethod.class);

        var classHierarchy = new OPALClassHierarchy(new HashMap<>(), new HashMap<>(), 5);

        var externalKeys = classHierarchy.getExternalCallKeys(source, target);

        assertEquals(2, externalKeys.size());
        assertEquals(5, externalKeys.get(0));
        assertEquals(6, externalKeys.get(1));
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
    void putCalls() {
    }

    @Test
    void putExternalCall() {
    }

    @Test
    void getInternalMetadata() {
    }

    @Test
    void appendGraph() {
    }

    @Test
    void getSubGraph() {
    }

    @Test
    void getCallSite() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(ReferenceType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

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
        var callSite = classHierarchy.getCallSite(source, 0);

        assertNotNull(callSite);

        assertEquals(30, ((OPALCallSite) callSite.get("0")).getLine());
        assertEquals("/some.package/typeName", ((OPALCallSite) callSite.get("0")).getReceiver());
        assertEquals("testType", ((OPALCallSite) callSite.get("0")).getType());
    }
}