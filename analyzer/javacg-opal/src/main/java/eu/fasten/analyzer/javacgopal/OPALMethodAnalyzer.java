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
     * Given an OPAL method gives us a Canonicalized (reletivized) eu.fasten.core.data.FastenJavaURI.
     *
     * @param method org.opalj.br.Method.
     * @return Canonicalized eu.fasten.core.data.FastenJavaURI of the given method.
     */
    public static FastenURI toCanonicalSchemelessURI(Method method) {

        try {
            var JavaURI = FastenJavaURI.create(null, null, null,
                getPackageName(method.declaringClassFile().thisType()).replace("/",""),
                getClassName(method.declaringClassFile().thisType()).replace("/",""),
                getMethodName(method),
                getParametersURI(JavaConversions.seqAsJavaList(method.parameterTypes())),
                getReturnTypeURI(method.returnType())
            );

            return FastenURI.createSchemeless(JavaURI.getRawForge(), JavaURI.getRawProduct(), JavaURI.getRawVersion(),
                JavaURI.getRawNamespace(), JavaURI.getRawEntity());

        } catch (IllegalArgumentException | NullPointerException e) {
            logger.error("{} ", e.getMessage());
            return null;
        }
    }

    /**
     * Converts an unresolved method to a Canonicalized (reletivized) eu.fasten.core.data.FastenJavaURI.
     *
     * @param calleeClass      The class of the method in org.opalj.br.ReferenceType format.
     * @param calleeName       Name of the class in String.
     * @param calleeDescriptor Descriptor of the method in org.opalj.br.MethodDescriptor format.
     * @return @return Canonicalized eu.fasten.core.data.FastenJavaURI of the given method.
     */
    public static FastenURI toCanonicalSchemelessURI(ReferenceType calleeClass, String calleeName, MethodDescriptor calleeDescriptor) {

        try {
            var JavaURI =
                FastenJavaURI.create(null, "SomeDependency", null,
                    //TODO change SomeDependency to $! when it's supported.
                    getPackageName(calleeClass).replace("/",""),
                    getClassName(calleeClass).replace("/",""),
                    getMethodName(calleeClass, calleeName),
                    getParametersURI(JavaConversions.seqAsJavaList(calleeDescriptor.parameterTypes())),
                    getReturnTypeURI(calleeDescriptor.returnType())
                ).canonicalize();

            return FastenURI.createSchemeless(JavaURI.getRawForge(), JavaURI.getRawProduct(), JavaURI.getRawVersion(),
                JavaURI.getRawNamespace(), JavaURI.getRawEntity());

        } catch (IllegalArgumentException | NullPointerException e) {
            logger.error("{}", e.getMessage());
        }

        return null;
    }

    /**
     * Find the String Method name that FastenURI supports.
     *
     * @param method org.opalj.br.Method
     * @return If the method is a constructor the output is the class name.
     * For class initializer (static initialization blocks for the class,
     * and static field initialization), it's pctEncoded "<"init">", otherwise the method name.
     */
    public static String getMethodName(Method method) {
        if (method.name().equals("<init>")) {
            return method.declaringClassFile().thisType().simpleName();
        } else if (method.name().equals("<clinit>")) {
            return FastenJavaURI.pctEncodeArg("<init>");
        } else return method.name();
    }

    /**
     * Find the String Method name that FastenURI supports.
     *
     * @param method String
     * @return If the method is a constructor the output is the class name.
     * For class initializer (static initialization blocks for the class,
     * and static field initialization), it's pctEncoded "<"init">", otherwise the method name.
     */
    public static String getMethodName(ReferenceType clas, String method) {
        if (method.equals("<init>")) {
            return getClassName(clas).replace("/", "");
        } else if (method.equals("<clinit>")) {
            return FastenJavaURI.pctEncodeArg("<init>");
        } else return method;
    }

    /**
     * Convert OPAL return types to FastenJavaURI.
     *
     * @param returnType Return type of a method in OPAL format.
     * @return return type in FastenJavaURI.
     */
    public static FastenJavaURI getReturnTypeURI(Type returnType) {

        if (getClassName(returnType).contains(FastenJavaURI.pctEncodeArg("[]"))) {
            return new FastenJavaURI(FastenJavaURI.pctEncodeArg(getPackageName(returnType) + getClassName(returnType)));

        }
            return new FastenJavaURI(getPackageName(returnType) + getClassName(returnType));
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

            if (getClassName(parametersType.get(0)).contains(FastenJavaURI.pctEncodeArg("[]"))) {
                parameters[i] = new FastenJavaURI(FastenJavaURI.pctEncodeArg(getPackageName(parametersType.get(i)) + getClassName(parametersType.get(0))));
            }else{
                parameters[i] = new FastenJavaURI(getPackageName(parametersType.get(i)) + getClassName(parametersType.get(0)));
            }

        }

        return parameters;

    }

    /**
     * Recursively figures out the OPAL types of parameters and convert them to FastenURI namespaces.
     *
     * @param parameter OPAL parameter.
     * @return String in eu.fasten.core.data.FastenURI format, for namespace of the given parameter.
     */
    public static String getPackageName(Type parameter) {
        String parameterPackageName = "";
        if (parameter.isBaseType()) {
            parameterPackageName = parameter.asBaseType().WrapperType().packageName();
        } else if (parameter.isReferenceType()) {
            if (parameter.isArrayType()) {
                parameterPackageName = getPackageName(parameter.asArrayType().componentType()).replace("/", "");
            } else
                parameterPackageName = parameter.asObjectType().packageName();
        } else if (parameter.isVoidType()) {
            parameterPackageName = "java.lang";
        }
        parameterPackageName = parameterPackageName.startsWith("/") ? parameterPackageName.substring(1) : parameterPackageName;

        return "/" + parameterPackageName.replace("/", ".");
    }

    /**
     * Recursively figures out the OPAL types of parameters and convert them to FastenURI type (class).
     *
     * @param parameter OPAL parameter in org.opalj.br.Type format.
     * @return String in eu.fasten.core.data.FastenURI format, for type of the given parameter.
     */
    public static String getClassName(Type parameter) {
        String parameterClassName = "";
        if (parameter.isBaseType()) {
            parameterClassName = parameter.asBaseType().WrapperType().simpleName();
        } else if (parameter.isReferenceType()) {
            if (parameter.isArrayType()) {
                parameterClassName = getClassName(parameter.asArrayType().componentType()).replace("/", "").concat(FastenJavaURI.pctEncodeArg("[]"));
            } else
                parameterClassName = parameter.asObjectType().simpleName();
        } else if (parameter.isVoidType()) {
            parameterClassName = "void";
        }
        return "/" + parameterClassName;
    }
}
