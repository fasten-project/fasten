/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.maven.resolution;

import static eu.fasten.core.maven.data.Scope.RUNTIME;
import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;

import java.util.Date;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import eu.fasten.core.maven.data.Scope;

public class ResolverConfig {

    public long resolveAt = new Date().getTime();
    public int depth = Integer.MAX_VALUE;
    public Scope scope = RUNTIME;
    public int limit = Integer.MAX_VALUE;

    /**
     * if false, include only direct dependencies
     */
    public boolean alwaysIncludeProvided;

    /**
     * if false, include only direct dependencies
     */
    public boolean alwaysIncludeOptional;

    public ResolverConfig at(long timestamp) {
        this.resolveAt = timestamp;
        return this;
    }

    public ResolverConfig includeTransitiveDeps() {
        this.depth = Integer.MAX_VALUE;
        return this;
    }

    public ResolverConfig excludeTransitiveDeps() {
        this.depth = 1;
        return this;
    }

    public ResolverConfig limitTransitiveDeps(int depth) {
        if (depth < 1) {
            var msg = "Resolution depth must be >0, but was %d";
            throw new MavenResolutionException(String.format(msg, depth));
        }
        this.depth = depth;
        return this;
    }

    public boolean isExcludingTransitiveDeps() {
        return depth == 1;
    }

    public ResolverConfig limit(int limit) {
        this.limit = limit;
        return this;
    }

    public ResolverConfig scope(Scope scope) {
        this.scope = scope;
        return this;
    }

    /**
     * if false, include only direct dependencies
     */
    public ResolverConfig alwaysIncludeProvided(boolean alwaysIncludeProvided) {
        this.alwaysIncludeProvided = alwaysIncludeProvided;
        return this;
    }

    /**
     * if false, include only direct dependencies
     */
    public ResolverConfig alwaysIncludeOptional(boolean alwaysIncludeOptional) {
        this.alwaysIncludeOptional = alwaysIncludeOptional;
        return this;
    }

    public static ResolverConfig resolve() {
        return new ResolverConfig();
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, MULTI_LINE_STYLE);
    }
}