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

package eu.fasten.analyzer.javacgwala.data.core;

import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.util.Objects;
import java.util.stream.IntStream;

public abstract class Method {

    private final String namespace;

    private final Selector symbol;

    private final MethodReference reference;

    /**
     * Construct Method from {@link MethodReference}.
     *
     * @param reference Method Reference
     */
    public Method(final MethodReference reference) {
        this.reference = reference;
        this.namespace = getPackageName(reference.getDeclaringClass()) + "."
                + getClassName(reference.getDeclaringClass());
        this.symbol = reference.getSelector();
    }

    public MethodReference getReference() {
        return reference;
    }

    public String getNamespace() {
        return namespace;
    }

    public Selector getSymbol() {
        return symbol;
    }

    /**
     * Convert {@link Method} to ID representation.
     *
     * @return Method ID
     */
    public abstract String toID();

    /**
     * Convert {@link FastenJavaURI} to {@link FastenURI}.
     *
     * @return {@link FastenURI}
     */
    public FastenURI toCanonicalSchemalessURI() {

        final var javaURI = FastenJavaURI.create(null, null, null,
                getPackageName(reference.getDeclaringClass()),
                getClassName(reference.getDeclaringClass()),
                getMethodName(reference),
                getParameters(reference),
                getReturnType(reference)
        ).canonicalize();

        return FastenURI.createSchemeless(javaURI.getRawForge(), javaURI.getRawProduct(),
                javaURI.getRawVersion(),
                javaURI.getRawNamespace(), javaURI.getRawEntity());
    }

    /**
     * Get package name in which class is located.
     *
     * @param reference Type Reference
     * @return Package name
     */
    public static String getPackageName(final TypeReference reference) {
        if (reference.isPrimitiveType()) {
            return "java.lang";

        } else {
            if (reference.isArrayType()) {
                return Objects.requireNonNull(getPackageName(reference.getArrayElementType()))
                        .replace("/", ".");

            } else if (reference.getName().getPackage() == null) {
                return null;

            } else {
                return reference.getName().getPackage().toString().replace("/", ".");
            }

        }
    }

    /**
     * Get package name in which this class is located.
     *
     * @return Package name
     */
    public String getPackageName() {
        return getPackageName(reference.getDeclaringClass());
    }

    /**
     * Get class name in which method is declared.
     *
     * @param reference Type Reference
     * @return Class name
     */
    public static String getClassName(final TypeReference reference) {
        if (reference.isPrimitiveType()) {
            return resolvePrimitiveTypeEncoding(reference.getName().toString());

        } else {
            if (reference.isArrayType()) {
                return Objects.requireNonNull(getClassName(reference.getArrayElementType()))
                        .concat(threeTimesPct("[]"));
            } else {
                return reference.getName().getClassName().toString();
            }

        }
    }

    /**
     * Get class name in which this method is declared.
     *
     * @return Class name
     */
    public String getClassName() {
        return getClassName(reference.getDeclaringClass());
    }

    /**
     * Get method name.
     *
     * @param reference Method reference
     * @return Method name
     */
    public static String getMethodName(final MethodReference reference) {
        if (reference.getSelector().getName().toString().equals("<init>")) {
            return getClassName(reference.getDeclaringClass());

        } else if (reference.getSelector().getName().toString().equals("<clinit>")) {
            return threeTimesPct("<init>");

        } else {
            return reference.getSelector().getName().toString();
        }
    }

    /**
     * Get list of parameters of a method in the form of FastenJavaURI.
     *
     * @param reference Method reference
     * @return List of parameters
     */
    public static FastenJavaURI[] getParameters(final MethodReference reference) {
        final FastenJavaURI[] parameters = new FastenJavaURI[reference.getNumberOfParameters()];

        IntStream.range(0, reference.getNumberOfParameters())
                .forEach(i -> parameters[i] = getType(reference.getParameterType(i)));

        return parameters;
    }

    /**
     * Return Type in the form /namespace/class.
     *
     * @param reference Type Reference
     * @return Type
     */
    public static FastenJavaURI getType(final TypeReference reference) {
        return new FastenJavaURI("/" + getPackageName(reference) + "/" + getClassName(reference));
    }

    /**
     * Get return type of a method.
     *
     * @param reference Method Reference
     * @return Return type
     */
    public static FastenJavaURI getReturnType(final MethodReference reference) {
        return getType(reference.getReturnType());
    }

    /**
     * Perform encoding 2 times.
     *
     * @param nonEncoded String to encode
     * @return Encoded string
     */
    private static String threeTimesPct(final String nonEncoded) {
        return FastenJavaURI.pctEncodeArg(FastenJavaURI
                .pctEncodeArg(FastenJavaURI.pctEncodeArg(nonEncoded)));
    }

    /**
     * Returns wrapper object name of primitive types.
     *
     * @param encoded Encoded primitive type
     * @return Wrapper object name
     */
    private static String resolvePrimitiveTypeEncoding(final String encoded) {
        switch (encoded) {
            case "Z":
                return "BooleanType";
            case "B":
                return "ByteType";
            case "C":
                return "CharacterType";
            case "D":
                return "DoubleType";
            case "F":
                return "FloatType";
            case "I":
                return "IntegerType";
            case "J":
                return "LongType";
            case "S":
                return "ShortType";
            default:
                return "VoidType";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Method method = (Method) o;
        return Objects.equals(namespace, method.namespace)
                && Objects.equals(symbol, method.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, symbol);
    }
}
