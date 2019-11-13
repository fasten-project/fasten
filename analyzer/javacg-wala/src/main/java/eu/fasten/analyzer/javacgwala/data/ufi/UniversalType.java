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


import eu.fasten.analyzer.javacgwala.data.type.Namespace;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

public class UniversalType implements Serializable {
    public final Optional<Namespace> outer;
    public final Namespace inner;

    public UniversalType(Optional<Namespace> outer, Namespace inner) {
        this.outer = outer;
        this.inner = inner;
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
        UniversalType ty = (UniversalType) o;
        return Objects.equals(this.outer, ty.outer) &&
                Objects.equals(this.inner, ty.inner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.outer, this.inner);
    }
}
