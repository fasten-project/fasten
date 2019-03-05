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

package eu.fasten.core.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A function node in a call-based dependency network. A function contains
 * a set of function calls that the call graph generator can resolve within
 * the current analysis context ({@link Function#resolvedCalls}) and
 * another set of function calls that need to be resolved by consulting the
 * dependency set for the package that includes this function
 * ({@link Function#unresolvedCalls}).
 */
public abstract class Function implements Serializable {

    public final String fqn;

    public final Set<ResolvedFunction> resolvedCalls;
    public final Set<UnresolvedFunction> unresolvedCalls;

    public final Map<String, String> metadata;

    protected Function(String fqn, Set<ResolvedFunction> resolvedCalls, Set<UnresolvedFunction> unresolvedCalls) {
        this.fqn = fqn;
        this.resolvedCalls = resolvedCalls;
        this.unresolvedCalls = unresolvedCalls;
        metadata = new HashMap<>();
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public Optional<String> getMetadata(String key) {
        return Optional.of(metadata.get(key));
    }
}
