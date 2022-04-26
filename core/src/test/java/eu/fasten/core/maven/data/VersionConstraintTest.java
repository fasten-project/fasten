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

import static eu.fasten.core.maven.data.VersionConstraint.parseVersionSpec;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
public class VersionConstraintTest {

    @Test
    public void defaults() {
        var sut = new VersionConstraint(null);
        assertEquals(null, sut.getSpec());
        assertEquals(31, sut.hashCode());
    }

    @Test
    public void nonDefaults() {
        var sut = new VersionConstraint("1.2.3");
        assertEquals("1.2.3", sut.getSpec());
        assertEquals("1.2.3".hashCode() + 1, sut.hashCode());
    }

    @Test
    public void equalityDefaults() {
        var a = new VersionConstraint(null);
        var b = new VersionConstraint(null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityNonDefaults() {
        var a = new VersionConstraint("1.2.3");
        var b = new VersionConstraint("1.2.3");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(0, a.hashCode());
    }

    @Test
    public void equalityDifferentSpec() {
        var a = new VersionConstraint("1.2.3");
        var b = new VersionConstraint("2.3.4");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hasToString() {
        var sut = new VersionConstraint("1.2.3");
        assertEquals("1.2.3", sut.toString());
    }

    @Test
    public void parsing0() {
        assertEquals(Set.of(), parseVersionSpec(""));
    }

    @Test
    public void parsing1() {
        validate("1.0", //
                vc(false, false, "+1.0", "+1.0"));
    }

    @Test
    public void parsing2() {
        validate("[1.0]", //
                vc(false, true, "+1.0", "+1.0"));
    }

    @Test
    public void parsing3() {
        validate("(,1.0]", //
                vc(true, true, "0", "+1.0"));
    }

    @Test
    public void parsing4() {
        validate("[1.2,1.3]", //
                vc(true, true, "+1.2", "+1.3"));
    }

    @Test
    public void parsing5() {
        validate("[1.0,2.0)", //
                vc(true, true, "+1.0", "2.0"));
    }

    @Test
    public void parsing6() {
        validate("[1.5,)", //
                vc(true, true, "+1.5", "999"));
    }

    @Test
    public void parsing7() {
        validate("(,1.0],[1.2,)", //
                vc(true, true, "0", "+1.0"), //
                vc(true, true, "+1.2", "999"));
    }

    @Test
    public void parsing8() {
        validate("(,1.1),(1.1,)", //
                vc(true, true, "0", "1.1"), //
                vc(true, true, "1.1", "999"));
    }

    @Test
    public void commaButNoRange() {
        validate("[1.1],[1.2]", //
                vc(false, true, "+1.1", "+1.1"), //
                vc(false, true, "+1.2", "+1.2"));
    }

    @Test
    public void parsingReturnsLinkedHashSet_softConstraint() {
        var actual = VersionConstraint.parseVersionSpec("1");
        assertEquals(LinkedHashSet.class, actual.getClass());
    }

    @Test
    public void parsingReturnsLinkedHashSet_hardConstraint() {
        var actual = VersionConstraint.parseVersionSpec("[1]");
        assertEquals(LinkedHashSet.class, actual.getClass());
    }

    @Test
    public void parsingReturnsLinkedHashSet_multiConstraint() {
        var actual = VersionConstraint.parseVersionSpec("[1],[2]");
        assertEquals(LinkedHashSet.class, actual.getClass());
    }

    @Test
    public void longAndComplicatedSpec() {
        var actual = parseVersionSpec("[1.1],[1.2,1.3],[1.4,],[,1.5],(1.6),(1.7,1.8),(1.9,),(,2.0)") //
                .stream().map(vc -> vc.getSpec()).collect(Collectors.toSet());
        var expected = Set.of("[1.1]", "[1.2,1.3]", "[1.4,]", "[,1.5]", "(1.6)", "(1.7,1.8)", "(1.9,)", "(,2.0)");
        assertEquals(expected, actual);
    }

    @Test
    public void versionMatches() {
        assertMatch("1.2.3", "1.2.3");
        assertMatch("1.2.3", "[1.2.3]");

        assertMatch("1.2.3", "[1,1.2.3]");
        assertMatch("1.2.3", "[1.2.3,2]");

        assertNoMatch("1.2.3", "[1,1.2.3)");
        assertNoMatch("1.2.3", "(1.2.3,2]");
        assertNoMatch("1.2.3", "[0,1]");
        assertNoMatch("1.2.3", "[2,3]");

        assertMatch("1.2.3", "[1.2,1.3]");
        assertMatch("1.2.3", "[,1.3]");
        assertMatch("1.2.3", "[1.2,]");
        assertMatch("1.2.3", "(1.2,1.3)]");
        assertMatch("1.2.3", "(,1.3)");
        assertMatch("1.2.3", "(1.2,)");
    }

    private void assertMatch(String version, String spec) {
        var sut = new VersionConstraint(spec);
        assertTrue(sut.matches(version));
    }

    private void assertNoMatch(String version, String spec) {
        var sut = new VersionConstraint(spec);
        assertFalse(sut.matches(version));
    }

    @Test
    public void equality() {
        for (var spec : new String[] { "1.0", "[1.0]", "(,1.0]", "[1.2,1.3]", "[1.0,2.0)", "[1.5,)" }) {
            var a = new VersionConstraint(spec);
            var b = new VersionConstraint(spec);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    private static void validate(String spec, Consumer<VersionConstraint>... validators) {
        var vcs = List.copyOf(VersionConstraint.parseVersionSpec(spec));
        assertEquals(validators.length, vcs.size());
        for (var i = 0; i < vcs.size(); i++) {
            validators[i].accept(vcs.get(i));
        }
    }

    private static Consumer<VersionConstraint> vc(boolean isRange, boolean isHard, String lowerBound,
            String upperBound) {
        return vcs -> {
            var isInclusiveLower = lowerBound.startsWith("+");
            var lb = isInclusiveLower ? lowerBound.substring(1) : lowerBound;
            var isInclusiveUpper = upperBound.startsWith("+");
            var ub = isInclusiveUpper ? upperBound.substring(1) : upperBound;

            assertEquals(isRange, vcs.isRange(), "isRange: " + vcs.getSpec());
            assertEquals(isHard, vcs.isHard(), "isHard: " + vcs.getSpec());
            assertEquals(isInclusiveLower, vcs.isLowerBoundInclusive(), "isLowerBoundInclusive: " + vcs.getSpec());
            assertEquals(lb, vcs.getLowerBound(), "lowerBound: " + vcs.getSpec());
            assertEquals(isInclusiveUpper, vcs.isUpperBoundInclusive(), "isUpperBoundInclusive: " + vcs.getSpec());
            assertEquals(ub, vcs.getUpperBound(), "upperBound: " + vcs.getSpec());
        };
    }
}