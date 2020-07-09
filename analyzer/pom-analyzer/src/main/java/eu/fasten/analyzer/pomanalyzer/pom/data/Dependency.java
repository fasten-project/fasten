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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

public class Dependency {

    public final String artifactId;
    public final String groupId;
    public final String version;    // TODO: Change type to String[] as in RevisionCallGraph

    public final List<Exclusion> exclusions;
    public final String scope;
    public final boolean optional;
    public final String type;
    public final String classifier;

    /**
     * Constructor for Dependency object.
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

    public Dependency(final String artifactId, final String groupId, final String version) {
        this(artifactId, groupId, version, new ArrayList<>(), "", false, "", "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Dependency that = (Dependency) o;
        if (optional != that.optional) {
            return false;
        }
        if (!artifactId.equals(that.artifactId)) {
            return false;
        }
        if (!groupId.equals(that.groupId)) {
            return false;
        }
        if (!Objects.equals(version, that.version)) {
            return false;
        }
        if (!Objects.equals(exclusions, that.exclusions)) {
            return false;
        }
        if (!Objects.equals(scope, that.scope)) {
            return false;
        }
        if (!Objects.equals(type, that.type)) {
            return false;
        }
        return Objects.equals(classifier, that.classifier);
    }

    /**
     * Converts Dependency object into JSON.
     *
     * @return JSONObject representation of dependency
     */
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

    /**
     * Creates a Dependency object from JSON.
     *
     * @param json JSONObject representation of dependency
     * @return Dependency object
     */
    public static Dependency fromJSON(JSONObject json) {
        var artifactId = json.getString("artifactId");
        var groupId = json.getString("groupId");
        var version = json.optString("version");
        var exclusions = new ArrayList<Exclusion>();
        if (json.has("exclusions")) {
            var exclusionsJson = json.getJSONArray("exclusions");
            for (var i = 0; i < exclusionsJson.length(); i++) {
                exclusions.add(Exclusion.fromJSON(exclusionsJson.getJSONObject(i)));
            }
        }
        var scope = json.optString("scope");
        var optional = json.optBoolean("optional", false);
        var type = json.optString("type");
        var classifier = json.optString("classifier");
        return new Dependency(
                artifactId, groupId, version, exclusions, scope, optional, type, classifier
        );
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Exclusion exclusion = (Exclusion) o;
            if (!artifactId.equals(exclusion.artifactId)) {
                return false;
            }
            return groupId.equals(exclusion.groupId);
        }

        /**
         * Converts Exclusion object into JSON.
         *
         * @return JSONObject representation of exclusion
         */
        public JSONObject toJSON() {
            final var json = new JSONObject();
            json.put("artifactId", this.artifactId);
            json.put("groupId", this.groupId);
            return json;
        }

        /**
         * Creates a Exclusion object from JSON.
         *
         * @param json JSONObject representation of exclusion
         * @return Exclusion object
         */
        public static Exclusion fromJSON(JSONObject json) {
            var artifactId = json.getString("artifactId");
            var groupId = json.getString("groupId");
            return new Exclusion(artifactId, groupId);
        }
    }

}
