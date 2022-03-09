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

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.json.JSONArray;
import org.json.JSONObject;

import eu.fasten.core.data.Constants;

/**
 * A dependency declaration. Denotes a Revision's will to use the functionality
 * of the {@class MavenProduct} that matches the dependency's qualifiers.
 */
public class Dependency extends MavenProduct {
	public static final Dependency empty = new Dependency("", "", "");

	public Set<VersionConstraint> versionConstraints;
	public Set<Exclusion> exclusions;
	public Scope scope = Scope.COMPILE;
	public boolean optional;
	public String type;
	public String classifier;

	@SuppressWarnings("unused")
	private Dependency() {
		// exists for JSON object mappers
	}

	/**
	 * Constructor for Dependency object. (From
	 * https://maven.apache.org/ref/3.6.3/maven-model/maven.html#class_dependency)
	 *
	 * @param groupId            groupId of dependency Maven coordinate
	 * @param artifactId         artifactId of dependency Maven coordinate
	 * @param versionConstraints List of version constraints of the dependency
	 * @param exclusions         List of exclusions
	 * @param scope              Scope of the dependency
	 * @param optional           Is dependency optional
	 * @param type               Type of the dependency
	 * @param classifier         Classifier for dependency
	 */
	public Dependency(final String groupId, final String artifactId, final Set<VersionConstraint> versionConstraints,
			final Set<Exclusion> exclusions, final Scope scope, final boolean optional, final String type,
			final String classifier) {
		super(groupId, artifactId);
		this.versionConstraints = versionConstraints;
		this.exclusions = exclusions;
		this.scope = scope;
		this.optional = optional;
		this.type = type.toLowerCase();
		assertNotNullOrEmpty(this.type);
		this.classifier = classifier.toLowerCase();
	}

	public Dependency(final String groupId, final String artifactId, final String version,
			final Set<Exclusion> exclusions, final Scope scope, final boolean optional, final String type,
			final String classifier) {
		this(groupId, artifactId, VersionConstraint.parseVersionSpec(version), exclusions, scope,
				optional, type, classifier);
	}

	public Dependency(final String groupId, final String artifactId, final String version) {
		this(groupId, artifactId, version, new HashSet<>(), Scope.COMPILE, false, "jar", "");
	}

	public MavenProduct product() {
		return new MavenProduct(groupId, artifactId);
	}

	/**
	 * Turns list of version constraints into string array of specifications.
	 *
	 * @return String array representation of the dependency version constraints
	 */
	public String[] getVersionConstraints() {
		var constraints = new String[this.versionConstraints.size()];
		var i = new int[] { 0 };
		versionConstraints.forEach(vc -> {
			constraints[i[0]++] = vc.spec;
		});
		return constraints;
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
		final var constraintsJson = new JSONArray();
		for (var constraint : this.versionConstraints) {
			constraintsJson.put(constraint.spec);
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

	public String getGroupId() {
		return this.groupId;
	}

	public String getArtifactId() {
		return this.artifactId;
	}

	public String getVersion() {
		return String.join(",", this.getVersionConstraints());
	}

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

	public String toMavenCoordinate() {
		return this.groupId + Constants.mvnCoordinateSeparator + this.artifactId + Constants.mvnCoordinateSeparator
				+ this.getVersion();
	}

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

	/**
	 * Creates a Dependency object from JSON.
	 *
	 * @param json JSONObject representation of dependency
	 * @return Dependency object
	 */
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
		if(scopeStr == null || scopeStr.isEmpty()) {
		    scope = Scope.COMPILE;
		} else {
		    scope = Scope.valueOf(scopeStr.toUpperCase());
		}
		var optional = json.optBoolean("optional", false);
		var type = json.optString("type");
		var classifier = json.optString("classifier");
		return new Dependency(groupId, artifactId, versionConstraints, exclusions, scope, optional, type, classifier);
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
		return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
	}
}