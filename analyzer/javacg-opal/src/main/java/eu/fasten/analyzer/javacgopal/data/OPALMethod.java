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

package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.opalj.br.FieldType;
import org.opalj.br.MethodDescriptor;
import org.opalj.br.ReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConverters;

/**
 * Analyze OPAL methods.
 */
public class OPALMethod {

    private static Logger logger = LoggerFactory.getLogger(OPALMethod.class);

    /**
     * Converts a method to a Canonicalized Schemeless eu.fasten.core.data.FastenURI.
     * @param product    The product which entity belongs to.
     * @param clas       The class of the method in org.opalj.br.ReferenceType format.
     * @param method     Name of the method in String.
     * @param descriptor Descriptor of the method in org.opalj.br.MethodDescriptor format.
     * @return Canonicalized Schemeless eu.fasten.core.data.FastenURI of the given method.
     */
    public static FastenURI toCanonicalSchemelessURI(final String product, final ReferenceType clas,
                                                     final String method,
                                                     final MethodDescriptor descriptor)
        throws IllegalArgumentException, NullPointerException {
        final var javaURI = FastenJavaURI.create(null, product, null,
            getPackageName(clas),
            getClassName(clas),
            getMethodName(getClassName(clas), method),
            getParametersURI(JavaConverters.seqAsJavaList(descriptor.parameterTypes())),
            getTypeURI(descriptor.returnType())
        ).canonicalize();

        return FastenURI.createSchemeless(javaURI.getRawForge(), javaURI.getRawProduct(),
            javaURI.getRawVersion(),
            javaURI.getRawNamespace(), javaURI.getRawEntity());

    }

    /**
     * Find the String OPALMethod name that FastenURI supports.
     * @param className  Name of class that method belongs in String.
     * @param methodName Name of method in String.
     * @return If the method is a constructor the output is the class name. For class initializer
     *     (static initialization blocks for the class, and static field initialization), it's
     *     pctEncoded "<"init">", otherwise the method name.
     */
    public static String getMethodName(final String className, final String methodName) {

        if (methodName.equals("<init>")) {

            if (className.contains("Lambda")) {
                return FastenJavaURI.pctEncodeArg(className);
            } else {
                return className;
            }

        } else if (methodName.equals("<clinit>")) {
            return threeTimesPct("<init>");
        } else {
            return methodName;
        }
    }

    private static String threeTimesPct(final String nonEncoded) {
        return FastenJavaURI
            .pctEncodeArg(FastenJavaURI.pctEncodeArg(FastenJavaURI.pctEncodeArg(nonEncoded)));
    }

    /**
     * Convert OPAL return types to FastenJavaURI.
     * @param returnType org.opalj.br.OPALType
     * @return type in FastenJavaURI format.
     */
    public static FastenJavaURI getTypeURI(final org.opalj.br.Type returnType) {

        if (getClassName(returnType).contains("Lambda")) {
            return new FastenJavaURI("/" + getPackageName(returnType) + "/"
                + FastenJavaURI.pctEncodeArg(getClassName(returnType)));
        }
        return new FastenJavaURI("/" + getPackageName(returnType) + "/" + getClassName(returnType));
    }

    /**
     * Convert OPAL parameters to FastenJavaURI.
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
     * @param parameter OPAL parameter.
     * @return String in eu.fasten.core.data.FastenURI format, for namespace of the given parameter.
     */
    public static String getPackageName(final org.opalj.br.Type parameter) {

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

    private static String slashToDot(final String s) {
        return s.replace("/", ".");
    }

    /**
     * Recursively figures out the OPAL types of parameters and convert them to FastenURI type
     * (class).
     * @param parameter OPAL parameter in org.opalj.br.OPALType format.
     * @return String in eu.fasten.core.data.FastenURI format, for type of the given parameter.
     */
    public static String getClassName(final org.opalj.br.Type parameter) {


        if (parameter.isBaseType()) {

            return parameter.asBaseType().toString();

        } else if (parameter.isReferenceType()) {

            if (parameter.isArrayType()) {
                return getClassName(parameter.asArrayType().componentType())
                    .concat(threeTimesPct("[]"));

            } else if (parameter.asObjectType().simpleName().contains("Lambda")) {
                return FastenJavaURI.pctEncodeArg(
                    FastenJavaURI.pctEncodeArg(parameter.asObjectType().simpleName()));
            } else {

                return parameter.asObjectType().simpleName();
            }

        } else if (parameter.isVoidType()) {
            return "VoidType";
        }

        return "";
    }
}
