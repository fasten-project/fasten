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

import java.util.EnumMap;
import java.util.Map;

/**
 * Builder for {@link ExtendedRevisionCCallGraph}.
 */
public final class ExtendedBuilderC extends ExtendedBuilder<EnumMap<CScope, Map<String, Map<Integer, CNode>>>> {
    public String architecture;

    public String getArchitecture() {
        return architecture;
    }

    public ExtendedBuilderC nodeCount(final int nodeCount) {
        return this;
    }

    public ExtendedBuilderC forge(final String forge) {
        this.forge = forge;
        return this;
    }

    public ExtendedBuilderC product(final String product) {
        this.product = product;
        return this;
    }

    public ExtendedBuilderC version(final String version) {
        this.version = version;
        return this;
    }

    public ExtendedBuilderC architecture(final String architecture) {
        this.architecture = architecture;
        return this;
    }

    public ExtendedBuilderC cgGenerator(final String cgGenerator) {
        this.cgGenerator = cgGenerator;
        return this;
    }

    public ExtendedBuilderC timestamp(final long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public ExtendedBuilderC graph(final Graph graph) {
        this.graph = graph;
        return this;
    }

    public ExtendedBuilderC classHierarchy(final EnumMap<CScope, Map<String, Map<Integer, CNode>>> cha) {
        this.classHierarchy = cha;
        return this;
    }

    public ExtendedRevisionCCallGraph build() {
        return new ExtendedRevisionCCallGraph(this);
    }
}
