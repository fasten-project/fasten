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
import org.json.JSONObject;
import java.util.List;

public class Dependency {

    public final String artifactId;
    public final String groupId;
    public final String version;

    public final List<Exclusion> exclusions;
    public final String scope;
    public final boolean optional;
    public final String type;
    public final String classifier;

    /**
     * Constructor for Dependency object.
     *
     * (From https://maven.apache.org/ref/3.6.3/maven-model/maven.html#class_dependency)
     *
     * @param artifactId artifactId of dependency Maven coordinate
     * @param groupId    groupId of dependency Maven coordinate
     * @param version    version of dependency Maven coordinate
     * @param exclusions List of exclusions
     * @param scope      Scope of the dependency
     * @param optional   Is dependency optional
     * @param type       Type of the dependency
     * @param classifier Classifier for dependency
     */
    public Dependency(final String artifactId, final String groupId, final String version,
                      final List<Exclusion> exclusions, final String scope, final boolean optional,
                      final String type, final String classifier) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.version = version;
        this.exclusions = exclusions;
        this.scope = scope;
        this.optional = optional;
        this.type = type;
        this.classifier = classifier;
    }

    public JSONObject toJSON() {
        final var json = new JSONObject();
        json.put("artifactId", this.artifactId);
        json.put("groupId", this.groupId);
        json.put("version", this.version);
        final var exclusionsJson = new JSONArray();
        for (var exclusion : this.exclusions) {
            exclusionsJson.put(exclusion.toJSON());
        }
        json.put("exclusions", exclusionsJson);
        json.put("scope", this.scope);
        json.put("optional", this.optional);
        json.put("type", this.type);
        json.put("classifier", this.classifier);
        return json;
    }


    public static class Exclusion {

        public final String artifactId;
        public final String groupId;

        /**
         * Constructor for Exclusion object.
         * Exclusion defines a dependency which must be excluded from transitive dependencies.
         *
         * @param artifactId artifactId of excluded Maven coordinate
         * @param groupId    groupId of excluded Maven coordinate
         */
        public Exclusion(final String artifactId, final String groupId) {
            this.artifactId = artifactId;
            this.groupId = groupId;
        }

        public JSONObject toJSON() {
            final var json = new JSONObject();
            json.put("artifactId", this.artifactId);
            json.put("groupId", this.groupId);
            return json;
        }
    }

}
