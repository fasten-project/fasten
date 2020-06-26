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

package eu.fasten.analyzer.pomanalyzer.pom.data;

import org.json.JSONArray;
import java.util.List;

public class DependencyManagement {

    public final List<Dependency> dependencies;

    /**
     * Constructor for DependencyManagement object.
     * Defines the default dependency information for projects that inherit from this one.
     * <p>
     * (From https://maven.apache.org/ref/3.6.3/maven-model/maven.html#class_dependencyManagement)
     *
     * @param dependencies List of Dependencies
     */
    public DependencyManagement(final List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public JSONArray toJSON() {
        final var dependenciesJson = new JSONArray();
        for (var dependency : this.dependencies) {
            dependenciesJson.put(dependency.toJSON());
        }
        return dependenciesJson;
    }
}
