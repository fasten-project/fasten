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

import static eu.fasten.core.utils.Asserts.assertNotNullOrEmpty;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.json.JSONArray;
import org.json.JSONObject;

import eu.fasten.core.data.Constants;

public class Dependency extends MavenProduct {

    private int hashCode;

    private final Set<VersionConstraint> versionConstraints = new HashSet<>();
    private final Set<Exclusion> exclusions = new HashSet<>();
    private Scope scope = Scope.COMPILE;
    private boolean optional;
    private String type = "jar";
    private String classifier;

    public Dependency() {
        this("", "", "");
    }

    public Dependency(final String groupId, final String artifactId, final String version) {
        this(groupId, artifactId, version, new HashSet<>(), Scope.COMPILE, false, "jar", "");
    }

    public Dependency(final String groupId, final String artifactId, final String version,
            final Set<Exclusion> exclusions, final Scope scope, final boolean optional, final String type,
            final String classifier) {
        this(groupId, artifactId, VersionConstraint.parseVersionSpec(version), exclusions, scope, optional, type,
                classifier);
    }

    public Dependency(final String groupId, final String artifactId, final Set<VersionConstraint> versionConstraints,
            final Set<Exclusion> exclusions, final Scope scope, final boolean optional, final String type,
            final String classifier) {

        this.groupId = groupId;
        this.artifactId = artifactId;

        this.versionConstraints.addAll(versionConstraints);
        this.exclusions.addAll(exclusions);
        this.scope = scope;
        this.optional = optional;
        assertNotNullOrEmpty(type);
        this.type = type.toLowerCase();
        this.classifier = classifier.toLowerCase();
        refreshHashCode();
    }

    public MavenProduct product() {
        return new MavenProduct(groupId, artifactId);
    }

    public String toGA() {
        return new StringBuilder().append(groupId).append(':').append(artifactId).toString();
    }

    @Override
    public void setGroupId(String groupId) {
        super.setGroupId(groupId);
        refreshHashCode();
    }

    @Override
    public void setArtifactId(String artifactId) {
        super.setArtifactId(artifactId);
        refreshHashCode();
    }

    public Set<VersionConstraint> getVersionConstraints() {
        // keep immutability or hashCode will break
        return Collections.unmodifiableSet(versionConstraints);
    }

    public Set<Exclusion> getExclusions() {
        // keep immutability or hashCode will break
        return Collections.unmodifiableSet(exclusions);
    }

    public void addExclusion(Exclusion e) {
        exclusions.add(e);
        refreshHashCode();
    }

    public void setOptional(boolean isOptional) {
        this.optional = isOptional;
        refreshHashCode();
    }

    public Scope getScope() {
        return scope;
    }

    public boolean isOptional() {
        return optional;
    }

    public String getPackagingType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
        refreshHashCode();
    }

    public void setVersionConstraints(Set<VersionConstraint> vcs) {
        this.versionConstraints.clear();
        this.versionConstraints.addAll(vcs);
        refreshHashCode();
    }

    public void addVersionConstraint(VersionConstraint vc) {
        this.versionConstraints.add(vc);
        refreshHashCode();
    }

    public void setPackagingType(String type) {
        this.type = type;
        refreshHashCode();
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
        refreshHashCode();
    }

    @Deprecated
    public String[] getVersionConstraintsArr() {
        var constraints = new String[this.versionConstraints.size()];
        var i = new int[] { 0 };
        versionConstraints.forEach(vc -> {
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

    private void refreshHashCode() {
        final var prime = 31;
        hashCode = 0;
        // MavenProduct
        hashCode = prime * hashCode + ((groupId == null) ? 0 : groupId.hashCode());
        hashCode = prime * hashCode + ((artifactId == null) ? 0 : artifactId.hashCode());
        // Dependency
        hashCode = prime * hashCode + ((versionConstraints == null) ? 0 : versionConstraints.hashCode());
        hashCode = prime * hashCode + ((exclusions == null) ? 0 : exclusions.hashCode());
        hashCode = prime * hashCode + ((scope == null) ? 0 : scope.hashCode());
        hashCode = prime * hashCode + (optional ? 1231 : 1237);
        hashCode = prime * hashCode + ((type == null) ? 0 : type.hashCode());
        hashCode = prime * hashCode + ((classifier == null) ? 0 : classifier.hashCode());
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
        for (var constraint : this.versionConstraints) {
            constraintsJson.put(constraint.getSpec());
        }
        json.put("versionConstraints", constraintsJson);
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