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
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class VersionConstraint {

    private int hashCode;
    private String spec;

    public String getSpec() {
        return spec;
    }

    public boolean isRange() {
        return spec.contains(",");
    }

    public boolean isHard() {
        return spec.startsWith("[") || spec.startsWith("(");
    }

    public String getLowerBound() {
        if (isRange()) {
            String asd = spec.substring(1, spec.length() - 1);
            var parts = asd.split(",", -1);
            return parts[0].isEmpty() ? "0" : parts[0];
        }
        if (isHard()) {
            return spec.substring(1, spec.length() - 1);
        }
        return spec;
    }

    public boolean isLowerBoundInclusive() {
        return !isHard() || spec.startsWith("[");
    }

    public String getUpperBound() {
        if (isRange()) {
            var parts = spec.substring(1, spec.length() - 1).split(",", -1);
            return parts[1].isEmpty() ? "999" : parts[1];

        }
        if (isHard()) {
            return spec.substring(1, spec.length() - 1);
        }
        return spec;
    }

    public boolean isUpperBoundInclusive() {
        return !isHard() || spec.endsWith("]");
    }

    public boolean matches(String version) {

        var v = new DefaultArtifactVersion(version);

        var lower = new DefaultArtifactVersion(getLowerBound());
        var upper = new DefaultArtifactVersion(getUpperBound());

        if (v.equals(lower) && isLowerBoundInclusive()) {
            return true;
        }
        if (v.equals(upper) && isUpperBoundInclusive()) {
            return true;
        }
        if (v.compareTo(lower) > 0 && v.compareTo(upper) < 0) {
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
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public String toString() {
        return spec;
    }

    public static VersionConstraint init(String spec) {
        var v = new VersionConstraint();
        v.spec = spec;
        v.hashCode = spec == null ? 31 : spec.hashCode() + 1;
        return v;
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
            constraints.add(VersionConstraint.init(spec));
            return constraints;
        }

        // has to be hard constraint
        var charZero = spec.charAt(0);
        assertTrue(charZero == '[' || charZero == '(');

        var idxOpen = 0;
        while (idxOpen != -1) {
            var idxClose = find(spec, idxOpen + 1, ')', ']');
            var vcSpec = spec.substring(idxOpen, idxClose + 1);
            constraints.add(VersionConstraint.init(vcSpec));
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