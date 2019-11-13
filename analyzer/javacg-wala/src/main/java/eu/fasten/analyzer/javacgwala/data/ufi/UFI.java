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


package eu.fasten.analyzer.javacgwala.data.ufi;

import eu.fasten.analyzer.javacgwala.data.type.JDKPackage;
import eu.fasten.analyzer.javacgwala.data.type.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.type.Namespace;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class UFI implements Serializable {
    public final static String DELIM = "---";
    public final UniversalType pathType;
    public final String methodName;
    public final Optional<List<UniversalType>> parameters;
    public final UniversalType returnType;

    public UFI(UniversalType pathType,
               String methodName,
               Optional<List<UniversalType>> parameters,
               UniversalType returnType) {
        this.pathType = pathType;
        this.methodName = methodName;
        this.parameters = parameters;
        this.returnType = returnType;
    }

    public static String stringBuilder(UniversalType uty) {

        String buildup = "";

        if (uty.outer.isPresent()) {

            Namespace global = uty.outer.get();

            if (global instanceof MavenCoordinate) {
                buildup += "mvn";


            } else if (global instanceof JDKPackage) {
                buildup += "jdk";
            }

            buildup += DELIM;
            buildup += String.join(global.getNamespaceDelim(), global.getSegments());
            buildup += DELIM;
            buildup += String.join(uty.inner.getNamespaceDelim(), uty.inner.getSegments());


        } else {
            buildup += String.join(uty.inner.getNamespaceDelim(), uty.inner.getSegments());
        }


        if (uty instanceof ArrayType) {
            ArrayType aty = (ArrayType) uty;
            String brackets = IntStream
                    .rangeClosed(1, aty.brackets)
                    .mapToObj(i -> "[]").collect(Collectors.joining(""));

            buildup += brackets;
        }
        return buildup;
    }

    @Override
    public String toString() {

        String args = parameters.isPresent() ?
                parameters.get()
                        .stream()
                        .map(UFI::stringBuilder)
                        .collect(Collectors.joining(",")) : "";


        return stringBuilder(this.pathType) + DELIM
                + this.methodName
                + "<" + args + ">"
                + stringBuilder(this.returnType);
    }


    @Override
    public boolean equals(Object o) {
        // self check
        if (this == o)
            return true;
        // null check
        if (o == null)
            return false;
        // type check and cast
        if (getClass() != o.getClass())
            return false;
        UFI ufi = (UFI) o;
        return Objects.equals(this.toString(), ufi.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.toString());
    }
}
