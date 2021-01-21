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

package eu.fasten.core.maven.data;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class DependencyEdge implements Serializable {

    public Revision source;
    public Revision target;
    public String scope;
    public boolean optional;
    public List<Dependency.Exclusion> exclusions;
    public String type;

    public DependencyEdge() {
    }

    public DependencyEdge(Revision source, Revision target, String scope, boolean optional,
                          List<Dependency.Exclusion> exclusions, String type) {
        this.source = source;
        this.target = target;
        this.scope = scope;
        this.optional = optional;
        this.exclusions = exclusions;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DependencyEdge that = (DependencyEdge) o;
        if (optional != that.optional) {
            return false;
        }
        if (!Objects.equals(source, that.source)) {
            return false;
        }
        if (!Objects.equals(target, that.target)) {
            return false;
        }
        if (!Objects.equals(scope, that.scope)) {
            return false;
        }
        if (!Objects.equals(type, that.type)) {
            return false;
        }
        return Objects.equals(exclusions, that.exclusions);
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + (target != null ? target.hashCode() : 0);
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (optional ? 1 : 0);
        result = 31 * result + (exclusions != null ? exclusions.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DependencyEdge{" +
                "source=" + source +
                ", target=" + target +
                ", scope='" + scope + '\'' +
                ", optional=" + optional +
                ", exclusions=" + exclusions +
                ", type='" + type + '\'' +
                '}';
    }
}
