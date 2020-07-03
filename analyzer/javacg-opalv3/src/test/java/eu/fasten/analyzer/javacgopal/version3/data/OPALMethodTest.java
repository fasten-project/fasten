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

import eu.fasten.core.data.FastenJavaURI;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opalj.br.ArrayType;
import org.opalj.br.BaseType;
import org.opalj.br.FieldType;
import org.opalj.br.ObjectType;
import org.opalj.br.Type;

class OPALMethodTest {

    @Test
    void toCanonicalSchemelessURI() {
    }

    @Test
    void getMethodNameInit() {
        var methodUri = OPALMethod.getMethodName("TestClass", "<init>");
        assertEquals("TestClass", methodUri);
    }

    @Test
    void getMethodNameClinit() {
        var methodUri = OPALMethod.getMethodName("TestClass", "<clinit>");
        var encoded = threeTimesPct("<init>");
        assertEquals(encoded, methodUri);
    }

    @Test
    void getMethodNameWithMethodName() {
        var methodUri = OPALMethod.getMethodName("TestClass", "TestMethod");
        assertEquals("TestMethod", methodUri);
    }

    @Test
    void getMethodNameLambda() {
        var methodUri = OPALMethod.getMethodName("LambdaTestClass%", "<init>");
        var encoded = FastenJavaURI.pctEncodeArg("LambdaTestClass%");
        assertEquals(encoded, methodUri);
    }

    @Test
    void getTypeURI() {
    }

    @Test
    void getParametersURI() {
    }

    @Test
    void getPackageName() {
    }

    @Test
    void getClassNameBaseType() {
        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.toString()).thenReturn("baseTypeReturn");

        var type = Mockito.mock(Type.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        assertEquals("baseTypeReturn", OPALMethod.getClassName(type));
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

        assertEquals("Integer%25255B%25255D", OPALMethod.getClassName(type));
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

        var encoded = FastenJavaURI.pctEncodeArg(
                threeTimesPct("LambdaTestName%"));

        assertEquals(encoded, OPALMethod.getClassName(type));
    }

    /**
     * Pct encode given String three times.
     *
     * @param nonEncoded non encoded String
     * @return encoded String
     */
    private static String threeTimesPct(final String nonEncoded) {
        return FastenJavaURI
                .pctEncodeArg(FastenJavaURI.pctEncodeArg(FastenJavaURI.pctEncodeArg(nonEncoded)));
    }
}