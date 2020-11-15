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

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class DependencyData {

    public final DependencyManagement dependencyManagement;
    public final List<Dependency> dependencies;

    /**
     * Constructor for DependencyData object.
     * DependencyData contains all the dependency information for some project.
     *
     * @param dependencyManagement Project's dependencyManagement
     * @param dependencies         List of project's dependencies
     */
    public DependencyData(final DependencyManagement dependencyManagement,
                          final List<Dependency> dependencies) {
        this.dependencyManagement = dependencyManagement;
        this.dependencies = dependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DependencyData that = (DependencyData) o;
        if (!dependencyManagement.equals(that.dependencyManagement)) {
            return false;
        }
        return dependencies.equals(that.dependencies);
    }

    /**
     * Converts DependencyData object into JSON.
     *
     * @return JSONObject representation of dependency data
     */
    public JSONObject toJSON() {
        final var json = new JSONObject();
        json.put("dependencyManagement", this.dependencyManagement.toJSON());
        final var dependenciesJson = new JSONArray();
        for (var dependency : this.dependencies) {
            dependenciesJson.put(dependency.toJSON());
        }
        json.put("dependencies", dependenciesJson);
        return json;
    }

    /**
     * Creates a DependencyData object from JSON.
     *
     * @param json JSONObject representation of dependency data
     * @return DependencyData object
     */
    public static DependencyData fromJSON(JSONObject json) {
        var dependencyManagement = DependencyManagement.fromJSON(
                json.getJSONObject("dependencyManagement")
        );
        var dependencies = new ArrayList<Dependency>();
        var dependenciesJson = json.getJSONArray("dependencies");
        for (var i = 0; i < dependenciesJson.length(); i++) {
            dependencies.add(Dependency.fromJSON(dependenciesJson.getJSONObject(i)));
        }
        return new DependencyData(dependencyManagement, dependencies);
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
}
