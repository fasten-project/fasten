/*
 * Copyright 2021 Delft University of Technology
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
package eu.fasten.core.maven.data;

import static eu.fasten.core.maven.data.Scope.COMPILE;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.json.JSONArray;
import org.json.JSONObject;

import eu.fasten.core.data.Constants;

public class Dependency {

    private static final String JAR = "jar";
    private static final Set<Exclusion> NO_EXCLS = Set.of();
    private static final String EMPTY_STR = "";

    public final String groupId;
    public final String artifactId;
    public final boolean optional;

    private final String classifier;
    private final Scope scope;
    private final String type;

    private final VersionConstraint versionConstraint;
    private final Set<VersionConstraint> versionConstraints;
    private final Set<Exclusion> exclusions;

    private final int hashCode;

    public Dependency(String groupId, String artifactId, String version) {
        this(groupId, artifactId, VersionConstraint.parseVersionSpec(version), Set.of(), Scope.COMPILE, false, JAR, "");
    }

    public Dependency(String groupId, String artifactId, Set<VersionConstraint> versionConstraints,
            Set<Exclusion> exclusions, Scope scope, boolean optional, String type, String classifier) {

        this.groupId = groupId;
        this.artifactId = artifactId;
        this.optional = optional;

        if (classifier != null && !classifier.isEmpty()) {
            this.classifier = classifier.toLowerCase();
        } else {
            this.classifier = null;
        }
        if (scope != COMPILE) {
            this.scope = scope;
        } else {
            this.scope = null;
        }
        if (type != null && !type.isEmpty() && !JAR.equals(type)) {
            this.type = type.toLowerCase();
        } else {
            this.type = null;
        }

        if (versionConstraints.size() == 1) {
            this.versionConstraint = versionConstraints.iterator().next();
            this.versionConstraints = null;
        } else {
            this.versionConstraint = null;
            this.versionConstraints = Set.copyOf(versionConstraints);
        }

        if (exclusions.isEmpty()) {
            this.exclusions = NO_EXCLS;
        } else {
            this.exclusions = Set.copyOf(exclusions);
        }

        hashCode = calcHashCode();
    }

    private int calcHashCode() {
        final var prime = 31;
        var hashCode = 0;
        hashCode = prime * hashCode + ((groupId == null) ? 0 : groupId.hashCode());
        hashCode = prime * hashCode + ((artifactId == null) ? 0 : artifactId.hashCode());
        hashCode = prime * hashCode + (optional ? 1231 : 1237);

        hashCode = prime * hashCode + ((classifier == null) ? 0 : classifier.hashCode());
        hashCode = prime * hashCode + ((scope == null) ? 0 : scope.hashCode());
        hashCode = prime * hashCode + ((type == null) ? 0 : type.hashCode());

        hashCode = prime * hashCode + ((versionConstraint == null) ? 0 : versionConstraint.hashCode());
        hashCode = prime * hashCode + ((versionConstraints == null) ? 0 : versionConstraints.hashCode());
        hashCode = prime * hashCode + ((exclusions == null) ? 0 : exclusions.hashCode());
        return hashCode;
    }

    public String toGA() {
        return new StringBuilder().append(groupId).append(':').append(artifactId).toString();
    }

    public Set<VersionConstraint> getVersionConstraints() {
        if (versionConstraints == null) {
            return Set.of(versionConstraint);
        }
        return versionConstraints;
    }

    public Set<Exclusion> getExclusions() {
        return exclusions;
    }

    public Scope getScope() {
        if (scope == null) {
            return COMPILE;
        }
        return scope;
    }

    public boolean isOptional() {
        return optional;
    }

    public String getPackagingType() {
        if (type == null) {
            return JAR;
        }
        return type;
    }

    public String getClassifier() {
        if (classifier == null) {
            return EMPTY_STR;
        }
        return classifier;
    }

    @Deprecated
    public String[] getVersionConstraintsArr() {
        var constraints = new String[getVersionConstraints().size()];
        var i = new int[] { 0 };
        getVersionConstraints().forEach(vc -> {
            constraints[i[0]++] = vc.getSpec();
        });
        return constraints;
    }

    @Deprecated
    public String getVersion() {
        return String.join(",", this.getVersionConstraintsArr());
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    // TODO delete everything below

    @Deprecated
    public String toCanonicalForm() {
        var builder = new StringBuilder();
        builder.append(this.groupId);
        builder.append(Constants.mvnCoordinateSeparator);
        builder.append(this.artifactId);
        builder.append(Constants.mvnCoordinateSeparator);
        if (!this.type.isEmpty()) {
            builder.append(this.type);
            builder.append(Constants.mvnCoordinateSeparator);
        }
        if (!this.classifier.isEmpty()) {
            builder.append(this.classifier);
            builder.append(Constants.mvnCoordinateSeparator);
        }
        builder.append(this.getVersion());
        return builder.toString();
    }

    @Deprecated
    public String toMavenCoordinate() {
        return this.groupId + Constants.mvnCoordinateSeparator + this.artifactId + Constants.mvnCoordinateSeparator
                + this.getVersion();
    }

    @Deprecated
    public String toFullCanonicalForm() {
        var builder = new StringBuilder();
        builder.append(this.groupId);
        builder.append(Constants.mvnCoordinateSeparator);
        builder.append(this.artifactId);
        builder.append(Constants.mvnCoordinateSeparator);
        if (!this.type.isEmpty()) {
            builder.append(this.type);
        } else {
            builder.append("jar");
        }
        builder.append(Constants.mvnCoordinateSeparator);
        if (!this.classifier.isEmpty()) {
            builder.append(this.classifier);
            builder.append(Constants.mvnCoordinateSeparator);
        }
        builder.append(this.getVersion());
        builder.append(Constants.mvnCoordinateSeparator);
        builder.append(this.scope);
        return builder.toString();
    }

    @Deprecated // see eu.fasten.core.json.CoreMavenDataModule
    public JSONObject toJSON() {
        final var json = new JSONObject();
        json.put("artifactId", this.artifactId);
        json.put("groupId", this.groupId);
        final var constraintsJson = new JSONArray();
        for (var constraint : this.getVersionConstraints()) {
            constraintsJson.put(constraint.getSpec());
        }
        json.put("versionConstraints", constraintsJson);
        final var exclusionsJson = new JSONArray();
        for (var exclusion : this.getExclusions()) {
            exclusionsJson.put(exclusion.toJSON());
        }
        json.put("exclusions", exclusionsJson);
        json.put("scope", this.scope);
        json.put("optional", this.optional);
        json.put("type", this.type);
        json.put("classifier", this.classifier);
        return json;
    }

    @Deprecated // see eu.fasten.core.json.CoreMavenDataModule
    public static Dependency fromJSON(JSONObject json) {
        var artifactId = json.getString("artifactId");
        var groupId = json.getString("groupId");
        var versionConstraints = new HashSet<VersionConstraint>();
        if (json.has("versionConstraints")) {
            json.getJSONArray("versionConstraints").forEach(s -> {
                versionConstraints.add(new VersionConstraint((String) s));
            });
        }
        var exclusions = new HashSet<Exclusion>();
        if (json.has("exclusions")) {
            var exclusionsJson = json.getJSONArray("exclusions");
            for (var i = 0; i < exclusionsJson.length(); i++) {
                var exclJson = exclusionsJson.getString(i);
                exclusions.add(Exclusion.fromJSON(exclJson));
            }
        }
        Scope scope;
        var scopeStr = json.optString("scope").strip();
        if (scopeStr == null || scopeStr.isEmpty()) {
            scope = Scope.COMPILE;
        } else {
            scope = Scope.valueOf(scopeStr.toUpperCase());
        }
        var optional = json.optBoolean("optional", false);
        var type = json.optString("type");
        var classifier = json.optString("classifier");
        return new Dependency(groupId, artifactId, versionConstraints, exclusions, scope, optional, type, classifier);
    }
}