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

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class VersionConstraint {

	public final String lowerBound;
	public final boolean isLowerHardRequirement;
	public final String upperBound;
	public final boolean isUpperHardRequirement;

	public final String spec;

	/**
	 * Constructs a VersionConstraint object from specification. (From
	 * https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification)
	 *
	 * @param spec String specification of version constraint
	 */
	public VersionConstraint(final String spec) {
		this.spec = spec;
		this.isLowerHardRequirement = spec.startsWith("[");
		this.isUpperHardRequirement = spec.endsWith("]");
		if (!spec.contains(",")) {
			var version = spec;
			if (version.startsWith("[") && version.endsWith("]")) {
				version = version.substring(1, spec.length() - 1);
			}
			this.upperBound = version;
			this.lowerBound = version;

		} else {
			final var versionSplit = startsAndEndsWithBracket(spec) ? spec.substring(1, spec.length() - 1).split(",")
					: spec.split(",");
			this.lowerBound = versionSplit[0];
			this.upperBound = (versionSplit.length > 1) ? versionSplit[1] : "";
		}
	}

	private boolean startsAndEndsWithBracket(String str) {
		return (str.startsWith("(") || str.startsWith("[")) && (str.endsWith(")") || str.endsWith("]"));
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
		return spec;
	}

	/**
	 * Creates full list of version constraints from specification. (From
	 * https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification)
	 *
	 * @param spec String specification of version constraints
	 * @return List of Version Constraints
	 */
	public static Set<VersionConstraint> resolveMultipleVersionConstraints(String spec) {
		if (spec == null) {
			return Set.of(new VersionConstraint("*"));
		}
		if (spec.startsWith("$")) {
			return Set.of(new VersionConstraint(spec));
		}
		final var versionRangesCount = (StringUtils.countMatches(spec, ",") + 1) / 2;
		var versionConstraints = new LinkedHashSet<VersionConstraint>(versionRangesCount);
		int count = 0;
		for (int i = 0; i < spec.length(); i++) {
			if (spec.charAt(i) == ',') {
				count++;
				if (count % 2 == 0) {
					var specBuilder = new StringBuilder(spec);
					specBuilder.setCharAt(i, ';');
					spec = specBuilder.toString();
				}
			}
		}
		var versionRanges = spec.split(";");
		for (var versionRange : versionRanges) {
			versionConstraints.add(new VersionConstraint(versionRange));
		}
		return versionConstraints;
	}
}