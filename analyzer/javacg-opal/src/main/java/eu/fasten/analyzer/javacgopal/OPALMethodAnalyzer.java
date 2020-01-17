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

package eu.fasten.analyzer.javacgopal;

import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;

import org.opalj.br.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.collection.JavaConversions;

import java.util.List;

/**
 * Analyze OPAL methods.
 */
public class OPALMethodAnalyzer {

    private static Logger logger = LoggerFactory.getLogger(OPALMethodAnalyzer.class);

    /**
     * Converts a method to a Canonicalized Schemeless eu.fasten.core.data.FastenURI.
     *
     * @param product The product which entity belongs to.
     * @param clas The class of the method in org.opalj.br.ReferenceType format.
     * @param method Name of the method in String.
     * @param descriptor Descriptor of the method in org.opalj.br.MethodDescriptor format.
     * @return @return Canonicalized Schemeless eu.fasten.core.data.FastenURI of the given method.
     */
    public static FastenURI toCanonicalSchemelessURI(String product, ReferenceType clas, String method, MethodDescriptor descriptor) {

        try {
            var JavaURI = FastenJavaURI.create(null, product, null,
                    getPackageName(clas),
                    getClassName(clas),
                    getMethodName(getClassName(clas), method),
                    getParametersURI(JavaConversions.seqAsJavaList(descriptor.parameterTypes())),
                    getTypeURI(descriptor.returnType())
                ).canonicalize();

            return FastenURI.createSchemeless(JavaURI.getRawForge(), JavaURI.getRawProduct(),
                JavaURI.getRawVersion(),
                JavaURI.getRawNamespace(), JavaURI.getRawEntity());

        } catch (IllegalArgumentException | NullPointerException | OutOfMemoryError e) {
            logger.error("{}", e.getMessage());
        }

        return null;
    }

    /**
     * Find the String Method name that FastenURI supports.
     *
     * @param className Name of class that method belongs in String.
     * @param methodName Name of method in String.
     * @return If the method is a constructor the output is the class name. For class initializer
     * (static initialization blocks for the class, and static field initialization), it's
     * pctEncoded "<"init">", otherwise the method name.
     */
    public static String getMethodName(String className,String methodName) {

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

    private static String threeTimesPct(String nonEncoded) {
        return FastenJavaURI.pctEncodeArg(FastenJavaURI.pctEncodeArg(FastenJavaURI.pctEncodeArg(nonEncoded)));
    }

    /**
     * Convert OPAL return types to FastenJavaURI.
     *
     * @param returnType org.opalj.br.Type
     * @return type in FastenJavaURI format.
     */
    public static FastenJavaURI getTypeURI(org.opalj.br.Type returnType) {

        if (getClassName(returnType).contains("Lambda")){
            return new FastenJavaURI("/" + getPackageName(returnType) + "/" + FastenJavaURI.pctEncodeArg(getClassName(returnType)));
        }
        return new FastenJavaURI("/" + getPackageName(returnType) + "/" + getClassName(returnType));
    }

    /**
     * Convert OPAL parameters to FastenJavaURI.
     *
     * @param parametersType Java List of parameters of in OPAL types.
     * @return parameters in FastenJavaURI[].
     */
    public static FastenJavaURI[] getParametersURI(List<FieldType> parametersType) {

        FastenJavaURI[] parameters = new FastenJavaURI[parametersType.size()];

        for (int i = 0; i < parametersType.size(); i++) {
            parameters[i] = getTypeURI(parametersType.get(i));
        }
        return parameters;
    }

    /**
     * Recursively figures out the OPAL types of parameters and convert them to FastenURI
     * namespaces.
     *
     * @param parameter OPAL parameter.
     * @return String in eu.fasten.core.data.FastenURI format, for namespace of the given parameter.
     */
    public static String getPackageName(org.opalj.br.Type parameter) {

        String parameterPackageName = "";

        if (parameter.isBaseType()) {
            parameterPackageName = parameter.asBaseType().WrapperType().packageName();
        } else if (parameter.isReferenceType()) {

            if (parameter.isArrayType()) {
                parameterPackageName = getPackageName(parameter.asArrayType().componentType());
            } else if (parameter.asObjectType().packageName().equals("")) {
                return null;
            } else {
                parameterPackageName = parameter.asObjectType().packageName();
            }

        } else if (parameter.isVoidType()) {
            parameterPackageName = "java.lang";
        }

        return parameterPackageName.replace("/", ".");
    }

    /**
     * Recursively figures out the OPAL types of parameters and convert them to FastenURI type
     * (class).
     *
     * @param parameter OPAL parameter in org.opalj.br.Type format.
     * @return String in eu.fasten.core.data.FastenURI format, for type of the given parameter.
     */
    public static String getClassName(org.opalj.br.Type parameter) {

        String parameterClassName = "";

        if (parameter.isBaseType()) {
            parameterClassName = parameter.asBaseType().WrapperType().simpleName();
        } else if (parameter.isReferenceType()) {

            if (parameter.isArrayType()) {
                parameterClassName = getClassName(parameter.asArrayType().componentType()).concat(threeTimesPct("[]"));

            } else if (parameter.asObjectType().simpleName().contains("Lambda")) {
                return FastenJavaURI.pctEncodeArg(FastenJavaURI.pctEncodeArg(parameter.asObjectType().simpleName()));
            } else {
                parameterClassName = parameter.asObjectType().simpleName();
            }

        } else if (parameter.isVoidType()) {
            parameterClassName = "Void";
        }

        return parameterClassName;
    }
}
