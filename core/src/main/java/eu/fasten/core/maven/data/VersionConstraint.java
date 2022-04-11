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

import static eu.fasten.core.utils.Asserts.assertNotNull;
import static eu.fasten.core.utils.Asserts.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class VersionConstraint {

    public String spec;

    public boolean isRange;
    public boolean isHard;

    public String lowerBound;
    public boolean isLowerBoundInclusive;
    public String upperBound;
    public boolean isUpperBoundInclusive;

    @SuppressWarnings("unused")
    private VersionConstraint() {
        // exists for JSON object mappers
    }

    /**
     * Constructs a VersionConstraint object from specification. (From
     * https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification)
     *
     * @param spec String specification of version constraint
     */
    public VersionConstraint(String spec) {
        this.spec = spec;

        isHard = spec.startsWith("[") || spec.startsWith("(");
        isRange = spec.contains(",");
        isLowerBoundInclusive = !isHard || spec.startsWith("[");
        isUpperBoundInclusive = !isHard || spec.endsWith("]");

        if (isRange) {
            String asd = spec.substring(1, spec.length() - 1);
            var parts = asd.split(",", -1);
            lowerBound = parts[0].isEmpty() ? "0" : parts[0];
            upperBound = parts[1].isEmpty() ? "999" : parts[1];

        } else {
            if (isHard) {
                upperBound = spec.substring(1, spec.length() - 1);
            } else {
                upperBound = spec;
            }
            lowerBound = upperBound;
        }
    }

    public boolean matches(String version) {

        var v = new DefaultArtifactVersion(version);

        var lower = new DefaultArtifactVersion(lowerBound);
        var upper = new DefaultArtifactVersion(upperBound);

        if (v.equals(lower) && isLowerBoundInclusive) {
            return true;
        }
        if (v.equals(upper) && isUpperBoundInclusive) {
            return true;
        }
        if (v.compareTo(lower) > 0 && v.compareTo(upper) < 0) {
            return true;
        }

        return false;
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
    public static Set<VersionConstraint> parseVersionSpec(String spec) {
        assertNotNull(spec);
        spec = spec.replaceAll(" ", "").replaceAll("\n", "").replaceAll("\t", "");
        assertTrue(!spec.startsWith("$"));

        var constraints = new LinkedHashSet<VersionConstraint>();
        if (spec.isEmpty()) {
            return constraints;
        }

        if (!spec.startsWith("[") && !spec.startsWith("(")) {
            // has to be soft constraint
            assertTrue(!spec.contains(","));
            assertTrue(!spec.contains("["));
            assertTrue(!spec.contains("]"));
            assertTrue(!spec.contains("("));
            assertTrue(!spec.contains(")"));
            constraints.add(new VersionConstraint(spec));
            return constraints;
        }

        // has to be hard constraint
        var charZero = spec.charAt(0);
        assertTrue(charZero == '[' || charZero == '(');

        var idxOpen = 0;
        while (idxOpen != -1) {
            var idxClose = find(spec, idxOpen + 1, ')', ']');
            var vcSpec = spec.substring(idxOpen, idxClose + 1);
            constraints.add(new VersionConstraint(vcSpec));
            idxOpen = find(spec, idxClose, '(', '[');
        }

        return constraints;
    }

    private static int find(String hay, int idx, char... needles) {
        var found = false;
        while (!found && idx < hay.length()) {
            char cur = hay.charAt(idx);
            for (var needle : needles) {
                if (cur == needle) {
                    return idx;
                }
            }
            idx++;
        }
        return -1;
    }
}