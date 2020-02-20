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

import eu.fasten.core.data.FastenURI;
import java.util.Objects;

public class Call {

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

    private Method source;
    private Method target;
    private final CallType callType;

    /**
     * Construct call given source and target methods and call type.
     *
     * @param source   Caller
     * @param target   Callee
     * @param callType Call type
     */
    public Call(Method source, Method target, CallType callType) {
        this.source = source;
        this.target = target;
        this.callType = callType;
    }

    public Method getSource() {
        return source;
    }

    public Method getTarget() {
        return target;
    }

    public CallType getCallType() {
        return callType;
    }

    /**
     * Convert a call to FastenURI array in which 0th element represents caller URI
     * and 1st represents callee URI.
     *
     * @return FastenURI array
     */
    public FastenURI[] toURICall() {

        FastenURI[] fastenURI = new FastenURI[2];

        var sourceURI = source.toCanonicalSchemalessURI();
        var targetURI = target.toCanonicalSchemalessURI();

        fastenURI[0] = sourceURI;
        fastenURI[1] = FastenURI.create("//" + targetURI.toString());

        return fastenURI;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Call call = (Call) o;
        return Objects.equals(source, call.source)
                && Objects.equals(target, call.target)
                && callType == call.callType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, callType);
    }
}
