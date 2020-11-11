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

public class DependencyManagement {

    public final List<Dependency> dependencies;

    /**
     * Constructor for DependencyManagement object.
     * Defines the default dependency information for POMs that inherit from this one.
     * (From https://maven.apache.org/ref/3.6.3/maven-model/maven.html#class_dependencyManagement)
     *
     * @param dependencies List of Dependencies
     */
    public DependencyManagement(final List<Dependency> dependencies) {
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
        DependencyManagement that = (DependencyManagement) o;
        return dependencies.equals(that.dependencies);
    }

    /**
     * Converts DependencyManagement object into JSON.
     *
     * @return JSONObject representation of dependency management
     */
    public JSONObject toJSON() {
        final var dependenciesJson = new JSONArray();
        for (var dependency : this.dependencies) {
            dependenciesJson.put(dependency.toJSON());
        }
        final var json = new JSONObject();
        json.put("dependencies", dependenciesJson);
        return json;
    }

    /**
     * Creates a DependencyManagement object from JSON.
     *
     * @param json JSONObject representation of dependency management
     * @return DependencyManagement object
     */
    public static DependencyManagement fromJSON(JSONObject json) {
        var dependencies = new ArrayList<Dependency>();
        var dependenciesJson = json.getJSONArray("dependencies");
        for (var i = 0; i < dependenciesJson.length(); i++) {
            dependencies.add(Dependency.fromJSON(dependenciesJson.getJSONObject(i)));
        }
        return new DependencyManagement(dependencies);
    }
}
