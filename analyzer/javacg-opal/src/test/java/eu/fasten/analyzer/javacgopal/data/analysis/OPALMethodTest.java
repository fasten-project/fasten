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

import eu.fasten.analyzer.javacgopal.data.analysis.OPALMethod;
import eu.fasten.core.data.FastenJavaURI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opalj.br.ArrayType;
import org.opalj.br.BaseType;
import org.opalj.br.FieldType;
import org.opalj.br.MethodDescriptor;
import org.opalj.br.ObjectType;
import org.opalj.br.ReferenceType;
import org.opalj.br.Type;
import org.opalj.collection.immutable.RefArray;

class OPALMethodTest {

    @Test
    void toCanonicalSchemelessURI() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(ReferenceType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        var wrapperParameterType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperParameterType.packageName()).thenReturn("parameter/package");

        var baseParameterType = Mockito.mock(BaseType.class);
        Mockito.when(baseParameterType.WrapperType()).thenReturn(wrapperParameterType);
        Mockito.when(baseParameterType.toString()).thenReturn("parameterName");

        var parameterType = Mockito.mock(ReferenceType.class);
        Mockito.when(parameterType.asBaseType()).thenReturn(baseParameterType);
        Mockito.when(parameterType.isBaseType()).thenReturn(true);

        var wrapperReturnType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperReturnType.packageName()).thenReturn("return/package");

        var baseReturnType = Mockito.mock(BaseType.class);
        Mockito.when(baseReturnType.WrapperType()).thenReturn(wrapperReturnType);
        Mockito.when(baseReturnType.toString()).thenReturn("typeReturnName");

        var returnType = Mockito.mock(ReferenceType.class);
        Mockito.when(returnType.asBaseType()).thenReturn(baseReturnType);
        Mockito.when(returnType.isBaseType()).thenReturn(true);

        var arrayOfParameters = new RefArray<FieldType>(new FieldType[]{parameterType});

        var descriptor = Mockito.mock(MethodDescriptor.class);
        Mockito.when(descriptor.parameterTypes()).thenReturn(arrayOfParameters);
        Mockito.when(descriptor.returnType()).thenReturn(returnType);

        assertEquals(FastenJavaURI.create("//productName/some.package/typeName.methodName(%2Fparameter.package%2FparameterName)%2Freturn.package%2FtypeReturnName"),
                OPALMethod.toCanonicalSchemelessURI("productName", type, "methodName", descriptor));
    }

    @Test
    void defaultConstructor() {
        var method = new OPALMethod();
        assertNotNull(method);
    }

    @Test
    void getTypeURIWithoutLambda() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(Type.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        assertEquals(FastenJavaURI.create("/some.package/typeName"), OPALMethod.getTypeURI(type));
    }

    @Test
    void getTypeURILambda() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("%28%5DLjava$lang$String%3A%29V%3A14$Lambda");

        var type = Mockito.mock(Type.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        assertEquals(FastenJavaURI.create("/some.package/%28%5DLjava$lang$String%3A%29V%3A14$Lambda"),
                OPALMethod.getTypeURI(type));
    }

    @Test
    void getParametersURI() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(FieldType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        List<FieldType> parameters = new ArrayList<>();
        parameters.add(type);

        var parametersURIs = OPALMethod.getParametersURI(parameters);

        assertEquals(1, parametersURIs.length);
        assertEquals(FastenJavaURI.create("/some.package/typeName"), parametersURIs[0]);
    }

    @Test
    void getPackageNameBaseType() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("base/type/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);

        var type = Mockito.mock(Type.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        assertEquals("base.type.package", OPALMethod.getPackageName(type));
    }

    @Test
    void getPackageNameVoidType() {
        var type = Mockito.mock(Type.class);
        Mockito.when(type.isVoidType()).thenReturn(true);

        assertEquals("java.lang", OPALMethod.getPackageName(type));
    }

    @Test
    void getPackageNameNoType() {
        var type = Mockito.mock(Type.class);
        Mockito.when(type.isBaseType()).thenReturn(false);
        Mockito.when(type.isReferenceType()).thenReturn(false);
        Mockito.when(type.isVoidType()).thenReturn(false);

        assertEquals("", OPALMethod.getPackageName(type));
    }

    @Test
    void getPackageNameArrayType() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("array/type/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);

        var baseTypeWrapper = Mockito.mock(FieldType.class);
        Mockito.when(baseTypeWrapper.isBaseType()).thenReturn(true);
        Mockito.when(baseTypeWrapper.asBaseType()).thenReturn(baseType);

        var arrayType = Mockito.mock(ArrayType.class);
        Mockito.when(arrayType.componentType()).thenReturn(baseTypeWrapper);

        var type = Mockito.mock(Type.class);
        Mockito.when(type.isReferenceType()).thenReturn(true);
        Mockito.when(type.isArrayType()).thenReturn(true);
        Mockito.when(type.asArrayType()).thenReturn(arrayType);

        assertEquals("array.type.package", OPALMethod.getPackageName(type));
    }

    @Test
    void getPackageNameReferenceTypeEmpty() {
        var objectType = Mockito.mock(ObjectType.class);
        Mockito.when(objectType.packageName()).thenReturn("");

        var type = Mockito.mock(Type.class);
        Mockito.when(type.isReferenceType()).thenReturn(true);
        Mockito.when(type.isArrayType()).thenReturn(false);
        Mockito.when(type.asObjectType()).thenReturn(objectType);

        assertNull(OPALMethod.getPackageName(type));
    }

    @Test
    void getPackageNameReferenceType() {
        var objectType = Mockito.mock(ObjectType.class);
        Mockito.when(objectType.packageName()).thenReturn("some/package");

        var type = Mockito.mock(Type.class);
        Mockito.when(type.isReferenceType()).thenReturn(true);
        Mockito.when(type.isArrayType()).thenReturn(false);
        Mockito.when(type.asObjectType()).thenReturn(objectType);

        assertEquals("some.package", OPALMethod.getPackageName(type));
    }

    @Test
    void getClassNameBaseType() {
        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.toString()).thenReturn("baseType");

        var type = Mockito.mock(Type.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        assertEquals("baseType", OPALMethod.getClassName(type));
    }

    @Test
    void getClassNameVoidType() {
        var type = Mockito.mock(Type.class);
        Mockito.when(type.isVoidType()).thenReturn(true);

        assertEquals("VoidType", OPALMethod.getClassName(type));
    }

    @Test
    void getClassNameNoType() {
        var type = Mockito.mock(Type.class);
        Mockito.when(type.isBaseType()).thenReturn(false);
        Mockito.when(type.isReferenceType()).thenReturn(false);
        Mockito.when(type.isVoidType()).thenReturn(false);

        assertEquals("", OPALMethod.getClassName(type));
    }

    @Test
    void getClassNameArrayType() {
        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.toString()).thenReturn("Integer");

        var baseTypeWrapper = Mockito.mock(FieldType.class);
        Mockito.when(baseTypeWrapper.isBaseType()).thenReturn(true);
        Mockito.when(baseTypeWrapper.asBaseType()).thenReturn(baseType);

        var arrayType = Mockito.mock(ArrayType.class);
        Mockito.when(arrayType.componentType()).thenReturn(baseTypeWrapper);

        var type = Mockito.mock(Type.class);
        Mockito.when(type.isReferenceType()).thenReturn(true);
        Mockito.when(type.isArrayType()).thenReturn(true);
        Mockito.when(type.asArrayType()).thenReturn(arrayType);

        assertEquals("Integer[]", OPALMethod.getClassName(type));
    }

    @Test
    void getClassNameReferenceTypeSimpleObject() {
        var objectType = Mockito.mock(ObjectType.class);
        Mockito.when(objectType.simpleName()).thenReturn("TestName");

        var type = Mockito.mock(Type.class);
        Mockito.when(type.isReferenceType()).thenReturn(true);
        Mockito.when(type.isArrayType()).thenReturn(false);
        Mockito.when(type.asObjectType()).thenReturn(objectType);

        assertEquals("TestName", OPALMethod.getClassName(type));
    }

    @Test
    void getClassNameReferenceTypeLambda() {
        var objectType = Mockito.mock(ObjectType.class);
        Mockito.when(objectType.simpleName()).thenReturn("LambdaTestName%");

        var type = Mockito.mock(Type.class);
        Mockito.when(type.isReferenceType()).thenReturn(true);
        Mockito.when(type.isArrayType()).thenReturn(false);
        Mockito.when(type.asObjectType()).thenReturn(objectType);

        assertEquals("LambdaTestName%", OPALMethod.getClassName(type));
    }
}