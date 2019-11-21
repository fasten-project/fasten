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

import org.opalj.br.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.collection.JavaConversions;

import java.util.List;

/**
 * Analyze OPAL methods.
 */
public class OPALMethodAnalyzer{


    private static Logger logger = LoggerFactory.getLogger(OPALMethodAnalyzer.class);

    /**
     * Given an OPAL method gives us a Canonicalized (reletivized) eu.fasten.core.data.FastenJavaURI.
     *
     * @param method A Method in OPAL format.
     *
     * @return Canonicalized eu.fasten.core.data.FastenJavaURI of the given method.
     */
    public static FastenJavaURI toCanonicalFastenJavaURI(Method method) {

        String parameters = getPctParameters(JavaConversions.seqAsJavaList(method.parameterTypes()));
        String returnType = getPctReturnType(method.returnType());
        String fqn = method.declaringClassFile().fqn();
        String namespace = "";
        try {
            namespace = fqn.substring(0, fqn.lastIndexOf("/")).replace("/", ".");
        } catch (StringIndexOutOfBoundsException e) {
            logger.error("Can not find the namespace. {} happened for this URI: {}", e.getMessage(), JVMFormat.toJVMMethod(method));
        }
        String className = method.declaringClassFile().thisType().simpleName();
        String URIString = "/" + namespace + "/" + className + "." + method.name() + "(" + parameters + ")" + returnType;

        try {
            return new FastenJavaURI(URIString).canonicalize();
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.error("{} ", e.getMessage());
            return null;
        }
    }

    /**
     * Converts an unresolved method to a Canonicalized (reletivized) eu.fasten.core.data.FastenJavaURI.
     *
     * @param calleeClass The class of the method in org.opalj.br.ReferenceType format.
     * @param calleeName Name of the class in String.
     * @param calleeDescriptor Descriptor of the method in org.opalj.br.MethodDescriptor format.
     *
     * @return @return Canonicalized eu.fasten.core.data.FastenJavaURI of the given method.
     */
    public static FastenJavaURI toCanonicalFastenJavaURI(ReferenceType calleeClass, String calleeName, MethodDescriptor calleeDescriptor) {

        String URIString = "//SomeDependency" +
            getPackageName(calleeClass) +
            getClassName(calleeClass) +
            "." + calleeName +
            "(" + getPctParameters(JavaConversions.seqAsJavaList(calleeDescriptor.parameterTypes())) +
            ")" + getPctReturnType(calleeDescriptor.returnType());

        try {
            return new FastenJavaURI(URIString).canonicalize();
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.error("{}", e.getMessage());
        }

        return null;
    }

    /**
     * Convert OPAL return types to Fasten pct Format.
     *
     * @param returnType Return type of a method in OPAL format.
     *
     * @return Fasten pct encoded return type, e.g. it always replaces / with %2F.
     */
    public static String getPctReturnType(Type returnType) {
        return FastenJavaURI.pctEncodeArg(getPackageName(returnType) + getClassName(returnType));
    }

    /**
     * Convert OPAL parameters to pct Format.
     *
     * @param parametersType Java List of parameters of in OPAL types.
     *
     * @return Pct encoded return type in string, e.g. pct always replaces "/" with "%2F".
     */
    public static String getPctParameters(List<FieldType> parametersType) {

        String parameters = "";
        for (Type parameter : parametersType) {
            parameters = parameters + FastenJavaURI.pctEncodeArg(getPackageName(parameter) + getClassName(parameter)) + ",";
        }
        parameters = parameters.equals("") ? "" : parameters.substring(0, parameters.length() - 1);
        return parameters;

    }

    /**
     * Recursively figures out the OPAL types of parameters and convert them to FastenURI namespaces.
     *
     * @param parameter OPAL parameter.
     *
     * @return String in eu.fasten.core.data.FastenURI format, for namespace of the given parameter.
     */
    public static String getPackageName(Type parameter) {
        String parameterPackageName = "";
        if (parameter.isBaseType()) {
            parameterPackageName = parameter.asBaseType().WrapperType().packageName();
        } else if (parameter.isReferenceType()) {
            if (parameter.isArrayType()) {
                parameterPackageName = getPackageName(parameter.asArrayType().componentType());
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
     *
     * @return String in eu.fasten.core.data.FastenURI format, for type of the given parameter.
     */
    public static String getClassName(Type parameter) {
        String parameterClassName = "";
        if (parameter.isBaseType()) {
            parameterClassName =  parameter.asBaseType().WrapperType().simpleName();
        } else if (parameter.isReferenceType()) {
            if (parameter.isArrayType()) {
                parameterClassName =  getClassName(parameter.asArrayType().componentType());
            } else
                parameterClassName =  parameter.asObjectType().simpleName();
        } else if (parameter.isVoidType()) {
            parameterClassName = "void";
        }
        return "/" + parameterClassName;
    }
}
