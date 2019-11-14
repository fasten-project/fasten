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

import eu.fasten.core.data.FastenJavaURI;
import org.opalj.br.FieldType;
import org.opalj.br.Method;
import org.opalj.br.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConversions;

import java.util.List;

public class MethodAnalyzer {
    private static Logger logger = LoggerFactory.getLogger(MethodAnalyzer.class);

    public static FastenJavaURI toCanonicalFastenJavaURI(Method method) {

        String parameters = getPctParameters(JavaConversions.seqAsJavaList(method.parameterTypes()));
        String returnType = getPctReturnType(method.returnType());
        String fqn = method.declaringClassFile().fqn();
        String namespace = "";
        try {
            namespace = fqn.substring(0, fqn.lastIndexOf("/")).replace("/", ".");
        } catch (StringIndexOutOfBoundsException e) {
            logger.error("Can not find the namespace of {}. Faced {}", JVMFormat.toJVMMethod(method), e.getMessage());
        }
        String className = method.declaringClassFile().thisType().simpleName();
        String URIString = "/" + namespace + "/" + className + "." + method.name() + "(" + parameters + ")" + returnType;

        try {
            return new FastenJavaURI(URIString).canonicalize();
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.error("{} faced {}", JVMFormat.toJVMMethod(method), e.getMessage());
            return null;
        }
    }

    public static String getPctReturnType(Type returnType) {
        return FastenJavaURI.pctEncodeArg(getPackageName(returnType) + getClassName(returnType));
    }

    public static String getPctParameters(List<FieldType> inputParameters) {

        String parameters = "";
        for (Type parameter : inputParameters) {
            parameters = parameters + FastenJavaURI.pctEncodeArg(getPackageName(parameter) + getClassName(parameter)) + ",";
        }
        parameters = parameters.equals("") ? "" : parameters.substring(0, parameters.length() - 1);
        return parameters;

    }

    public static String getPackageName(Type parameter) {
        String parameterPackageName = "";
        if (parameter.isBaseType()) {
            parameterPackageName = parameter.asBaseType().WrapperType().packageName().replace("/", ".");
        } else if (parameter.isReferenceType()) {
            if (parameter.isArrayType()) {
                parameterPackageName = getPackageName(parameter.asArrayType().componentType()).replace("/", ".");
            } else
                parameterPackageName = parameter.asObjectType().packageName().replace("/", ".");
        } else if (parameter.isVoidType()) {
            parameterPackageName = "";
        }
        parameterPackageName = parameterPackageName.isEmpty() ? "" : "/" + parameterPackageName;
        return parameterPackageName;
    }

    public static String getClassName(Type parameter) {
        String parameterClassName = "";
        if (parameter.isBaseType()) {
            parameterClassName = "/" + parameter.asBaseType().WrapperType().simpleName();
        } else if (parameter.isReferenceType()) {
            if (parameter.isArrayType()) {
                parameterClassName = "/" + getClassName(parameter.asArrayType().componentType());
            } else
                parameterClassName = "/" + parameter.asObjectType().simpleName();
        } else if (parameter.isVoidType()) {
            parameterClassName = "/VoidType";
        }
        return parameterClassName;
    }
}
