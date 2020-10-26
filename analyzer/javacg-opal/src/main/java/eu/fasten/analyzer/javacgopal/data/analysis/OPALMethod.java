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

import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.opalj.br.FieldType;
import org.opalj.br.MethodDescriptor;
import org.opalj.br.ReferenceType;
import org.opalj.br.Type;
import scala.collection.JavaConverters;

/**
 * Analyze OPAL methods.
 */
public class OPALMethod {

    /**
     * Converts a method to a Canonicalized Schemeless {@link FastenURI}.
     *
     * @param product    The product which entity belongs to
     * @param klass      The class of the method in {@link ReferenceType} format
     * @param method     Name of the method in String
     * @param descriptor Descriptor of the method in {@link MethodDescriptor} format
     * @return canonicalized Schemeless {@link FastenURI} of the given method
     */
    public static FastenURI toCanonicalSchemelessURI(final String product,
                                                     final ReferenceType klass,
                                                     final String method,
                                                     final MethodDescriptor descriptor)
            throws IllegalArgumentException, NullPointerException {
        var packageName = getPackageName(klass);
        var className = getClassName(klass);
        var params = getParametersURI(JavaConverters.seqAsJavaList(descriptor.parameterTypes()));
        var returnType = getTypeURI(descriptor.returnType());

        final var javaURIRaw = FastenJavaURI.create(null, product, null,
                packageName, className, method, params, returnType);
        final var javaURI = javaURIRaw.canonicalize();

        return FastenURI.createSchemeless(javaURI.getRawForge(), javaURI.getRawProduct(),
                javaURI.getRawVersion(),
                javaURI.getRawNamespace(), javaURI.getRawEntity());
    }

    /**
     * Convert OPAL return types to {@link FastenJavaURI}.
     *
     * @param returnType {@link Type}
     * @return type in FastenJavaURI format.
     */
    public static FastenJavaURI getTypeURI(final Type returnType) {
        return FastenJavaURI.createWithoutFunction("/" + getPackageName(returnType)
                + "/" + getClassName(returnType));
    }

    /**
     * Convert OPAL parameters to FastenJavaURI.
     *
     * @param parametersType Java List of parameters of in OPAL types.
     * @return parameters in FastenJavaURI[].
     */
    public static FastenJavaURI[] getParametersURI(final List<FieldType> parametersType) {
        final FastenJavaURI[] parameters = new FastenJavaURI[parametersType.size()];

        IntStream.range(0, parametersType.size())
                .forEach(i -> parameters[i] = getTypeURI(parametersType.get(i)));

        return parameters;
    }

    /**
     * Recursively figures out the OPAL types of parameters and convert them to FastenURI
     * namespaces.
     *
     * @param parameter OPAL parameter.
     * @return String in {@link FastenURI} format, for namespace of the given parameter.
     */
    public static String getPackageName(final Type parameter) {
        if (parameter.isBaseType()) {
            return slashToDot(parameter.asBaseType().WrapperType().packageName());

        } else if (parameter.isReferenceType()) {
            if (parameter.isArrayType()) {
                return slashToDot(Objects
                        .requireNonNull(getPackageName(parameter.asArrayType().componentType())));
            } else if (parameter.asObjectType().packageName().equals("")) {
                return null;
            } else {
                return slashToDot(parameter.asObjectType().packageName());
            }

        } else if (parameter.isVoidType()) {
            return slashToDot("java.lang");
        }
        return "";
    }

    /**
     * Convert every slash in the given String to dots.
     *
     * @param s String containing slashes
     * @return String with slashes converted to dots
     */
    private static String slashToDot(final String s) {
        return s.replace("/", ".");
    }

    /**
     * Recursively figures out the OPAL types of parameters and convert them to FastenURI type.
     *
     * @param parameter OPAL parameter in {@link Type} format.
     * @return String in {@link FastenURI} format, for type of the given parameter.
     */
    public static String getClassName(final Type parameter) {
        if (parameter.isBaseType()) {
            return parameter.asBaseType().toString();

        } else if (parameter.isReferenceType()) {
            if (parameter.isArrayType()) {
                return getClassName(parameter.asArrayType().componentType()).concat("[]");
            } else {
                return parameter.asObjectType().simpleName();
            }
        } else if (parameter.isVoidType()) {
            return "VoidType";
        }
        return "";
    }
}
