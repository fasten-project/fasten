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


package eu.fasten.analyzer.javacgwala.lapp.call;

import eu.fasten.analyzer.javacgwala.lapp.core.Method;
import eu.fasten.analyzer.javacgwala.lapp.core.ResolvedMethod;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;

import java.util.Objects;

public class Call extends Edge {

    public enum CallType {
        UNKNOWN("unknown"),
        INTERFACE("invoke_interface"),
        VIRTUAL("invoke_virtual"),
        SPECIAL("invoke_special"),
        STATIC("invoke_static");

        public final String label;

        CallType(String label) {
            this.label = label;
        }

    }

    public final CallType callType;

    public Call(Method source, Method callee, CallType callType) {
        super(source, callee);
        this.callType = callType;
    }

    /**
     * Convert a call to FastenURI array in which 0th element represents caller URI
     * and 1st represents callee URI.
     *
     * @param source Caller
     * @param target Callee
     * @return FastenURI array
     */
    public FastenURI[] toURICall(FastenJavaURI source, FastenJavaURI target) {

        FastenURI[] fastenURI = new FastenURI[2];

        var sourceURI = Method.toCanonicalSchemalessURI(source);

        var targetURI = Method.toCanonicalSchemalessURI(target);

        fastenURI[0] = sourceURI;
        fastenURI[1] = FastenURI.create("//" + targetURI.toString());

        return fastenURI;
    }

    @Override
    public String getLabel() {
        return callType.label;
    }

    public boolean isResolved() {
        return source instanceof ResolvedMethod && target instanceof ResolvedMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Call call = (Call) o;
        return callType == call.callType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), callType);
    }
}
