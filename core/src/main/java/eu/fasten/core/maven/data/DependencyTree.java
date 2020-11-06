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

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Objects;

public class DependencyTree {

    public Dependency artifact;
    public List<DependencyTree> dependencies;

    public DependencyTree(Dependency artifact, List<DependencyTree> dependencies) {
        this.artifact = artifact;
        this.dependencies = dependencies;
    }

    public JSONObject toJSON() {
        var json = new JSONObject();
        json.put("artifact", artifact.toCanonicalForm());
        if (dependencies != null) {
            var jsonDependencies = new JSONArray();
            for (var dep : dependencies) {
                jsonDependencies.put(dep.toJSON());
            }
            json.put("dependencies", jsonDependencies);
        } else {
            json.put("dependencies", new JSONArray());
        }
        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DependencyTree that = (DependencyTree) o;
        if (!Objects.equals(artifact, that.artifact)) {
            return false;
        }
        return Objects.equals(dependencies, that.dependencies);
    }

    @Override
    public int hashCode() {
        int result = artifact != null ? artifact.hashCode() : 0;
        result = 31 * result + (dependencies != null ? dependencies.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }
}
