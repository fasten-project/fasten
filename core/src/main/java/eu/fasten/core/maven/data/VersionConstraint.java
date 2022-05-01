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

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class VersionConstraint {

    private final String spec;

    private final boolean isRange;
    private final boolean isHard;
    private final boolean isLowerBoundInclusive;
    private final boolean isUpperBoundInclusive;

    private final int hashCode;

    public VersionConstraint(String spec) {
        this.spec = spec;
        if (spec == null || spec.isEmpty()) {
            throw new IllegalArgumentException("Spec of VersionConstraint cannot be null or empty");
        }

        this.isRange = spec.contains(",");
        this.isHard = spec.startsWith("[") || spec.startsWith("(");
        this.isLowerBoundInclusive = !isHard || spec.startsWith("[");
        this.isUpperBoundInclusive = !isHard || spec.endsWith("]");

        this.hashCode = spec.hashCode() + 1;
    }

    public String getSpec() {
        return spec;
    }

    public boolean isRange() {
        return isRange;
    }

    public boolean isHard() {
        return isHard;
    }

    public String getLowerBound() {
        if (isRange) {
            var parts = spec.substring(1, spec.length() - 1).split(",", -1);
            return parts[0].isEmpty() ? "0" : parts[0];
        }
        if (isHard) {
            return spec.substring(1, spec.length() - 1);
        }
        return spec;
    }

    public boolean isLowerBoundInclusive() {
        return isLowerBoundInclusive;
    }

    public String getUpperBound() {
        if (isRange) {
            var parts = spec.substring(1, spec.length() - 1).split(",", -1);
            return parts[1].isEmpty() ? "999" : parts[1];

        }
        if (isHard) {
            return spec.substring(1, spec.length() - 1);
        }
        return spec;
    }

    public boolean isUpperBoundInclusive() {
        return isUpperBoundInclusive;
    }

    public boolean matches(String version) {

        var v = new DefaultArtifactVersion(version);

        var lower = new DefaultArtifactVersion(getLowerBound());
        int compLow = v.compareTo(lower);
        if (compLow == 0 && isLowerBoundInclusive) {
            return true;
        }

        var upper = new DefaultArtifactVersion(getUpperBound());
        int compHigh = v.compareTo(upper);
        if (compHigh == 0 && isUpperBoundInclusive) {
            return true;
        }

        if (compLow > 0 && compHigh < 0) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VersionConstraint other = (VersionConstraint) obj;
        return hashCode == other.hashCode;
    }

    @Override
    public String toString() {
        return spec;
    }

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
        while (idx < hay.length()) {
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